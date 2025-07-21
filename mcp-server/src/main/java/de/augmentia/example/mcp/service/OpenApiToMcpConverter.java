package de.augmentia.example.mcp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OpenApiToMcpConverter {

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final Map<String, Map.Entry<String, JsonNode>> pathMap = new ConcurrentHashMap<>();


    public List<Tool> convertOpenApiToMcpTools(InputStream inputStream, String content) throws IOException {
        JsonNode openApiDoc;
        if (content == null || content.isEmpty()) {
            openApiDoc = yamlMapper.readTree(inputStream);
        } else {
            openApiDoc = yamlMapper.readTree(content);
        }
        List<Tool> tools = new ArrayList<>();

        JsonNode paths = openApiDoc.get("paths");
        if (paths == null) return tools;

        paths.fields().forEachRemaining(pathEntry -> {
            String path = pathEntry.getKey();
            JsonNode pathItem = pathEntry.getValue();

            pathItem.fields().forEachRemaining(methodEntry -> {
                String method = methodEntry.getKey().toUpperCase();
                if (isValidHttpMethod(method)) {
                    JsonNode operation = methodEntry.getValue();
                    Tool tool = createToolFromOperation(path, method, operation);
                    pathMap.put(tool.name(), pathEntry);
                    tools.add(tool);
                }
            });
        });

        return tools;
    }

    private boolean isValidHttpMethod(String method) {
        return Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS").contains(method);
    }

    private Tool createToolFromOperation(String path, String method, JsonNode operation) {
        String operationId = getOperationId(operation, path, method);
        String description = getOperationDescription(operation);

        Map<String, Object> inputSchema = createInputSchema(operation, path);
        String jsonSchemaString;
        try {
            // Convert the Map<String, Object> to a proper JSON string
            jsonSchemaString = jsonMapper.writeValueAsString(inputSchema);
        } catch (JsonProcessingException e) {
            // Handle the exception, e.g., log it and return an error or a default schema
            System.err.println("Error converting schema map to JSON string: " + e.getMessage());
            // Depending on your error handling strategy, you might return null,
            // rethrow a custom exception, or return an empty/invalid JSON string.
            jsonSchemaString = "{}"; // Fallback to an empty JSON object
        }
        return new Tool(operationId, description, jsonSchemaString);

    }

    private String getOperationId(JsonNode operation, String path, String method) {
        if (operation.has("operationId")) {
            return operation.get("operationId").asText();
        }

        // Generate operationId from path and method
        String cleanPath = path.replaceAll("[{}]", "")
                .replaceAll("[^a-zA-Z0-9]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        return method.toLowerCase() + "_" + cleanPath;
    }

    private String getOperationDescription(JsonNode operation) {
        if (operation.has("description")) {
            return operation.get("description").asText();
        }
        if (operation.has("summary")) {
            return operation.get("summary").asText();
        }
        return "API endpoint";
    }

    private Map<String, Object> createInputSchema(JsonNode operation, String path) {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new HashMap<>();
        List<String> required = new ArrayList<>();

        // Add path parameters
        addPathParameters(properties, required, path);

        // Add query parameters
        addQueryParameters(properties, required, operation);

        // Add request body parameters
        addRequestBodyParameters(properties, required, operation);

        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }

        return schema;
    }

    private void addPathParameters(Map<String, Object> properties, List<String> required, String path) {
        // Extract path parameters like {id}, {userId} etc.
        String[] pathSegments = path.split("/");
        for (String segment : pathSegments) {
            if (segment.startsWith("{") && segment.endsWith("}")) {
                String paramName = segment.substring(1, segment.length() - 1);
                Map<String, Object> paramSchema = new HashMap<>();
                paramSchema.put("type", "string");
                paramSchema.put("description", "Path parameter: " + paramName);
                properties.put(paramName, paramSchema);
                required.add(paramName);
            }
        }
    }

    private void addQueryParameters(Map<String, Object> properties, List<String> required, JsonNode operation) {
        JsonNode parameters = operation.get("parameters");
        if (parameters == null || !parameters.isArray()) return;

        for (JsonNode param : parameters) {
            if ("query".equals(param.path("in").asText())) {
                String name = param.get("name").asText();
                Map<String, Object> paramSchema = convertParameterSchema(param);
                properties.put(name, paramSchema);

                if (param.path("required").asBoolean(false)) {
                    required.add(name);
                }
            }
        }
    }

    private void addRequestBodyParameters(Map<String, Object> properties, List<String> required, JsonNode operation) {
        JsonNode requestBody = operation.get("requestBody");
        if (requestBody == null) return;

        JsonNode content = requestBody.get("content");
        if (content == null) return;

        // Handle application/json content type
        JsonNode jsonContent = content.get("application/json");
        if (jsonContent != null) {
            JsonNode schema = jsonContent.get("schema");
            if (schema != null) {
                if (schema.has("properties")) {
                    JsonNode schemaProperties = schema.get("properties");
                    schemaProperties.fields().forEachRemaining(entry -> {
                        String propName = entry.getKey();
                        JsonNode propSchema = entry.getValue();
                        properties.put(propName, convertJsonSchemaToMap(propSchema));
                    });

                    JsonNode requiredArray = schema.get("required");
                    if (requiredArray != null && requiredArray.isArray()) {
                        requiredArray.forEach(req -> required.add(req.asText()));
                    }
                } else {
                    // Single body parameter
                    Map<String, Object> bodySchema = new HashMap<>();
                    bodySchema.put("type", "object");
                    bodySchema.put("description", "Request body");
                    properties.put("body", bodySchema);

                    if (requestBody.path("required").asBoolean(false)) {
                        required.add("body");
                    }
                }
            }
        }
    }

    private Map<String, Object> convertParameterSchema(JsonNode param) {
        Map<String, Object> schema = new HashMap<>();

        JsonNode paramSchema = param.get("schema");
        if (paramSchema != null) {
            schema.putAll(convertJsonSchemaToMap(paramSchema));
        } else {
            // Fallback for older OpenAPI versions
            schema.put("type", param.path("type").asText("string"));
        }

        if (param.has("description")) {
            schema.put("description", param.get("description").asText());
        }

        return schema;
    }

    private Map<String, Object> convertJsonSchemaToMap(JsonNode schemaNode) {
        try {
            return jsonMapper.convertValue(schemaNode, Map.class);
        } catch (Exception e) {
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("type", schemaNode.path("type").asText("string"));
            if (schemaNode.has("description")) {
                fallback.put("description", schemaNode.get("description").asText());
            }
            return fallback;
        }
    }

    public Map<String, Map.Entry<String, JsonNode>> getPathMap() {
        return Collections.unmodifiableMap(pathMap);
    }
}