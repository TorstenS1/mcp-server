
package com.example.mcp.service;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SpringAIToolConverterServiceTest {

    @Test
    void testConvertToMcpTool() throws NoSuchMethodException {
        SpringAIToolConverterService springAIToolConverterService = new SpringAIToolConverterService();
        Method method = TestTool.class.getMethod("testMethod");
        McpSchema.Tool result = springAIToolConverterService.convertToMcpTool(new TestTool(), method);
        assertNotNull(result);
    }

    @Test
    void testCreateToolHandler() throws NoSuchMethodException {
        SpringAIToolConverterService springAIToolConverterService = new SpringAIToolConverterService();
        Method method = TestTool.class.getMethod("testMethod");
        assertNotNull(springAIToolConverterService.createToolHandler(new TestTool(), method));
    }

    public static class TestTool {
        public void testMethod() {
        }
    }
}
