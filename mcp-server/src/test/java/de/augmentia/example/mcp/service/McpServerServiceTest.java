package de.augmentia.example.mcp.service;

import de.augmentia.example.mcp.controller.McpServerController;
import de.augmentia.example.mcp.exception.ToolNotFoundException;
import de.augmentia.example.mcp.exception.ToolRegistrationException;
import de.augmentia.example.mcp.model.OpenApiDef;
import de.augmentia.example.mcp.model.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class McpServerServiceTest {

    @Mock
    private OpenApiToMcpConverter converter;
    @Mock
    private RestApiExecutorService apiExecutor;
    @Mock
    private McpSyncServer mcpSyncServer;
    @Mock
    private ResourceLoader resourceLoader;

    @Mock
    private SpringAIToolConverterService springAIToolConverterService;
    @Mock
    private McpConfigurationService mcpConfigurationService;

    private McpServerService mcpServerService;

    private McpSchema.Tool mockTool;
    private File mockConfigFile;

    @BeforeEach
    void setUp() throws IOException {
        mcpServerService = spy(new McpServerService(converter, apiExecutor, mcpSyncServer, resourceLoader, springAIToolConverterService, mcpConfigurationService));
        mockTool = new McpSchema.Tool("testTool", "Test Description", (McpSchema.JsonSchema) null);
        // Reset internal state of McpServerService for each test
        ReflectionTestUtils.setField(mcpServerService, "tools", new java.util.ArrayList<>());
        ReflectionTestUtils.setField(mcpServerService, "toolBaseUrls", new java.util.HashMap<>());
        ReflectionTestUtils.setField(mcpServerService, "hardcodedToolHandlers", new java.util.HashMap<>());

        // Create a temporary file for tests that need a config file
        mockConfigFile = File.createTempFile("test-config", ".yml");
        mockConfigFile.deleteOnExit();
    }

    @Test
    void initTools_loadsOpenApiToolsAndHardcodedTools() throws IOException, URISyntaxException, NoSuchMethodException {
        // Mock configuration service
        McpServer mcpServer = new McpServer();
        OpenApiDef mTool = new OpenApiDef();
        mTool.setName("testOpenApiTool");
        mTool.setRestApiUrl("classpath:/tools/users-api.yml");
        mcpServer.setTools(Collections.singletonList(mTool));
        when(mcpConfigurationService.loadMcpServerConfiguration()).thenReturn(mcpServer);

        // Mock resource loading
        Resource mockResource = mock(Resource.class);
        when(resourceLoader.getResource(anyString())).thenReturn(mockResource);
        when(mockResource.exists()).thenReturn(true);
        when(mockResource.getInputStream()).thenAnswer(invocation -> new ClassPathResource("users-api.yml").getInputStream());

        // Mock OpenAPI conversion
        when(converter.convertOpenApiToMcpTools(any(InputStream.class), any())).thenReturn(Collections.singletonList(mockTool));
        when(apiExecutor.initialize(any(InputStream.class), any())).thenReturn("http://localhost:8110");


        mcpServerService.initTools();

        // Verify interactions
        verify(mcpConfigurationService).loadMcpServerConfiguration();
        verify(resourceLoader).getResource("classpath:/tools/users-api.yml");
        verify(converter, times(1)).convertOpenApiToMcpTools(any(InputStream.class), any());
        verify(apiExecutor, times(1)).initialize(any(InputStream.class), any());
        verify(mcpSyncServer, times(1)).removeTool(anyString()); // Once for OpenAPI, once for TimeTool
        verify(mcpSyncServer, times(1)).addTool(any(McpServerFeatures.SyncToolSpecification.class));

        // Verify tools are loaded
        assertEquals(1, mcpServerService.getTools().size());
        assertTrue(mcpServerService.getTools().stream().anyMatch(t -> t.name().equals("testTool")));
    }

    @Test
    void deleteTool_removesToolSuccessfully() {
        // Add a tool to the internal list first
        ReflectionTestUtils.setField(mcpServerService, "tools", new java.util.ArrayList<>(Collections.singletonList(mockTool)));

        doNothing().when(mcpSyncServer).removeTool(anyString());

        mcpServerService.deleteTool("testTool");

        verify(mcpSyncServer).removeTool("testTool");
        assertTrue(mcpServerService.getTools().isEmpty());
    }

    @Test
    void deleteTool_throwsToolNotFoundException() {
        assertThrows(ToolNotFoundException.class, () -> mcpServerService.deleteTool("nonExistentTool"));
        verify(mcpSyncServer, never()).removeTool(anyString());
    }

    @Test
    void updateToolDescription_updatesFileAndReinitializes() throws IOException {
        // Mock configuration service to indicate file loading
        when(mcpConfigurationService.getLoadedConfigFile()).thenReturn(mockConfigFile);
        when(mcpConfigurationService.isLoadedFromResource()).thenReturn(false);

        // Mock file content reading and writing
        String initialYaml = "mcp_server:\n  tools:\n    - name: testTool\n      description: old description\n";
        Files.writeString(mockConfigFile.toPath(), initialYaml);

        // Use a real ObjectMapper for YAML parsing/writing in the test
        // This requires a bit of a workaround since ObjectMapper is final and cannot be mocked easily
        // For this test, we'll simulate the file content directly.
        // In a real scenario, you might use a temporary file or a custom mock for ObjectMapper.

        // Simulate initial state
        ReflectionTestUtils.setField(mcpServerService, "yamlMapper", new com.fasterxml.jackson.databind.ObjectMapper(new com.fasterxml.jackson.dataformat.yaml.YAMLFactory()));

        // Mock initTools to prevent full re-initialization during test
        doNothing().when(mcpServerService).initTools();

        // Execute the method
        mcpServerService.updateToolDescription("testTool", "new description");

        // Verify that initTools was called (indicating re-initialization)
        verify(mcpServerService).initTools();
    }

    @Test
    void updateToolDescription_throwsUnsupportedOperationException() {
        when(mcpConfigurationService.isLoadedFromResource()).thenReturn(true);
        assertThrows(UnsupportedOperationException.class, () -> mcpServerService.updateToolDescription("testTool", "new description"));
    }

    @Test
    void updateToolDescription_throwsToolNotFoundException() throws IOException {
        when(mcpConfigurationService.getLoadedConfigFile()).thenReturn(mockConfigFile);
        when(mcpConfigurationService.isLoadedFromResource()).thenReturn(false);

        String initialYaml = "mcp_server:\n  tools:\n    - name: anotherTool\n      description: some description\n";
        Files.writeString(mockConfigFile.toPath(), initialYaml);
        // Simulate initial state
        ReflectionTestUtils.setField(mcpServerService, "yamlMapper", new com.fasterxml.jackson.databind.ObjectMapper(new com.fasterxml.jackson.dataformat.yaml.YAMLFactory()));

        assertThrows(ToolRegistrationException.class, () -> mcpServerService.updateToolDescription("nonExistentTool", "new description"));
    }

    @Test
    void registerOpenApi_registersNewToolFromUrl() throws IOException, URISyntaxException {
        McpServerController.OpenApiRegistrationRequest request = new McpServerController.OpenApiRegistrationRequest();
        request.setType("URL");
        request.setSource("http://example.com/openapi.yml");

        Resource mockResource = mock(Resource.class);
        when(resourceLoader.getResource("http://example.com/openapi.yml")).thenReturn(mockResource);
                when(mockResource.getInputStream()).thenAnswer(invocation -> new ClassPathResource("users-api.yml").getInputStream());

        when(converter.convertOpenApiToMcpTools(any(InputStream.class), any())).thenReturn(Collections.singletonList(mockTool));
        when(apiExecutor.initialize(any(InputStream.class), any())).thenReturn("http://localhost:8080");

        doNothing().when(mcpServerService).updateMcpServerTools(); // Mock internal method

        mcpServerService.registerOpenApi(request);

        verify(converter).convertOpenApiToMcpTools(any(InputStream.class), any());
        verify(apiExecutor).initialize(any(InputStream.class), any());
        verify(mcpServerService).updateMcpServerTools();
        assertEquals(1, mcpServerService.getTools().size());
        assertTrue(mcpServerService.getTools().stream().anyMatch(t -> t.name().equals("testTool")));
    }

    @Test
    void registerOpenApi_registersNewToolFromBase64() throws IOException, URISyntaxException {
        McpServerController.OpenApiRegistrationRequest request = new McpServerController.OpenApiRegistrationRequest();
        request.setType("BASE64");
        request.setSource("b3BlbmFwaTogMy4wLjA="); // Base64 for "openapi: 3.0.0"

        when(converter.convertOpenApiToMcpTools(any(InputStream.class), any())).thenReturn(Collections.singletonList(mockTool));
        when(apiExecutor.initialize(any(InputStream.class), any())).thenReturn("http://localhost:8080");

        doNothing().when(mcpServerService).updateMcpServerTools(); // Mock internal method

        mcpServerService.registerOpenApi(request);

        verify(converter).convertOpenApiToMcpTools(any(InputStream.class), any());
        verify(apiExecutor).initialize(any(InputStream.class), any());
        verify(mcpServerService).updateMcpServerTools();
        assertEquals(1, mcpServerService.getTools().size());
        assertTrue(mcpServerService.getTools().stream().anyMatch(t -> t.name().equals("testTool")));
    }

    @Test
    void registerOpenApi_throwsIllegalArgumentExceptionForUnsupportedType() {
        McpServerController.OpenApiRegistrationRequest request = new McpServerController.OpenApiRegistrationRequest();
        request.setType("UNSUPPORTED");
        request.setSource("someSource");

        assertThrows(ToolRegistrationException.class, () -> mcpServerService.registerOpenApi(request));
    }

    @Test
    void registerOpenApi_skipsExistingTool() throws IOException, URISyntaxException {
        // Add the tool to the internal list before the test
        ReflectionTestUtils.setField(mcpServerService, "tools", new java.util.ArrayList<>(Collections.singletonList(mockTool)));

        McpServerController.OpenApiRegistrationRequest request = new McpServerController.OpenApiRegistrationRequest();
        request.setType("URL");
        request.setSource("http://example.com/openapi.yml");

        Resource mockResource = mock(Resource.class);
        when(resourceLoader.getResource("http://example.com/openapi.yml")).thenReturn(mockResource);
                when(mockResource.getInputStream()).thenAnswer(invocation -> new ClassPathResource("users-api.yml").getInputStream());

        mcpServerService.registerOpenApi(request);

        // Verify that the tool was not added again
        assertEquals(1, mcpServerService.getTools().size());
        verify(mcpServerService, never()).updateMcpServerTools(); // Should not update if no new tools added
    }

    }