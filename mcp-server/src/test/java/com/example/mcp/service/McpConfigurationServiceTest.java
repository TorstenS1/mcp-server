package com.example.mcp.service;

import com.example.mcp.model.McpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class McpConfigurationServiceTest {

    @Mock
    private ResourceLoader resourceLoader;

    @InjectMocks
    private McpConfigurationService mcpConfigurationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(mcpConfigurationService, "configResourcePath", "mcp-client-config.yml");
    }

    @Test
    void loadMcpServerConfiguration_fromClasspath() throws IOException {
        Resource resource = new ClassPathResource("mcp-client-config.yml");
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        McpServer mcpServer = mcpConfigurationService.loadMcpServerConfiguration();
        assertNotNull(mcpServer);
    }

    @Test
    void loadMcpServerConfiguration_fromFile() throws IOException {
        Resource resource = new ClassPathResource("mcp-client-config.yml");
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        McpServer mcpServer = mcpConfigurationService.loadMcpServerConfiguration();
        assertNotNull(mcpServer);
    }
}