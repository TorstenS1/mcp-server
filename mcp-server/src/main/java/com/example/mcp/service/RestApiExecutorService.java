package com.example.mcp.service;

import com.example.mcp.config.SecurityProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Optional;

@Service
public class RestApiExecutorService {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private SecurityProperties securityProperties;

    @Autowired
    private OpenApiToMcpConverter openApiToMcpConverter;

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final ObjectMapper jsonMapper = new ObjectMapper();

    public String initialize(InputStream inputStream, String content) throws IOException, URISyntaxException {
        JsonNode openApiDoc;
        if (content == null || content.isEmpty()) {
            openApiDoc = yamlMapper.readTree(inputStream);
        } else {
            openApiDoc = yamlMapper.readTree(content);
        }
        String extractedBaseUrl = extractBaseUrl(openApiDoc);
        validateBaseUrl(extractedBaseUrl);
        return extractedBaseUrl;
    }

    private void validateBaseUrl(String url) throws URISyntaxException {
        URI uri = new URI(url);
        String domain = uri.getHost();
        if (domain == null || !securityProperties.getAllowedDomains().contains(domain)) {
            throw new IllegalArgumentException("Domain not allowed: " + domain);
        }
    }

    private String extractBaseUrl(JsonNode openApiDoc) {
        JsonNode servers = openApiDoc.get("servers");
        if (servers != null && servers.isArray() && servers.size() > 0) {
            return servers.get(0).get("url").asText();
        }

        // Fallback for older OpenAPI versions
        String host = openApiDoc.path("host").asText("localhost");
        String basePath = openApiDoc.path("basePath").asText("");
        String scheme = openApiDoc.path("schemes").isArray() && openApiDoc.get("schemes").size() > 0
                ? openApiDoc.get("schemes").get(0).asText("http")
                : "http";

        return scheme + "://" + host + basePath;
    }

    public String executeApiCall(String baseUrl, String toolName, Map<String, Object> arguments) {
        try {
            Map.Entry<String, JsonNode> entry = openApiToMcpConverter.getPathMap().get(toolName);
            String path = entry.getKey();
            JsonNode jsonNode = entry.getValue();
            String method = findHttpMethodByOperationId(jsonNode, toolName)
                    .orElseThrow(() -> new IllegalArgumentException("Operation ID not found: " + toolName));
            boolean needsRequestBody = needsRequestBody(method);
            //ApiCallInfo callInfo = parseToolName(toolName);
            String url = buildUrl(baseUrl, path, arguments, needsRequestBody);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            addAuthHeader(method, headers);


            Object requestBody = null;
            if (needsRequestBody) {
                requestBody = buildRequestBody(arguments);
            }

            HttpEntity<Object> entity = null;
            if (requestBody != null)
                entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.valueOf(method),
                    entity,
                    String.class
            );

            return formatResponse(response);

        } catch (Exception e) {
            return "Error executing API call: " + e.getMessage();
        }
    }

    public static Optional<String> findHttpMethodByOperationId(
            JsonNode pathItemNode, String targetOperationId) {

        // HTTP methods commonly used in OpenAPI
        String[] httpMethods = {"get", "post", "put", "delete", "patch", "head", "options", "trace"};

        if (pathItemNode == null || !pathItemNode.isObject()) {
            return Optional.empty();
        }

        ObjectNode objectNode = (ObjectNode) pathItemNode;

        for (String method : httpMethods) {
            JsonNode methodNode = objectNode.get(method);
            if (methodNode != null && methodNode.isObject()) {
                JsonNode operationIdNode = methodNode.get("operationId");
                if (operationIdNode != null && operationIdNode.isTextual()) {
                    String currentOperationId = operationIdNode.asText();
                    if (targetOperationId.equals(currentOperationId)) {
                        // Found it! Return the HTTP method name and the method's JsonNode
                        return Optional.of(method.toUpperCase());
                    }
                }
            }
        }
        // If loop completes, operationId was not found
        return Optional.empty();
    }

    private void addAuthHeader(String toolName, HttpHeaders headers) {
        // A simple approach: use the tool name (or part of it) to find the right API key.
        // This assumes a naming convention between tool names and the keys in application.yml.
        for (Map.Entry<String, String> entry : securityProperties.getApiKeys().entrySet()) {
            if (toolName.startsWith(entry.getKey())) {
                headers.set("Authorization", "Bearer " + entry.getValue());
                return;
            }
        }
    }


    private boolean needsRequestBody(String method) {
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
    }

    private ApiCallInfo parseToolName(String toolName) {
        // Parse tool name back to method and path
        // Assumes format: method_path (e.g., "get_users_id" -> GET /users/{id})
        String[] parts = toolName.split("_", 2);
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid tool name format: " + toolName);
        }

        String method = parts[0].toUpperCase();
        String path = "/" + parts[1].replace("_", "/");

        return new ApiCallInfo(method, path);
    }

    private String buildUrl(String baseUrl, String path, Map<String, Object> arguments, boolean needsRequestBody) throws URISyntaxException {
        String url = baseUrl + path;
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);

        // Replace path parameters
        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            if (url.contains(placeholder)) {
                url = url.replace(placeholder, entry.getValue().toString());
            } else if (!needsRequestBody) {
                // Add as query parameter if not a path parameter and not a body parameter
                builder.queryParam(entry.getKey(), entry.getValue());
            }
        }

        return builder.uri(new URI(url)).build().toString();
    }

    private Object buildRequestBody(Map<String, Object> arguments) {
        try {
            // Filter out path and query parameters, keep only body parameters
            Map<String, Object> bodyParams = arguments.entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue
                    ));

            return bodyParams.isEmpty() ? null : bodyParams;

        } catch (Exception e) {
            return null;
        }
    }

    private String formatResponse(ResponseEntity<String> response) {
        try {
            JsonNode responseBody = jsonMapper.readTree(response.getBody());
            return jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(responseBody);
        } catch (Exception e) {
            return "Status: " + response.getStatusCode() + "\nBody: " + response.getBody();
        }
    }

    private static class ApiCallInfo {
        final String method;
        final String path;

        ApiCallInfo(String method, String path) {
            this.method = method;
            this.path = path;
        }
    }
}