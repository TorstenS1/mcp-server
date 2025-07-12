
package com.example.mcp.service;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class OpenApiToMcpConverterTest {

    @Test
    void testConvertOpenApiToMcpTools() throws java.io.IOException {
        OpenApiToMcpConverter openApiToMcpConverter = new OpenApiToMcpConverter();
        List<McpSchema.Tool> result = openApiToMcpConverter.convertOpenApiToMcpTools(null, null);
        assertNotNull(result);
    }
}
