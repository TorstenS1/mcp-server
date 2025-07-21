package de.augmentia.example.mcp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

@Service
public class SpringAIToolConverterService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public McpSchema.Tool convertToMcpTool(Object bean, Method method) {
        Tool toolAnnotation = method.getAnnotation(Tool.class);
        if (toolAnnotation == null) {
            throw new IllegalArgumentException("Method must be annotated with @Tool");
        }

        String toolName = method.getName(); // Using method name as tool name
        String description = toolAnnotation.description();

        Map<String, Object> properties = new HashMap<>();
        for (Parameter parameter : method.getParameters()) {
            Map<String, String> paramSchema = new HashMap<>();
            paramSchema.put("type", getJsonSchemaType(parameter.getType()));
            // You might want to extract more details from parameter annotations if available
            properties.put(parameter.getName(), paramSchema);
        }

        Map<String, Object> inputSchema = new HashMap<>();
        inputSchema.put("type", "object");
        inputSchema.put("properties", properties);
        // Assuming all parameters are required for simplicity, adjust as needed
        inputSchema.put("required", properties.keySet().toArray(new String[0]));

        String jsonSchemaString;
        try {
            jsonSchemaString = objectMapper.writeValueAsString(inputSchema);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert input schema to JSON string", e);
        }

        return new McpSchema.Tool(toolName, description, jsonSchemaString);
    }

    public BiFunction<McpSyncServerExchange, Map<String, Object>, McpSchema.CallToolResult> createToolHandler(Object bean, Method method) {
        return (exchange, arguments) -> {
            try {
                // Prepare arguments for method invocation
                Object[] methodArgs = new Object[method.getParameterCount()];
                Parameter[] parameters = method.getParameters();
                for (int i = 0; i < parameters.length; i++) {
                    String paramName = parameters[i].getName();
                    Object argValue = arguments.get(paramName);
                    // Basic type conversion, extend as needed
                    if (argValue instanceof Number && parameters[i].getType() == String.class) {
                        methodArgs[i] = String.valueOf(argValue);
                    } else {
                        methodArgs[i] = argValue;
                    }
                }
                Object result = method.invoke(bean, methodArgs);
                return new McpSchema.CallToolResult(String.valueOf(result), false);
            } catch (Exception e) {
                return new McpSchema.CallToolResult("Error executing tool: " + e.getMessage(), true);
            }
        };
    }

    private String getJsonSchemaType(Class<?> javaType) {
        if (javaType == String.class) {
            return "string";
        } else if (javaType == Integer.class || javaType == int.class ||
                   javaType == Long.class || javaType == long.class) {
            return "integer";
        } else if (javaType == Double.class || javaType == double.class ||
                   javaType == Float.class || javaType == float.class) {
            return "number";
        } else if (javaType == Boolean.class || javaType == boolean.class) {
            return "boolean";
        }
        return "string"; // Default to string for unknown types
    }
}
