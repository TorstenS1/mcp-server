package com.example.mcp.service;

import com.example.mcp.controller.McpServerController;
import com.example.mcp.exception.*;
import com.example.mcp.model.OpenApiDef;
import com.example.mcp.model.McpServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.BiFunction;

/**
 * Main service for the MCP (Model Context Protocol) Server application.
 * This service manages the lifecycle of tools, including loading them from configuration,
 * registering them with the MCP synchronization server, and handling tool execution requests.
 * It also provides functionalities for dynamic tool registration and updates.
 */
@Service
@Slf4j
public class McpServerService {

    private final OpenApiToMcpConverter converter;
    private final RestApiExecutorService apiExecutor;
    private final McpSyncServer mcpSyncServer;
    private final ResourceLoader resourceLoader;
    private final SpringAIToolConverterService springAIToolConverterService;
    private final McpConfigurationService mcpConfigurationService; // New dependency

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    private HashMap <String, String> envMap = new HashMap<>(); // Environment variables map

    // List of all tools currently managed by this MCP server instance
    private final List<Tool> tools = new ArrayList<>();
    // Map to store base URLs for OpenAPI-defined tools, keyed by tool name
    private final Map<String, String> toolBaseUrls = new HashMap<>();
    // Map to store handlers for hardcoded tools, keyed by tool name
    private final Map<String, BiFunction<McpSyncServerExchange, Map<String, Object>, McpSchema.CallToolResult>> hardcodedToolHandlers = new HashMap<>();

    @Value("${config.directory:}")
    private String configFilePath;

    /**
     * Constructs the McpServerService with necessary dependencies.
     *
     * @param converter                    The service to convert OpenAPI specifications to MCP tools.
     * @param apiExecutor                  The service to execute REST API calls for tools.
     * @param mcpSyncServer                The MCP synchronization server instance.
     * @param resourceLoader               The Spring ResourceLoader for accessing resources.
     * @param springAIToolConverterService The service to convert Spring AI tools to MCP tools.
     * @param mcpConfigurationService      The service to load MCP server configuration.
     */
    @Autowired
    public McpServerService(OpenApiToMcpConverter converter,
                            RestApiExecutorService apiExecutor,
                            McpSyncServer mcpSyncServer,
                            ResourceLoader resourceLoader,
                            SpringAIToolConverterService springAIToolConverterService,
                            McpConfigurationService mcpConfigurationService) {
        this.converter = converter;
        this.apiExecutor = apiExecutor;
        this.mcpSyncServer = mcpSyncServer;
        this.resourceLoader = resourceLoader;
        this.springAIToolConverterService = springAIToolConverterService;
        this.mcpConfigurationService = mcpConfigurationService;
    }

    /**
     * Initializes the MCP server by loading tools from configuration,
     * adding hardcoded tools, and registering them with the MCP synchronization server.
     * This method is typically called once during application startup.
     *
     * @throws ConfigurationLoadingException if there's an issue loading the server configuration.
     * @throws ToolRegistrationException     if there's an issue registering tools.
     * @throws ExternalApiException          if there's an issue initializing an external API.
     * @throws InvalidOpenApiSpecException   if an OpenAPI specification is invalid.
     */
    public void initTools() {
        tools.clear(); // Clear existing tools on re-initialization
        toolBaseUrls.clear(); // Clear existing base URLs

        McpServer mcpServer = mcpConfigurationService.loadMcpServerConfiguration();

        envMap = mcpServer.getEnvironmentVariables();

        addHardcodedTools(); // Add hardcoded tools (like ping and Spring AI tools)

        // Initialize tools defined in mcp_server.yml
        for (OpenApiDef tool : mcpServer.getTools()) {
            try {
                String currentBaseUrl = "";
                List<Tool> convertedTools = List.of();
                InputStream apiSpecStreamForExecutor = null;
                InputStream apiSpecStreamForConverter = null;

                if (tool.getRestApiUrl() == null || tool.getRestApiUrl().isEmpty()) {
                    // Initialize tool without OpenAPI spec, // e.g., hardcoded tools or tools without REST API
                    if (tool.getApiDefinition() != null && !tool.getRestApiUrl().isEmpty()) {
                        currentBaseUrl = this.apiExecutor.initialize(null, mcpServer.getApiDefinition());
                        convertedTools = converter.convertOpenApiToMcpTools(null, mcpServer.getApiDefinition());
                    }
                } else {
                    // Load OpenAPI spec from resource (classpath or URL)
                    String restApiUrl = tool.getRestApiUrl().startsWith("file:") ?
                            configFilePath + tool.getRestApiUrl().substring(5) : // Remove 'file:' prefix if present
                            tool.getRestApiUrl(); // Use as is for classpath or URL;
                    Resource resource = resourceLoader.getResource(restApiUrl);
                    if (!resource.exists()) {
                        log.warn("OpenAPI resource not found for tool {}: {}", tool.getName(), restApiUrl);
                        continue; // Skip this tool if its spec is not found
                    }
                    apiSpecStreamForExecutor = resource.getInputStream();
                    apiSpecStreamForConverter = resource.getInputStream(); // Need a fresh stream for converter

                    currentBaseUrl = this.apiExecutor.initialize(apiSpecStreamForExecutor, null);
                    convertedTools = converter.convertOpenApiToMcpTools(apiSpecStreamForConverter, null);
                }
                if (!convertedTools.isEmpty()) {
                    this.tools.addAll(convertedTools);
                    // Store the base URL for each tool associated with this OpenAPI spec
                    for (Tool t : convertedTools) {
                        toolBaseUrls.put(t.name(), currentBaseUrl);
                    }
                }
            } catch (IOException | URISyntaxException e) {
                log.error("Error initializing OpenAPI tool {}: {}", tool.getName(), e.getMessage(), e);
                throw new ToolRegistrationException("Failed to initialize OpenAPI tool: " + tool.getName(), e);
            } catch (IllegalArgumentException e) {
                log.error("Invalid OpenAPI specification or URL for tool {}: {}", tool.getName(), e.getMessage(), e);
                throw new InvalidOpenApiSpecException("Invalid OpenAPI spec for tool: " + tool.getName(), e);
            } catch (Exception e) {
                log.error("An unexpected error occurred during OpenAPI tool initialization for {}: {}", tool.getName(), e.getMessage(), e);
                throw new ToolRegistrationException("Unexpected error during OpenAPI tool initialization: " + tool.getName(), e);
            }
        }


        updateMcpServerTools(); // Register all collected tools with McpSyncServer

        log.info("Spring-based MCP Server ready for integration.");
        log.info("Available tools: {}", this.getTools().size());
    }

    /**
     * Adds hardcoded tools to the MCP server's tool list.
     * This method scans the CommandLineTool and FileSystemTools classes
     */
    private void addHardcodedTools() {
        hardcodedToolHandlers.clear(); // Clear existing hardcoded tools on re-initialization
        //addHardcodedToolsFromClass(timeTool);
    }

    private void addHardcodedToolsFromClass(Object toolInstance) {
        for (Method method : toolInstance.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(org.springframework.ai.tool.annotation.Tool.class)) {
                try {
                    Tool mcpTool = springAIToolConverterService.convertToMcpTool(toolInstance, method);
                    BiFunction<McpSyncServerExchange, Map<String, Object>, McpSchema.CallToolResult> toolHandler =
                            springAIToolConverterService.createToolHandler(toolInstance, method);

                    this.tools.add(mcpTool);
                    this.hardcodedToolHandlers.put(mcpTool.name(), toolHandler);
                    log.debug("Added Spring AI tool: {}", mcpTool.name());
                } catch (Exception e) {
                    log.error("Error adding Spring AI tool from method {}: {}", method.getName(), e.getMessage(), e);
                    throw new ToolRegistrationException("Failed to register Spring AI tool: " + method.getName(), e);
                }
            }
        }
    }

    /**
     * Creates a list of {@link McpServerFeatures.SyncToolSpecification} objects
     * from the internal list of {@link Tool}s.
     * Each specification includes the tool definition and its execution handler.
     *
     * @return A list of tool specifications ready for the MCP synchronization server.
     */
    public List<McpServerFeatures.SyncToolSpecification> createToolSpecification() {
        List<McpServerFeatures.SyncToolSpecification> mcpTools = new ArrayList<>();
        for (Tool tool : tools) {
            final String currentToolName = tool.name();

            // Determine the appropriate handler for the tool (hardcoded or OpenAPI-based)
            BiFunction<McpSyncServerExchange, Map<String, Object>, McpSchema.CallToolResult> callToolHandler;

            if (hardcodedToolHandlers.containsKey(currentToolName)) {
                // Use the pre-defined handler for hardcoded tools
                callToolHandler = hardcodedToolHandlers.get(currentToolName);
            } else {
                // For OpenAPI-defined tools, delegate to the RestApiExecutorService
                callToolHandler = (exchange, arguments) -> callTool(currentToolName, arguments);
            }

            McpServerFeatures.SyncToolSpecification syncSpec = new McpServerFeatures.SyncToolSpecification(tool, callToolHandler);
            mcpTools.add(syncSpec);
        }
        return mcpTools;
    }

    /**
     * Executes an API call for a given tool using the {@link RestApiExecutorService}.
     * This method is primarily used for tools defined via OpenAPI specifications.
     *
     * @param toolName  The name of the tool to call.
     * @param arguments The arguments for the tool call.
     * @return A {@link McpSchema.CallToolResult} containing the result of the API call.
     * @throws IllegalArgumentException if the base URL for the tool is not found.
     * @throws ExternalApiException     if an error occurs during the external API call.
     */
    private McpSchema.CallToolResult callTool(String toolName, Object arguments) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> args = (Map<String, Object>) arguments;
            String baseUrl = toolBaseUrls.get(toolName);
            if (baseUrl == null) {
                throw new IllegalArgumentException("Base URL not found for tool: " + toolName);
            }
            // Execute the API call and return the result
            return new McpSchema.CallToolResult(apiExecutor.executeApiCall(baseUrl, toolName, args), false);
        } catch (IllegalArgumentException e) {
            log.error("Invalid arguments or base URL for tool {}: {}", toolName, e.getMessage(), e);
            return new McpSchema.CallToolResult("Error: " + e.getMessage(), true);
        } catch (Exception e) {
            log.error("Error executing API call for tool {}: {}", toolName, e.getMessage(), e);
            // Wrap generic exceptions in ExternalApiException for consistent error handling
            return new McpSchema.CallToolResult("Error: " + e.getMessage(), true);
        }
    }

    /**
     * Updates the tools registered with the {@link McpSyncServer}.
     * This method removes existing tools and adds the current set of tools.
     */
    protected void updateMcpServerTools() {
        List<McpServerFeatures.SyncToolSpecification> toolSpec = createToolSpecification();
        for (McpServerFeatures.SyncToolSpecification spec : toolSpec) {
            try {
                // Attempt to remove the tool first to ensure a clean update
                mcpSyncServer.removeTool(spec.tool().name());
            } catch (Exception e) {
                // Log a debug message if the tool doesn't exist, as removeTool might throw if not found
                log.debug("Tool {} not found on McpSyncServer during removal attempt (might be new). Message: {}", spec.tool().name(), e.getMessage());
            }
            mcpSyncServer.addTool(spec);
            log.info("Registered tool with McpSyncServer: {}", spec.tool().name());
        }
    }

    /**
     * Returns an unmodifiable list of all tools currently managed by this MCP server.
     *
     * @return A list of {@link Tool} objects.
     */
    public List<Tool> getTools() {
        return new ArrayList<>(tools); // Return a copy to prevent external modification
    }

    /**
     * Deletes a tool from the MCP server's internal list and from the {@link McpSyncServer}.
     *
     * @param toolName The name of the tool to delete.
     * @throws ToolNotFoundException if the tool with the given name is not found.
     */
    public void deleteTool(String toolName) {
        boolean removedFromList = tools.removeIf(tool -> tool.name().equals(toolName));
        if (!removedFromList) {
            throw new ToolNotFoundException("Tool not found: " + toolName);
        }
        try {
            mcpSyncServer.removeTool(toolName);
            log.info("Successfully deleted tool: {}", toolName);
        } catch (Exception e) {
            log.error("Error removing tool {} from McpSyncServer: {}", toolName, e.getMessage(), e);
            throw new ToolRegistrationException("Failed to remove tool from MCP Sync Server: " + toolName, e);
        }
    }

    /**
     * Updates the description of a tool in the configuration file and re-initializes the server.
     * This operation is only supported if the configuration was loaded from a file.
     *
     * @param toolName       The name of the tool whose description is to be updated.
     * @param newDescription The new description for the tool.
     * @throws UnsupportedOperationException if the configuration was not loaded from a file.
     * @throws ToolNotFoundException         if the tool with the given name is not found in the configuration.
     * @throws ConfigurationLoadingException if there's an error reading or writing the configuration file.
     * @throws ToolRegistrationException     if re-initialization of tools fails after update.
     */
    public void updateToolDescription(String toolName, String newDescription) {
        File loadedConfigFile = mcpConfigurationService.getLoadedConfigFile();
        boolean loadedFromResource = mcpConfigurationService.isLoadedFromResource();

        if (loadedFromResource || loadedConfigFile == null) {
            throw new UnsupportedOperationException("Cannot update tool description: Configuration was loaded from a resource or no file path was specified.");
        }

        // Create a backup of the current configuration file
        File backupFile = new File(loadedConfigFile.getAbsolutePath() + ".bak");
        try {
            Files.copy(loadedConfigFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            log.info("Created backup of configuration file at: {}", backupFile.getAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to create backup of configuration file: {}", e.getMessage(), e);
            throw new ConfigurationLoadingException("Failed to create backup of configuration file.", e);
        }

        try {
            // Read the current YAML content
            JsonNode rootNode = yamlMapper.readTree(loadedConfigFile);
            JsonNode serverNode = rootNode.get("mcp_server");
            if (serverNode == null) {
                throw new ConfigurationLoadingException("Missing 'mcp_server' section in configuration file.");
            }
            JsonNode toolsNode = serverNode.get("tools");

            if (toolsNode != null && toolsNode.isArray()) {
                boolean updated = false;
                for (JsonNode toolNode : toolsNode) {
                    if (toolNode.has("name") && toolNode.get("name").asText().equals(toolName)) {
                        ((ObjectNode) toolNode).put("description", newDescription);
                        updated = true;
                        log.info("Updated description for tool: {}", toolName);
                        break;
                    }
                }

                if (!updated) {
                    throw new ToolNotFoundException("Tool with name " + toolName + " not found in configuration.");
                }

                // Write the modified content back to the file
                yamlMapper.writerWithDefaultPrettyPrinter().writeValue(loadedConfigFile, rootNode);
                log.info("Configuration file updated successfully: {}", loadedConfigFile.getAbsolutePath());

                // Re-initialize and re-register tools to reflect the change
                initTools();

            } else {
                throw new ConfigurationLoadingException("Invalid 'tools' configuration format in " + loadedConfigFile.getAbsolutePath());
            }
        } catch (IOException e) {
            log.error("Error reading or writing configuration file: {}", e.getMessage(), e);
            // Attempt to restore from backup if an error occurs during file operation
            try {
                Files.copy(backupFile.toPath(), loadedConfigFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                log.info("Restored configuration file from backup: {}", backupFile.getAbsolutePath());
            } catch (IOException restoreEx) {
                log.error("Failed to restore configuration file from backup: {}", restoreEx.getMessage(), restoreEx);
            }
            throw new ConfigurationLoadingException("Error updating configuration file.", e);
        } catch (Exception e) {
            // Catch any other exceptions during the update process
            log.error("An unexpected error occurred during tool description update: {}", e.getMessage(), e);
            // Attempt to restore from backup
            try {
                Files.copy(backupFile.toPath(), loadedConfigFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                log.info("Restored configuration file from backup: {}", backupFile.getAbsolutePath());
            } catch (IOException restoreEx) {
                log.error("Failed to restore configuration file from backup: {}", restoreEx.getMessage(), restoreEx);
            }
            throw new ToolRegistrationException("Failed to update tool description: " + e.getMessage(), e);
        } finally {
            // Optionally delete the backup file here if not needed for recovery
            // backupFile.delete();
        }
    }

    /**
     * Registers a new OpenAPI-defined tool with the MCP server.
     * The OpenAPI specification can be provided via a URL or as a Base64 encoded string.
     *
     * @param request The {@link McpServerController.OpenApiRegistrationRequest} containing tool details.
     * @throws IllegalArgumentException    if the registration type is unsupported.
     * @throws ToolRegistrationException   if there's an error during tool registration.
     * @throws InvalidOpenApiSpecException if the provided OpenAPI specification is invalid.
     * @throws ExternalApiException        if there's an issue initializing an external API.
     */
    public void registerOpenApi(McpServerController.OpenApiRegistrationRequest request) {
        List<Tool> newTools;
        InputStream apiSpecStreamForExecutor = null;
        InputStream apiSpecStreamForConverter = null;
        boolean newToolAdded = false; // Flag to track if a new tool is added

        try {
            if ("URL".equals(request.getType())) {
                Resource resource = resourceLoader.getResource(request.getSource());
                apiSpecStreamForExecutor = resource.getInputStream();
                apiSpecStreamForConverter = resource.getInputStream();
            } else if ("BASE64".equals(request.getType())) {
                byte[] decodedBytes = Base64.getDecoder().decode(request.getSource());
                apiSpecStreamForExecutor = new ByteArrayInputStream(decodedBytes);
                apiSpecStreamForConverter = new ByteArrayInputStream(decodedBytes);
            } else {
                throw new IllegalArgumentException("Unsupported registration type: " + request.getType());
            }

            // Initialize API executor with the new spec to extract base URL and validate
            apiExecutor.initialize(apiSpecStreamForExecutor, null);
            // Convert OpenAPI spec to MCP tools
            newTools = converter.convertOpenApiToMcpTools(apiSpecStreamForConverter, null);

            for (Tool newTool : newTools) {
                // Only add if a tool with the same name doesn't already exist
                if (tools.stream().noneMatch(t -> t.name().equals(newTool.name()))) {
                    tools.add(newTool);
                    log.info("Added new OpenAPI tool: {}", newTool.name());
                    newToolAdded = true; // Set flag to true
                } else {
                    log.warn("Tool with name {} already exists. Skipping registration.", newTool.name());
                }
            }

            if (newToolAdded) {
                updateMcpServerTools(); // Register newly added tools with McpSyncServer
            }
        } catch (IOException e) {
            log.error("Error reading OpenAPI specification from source: {}", e.getMessage(), e);
            throw new InvalidOpenApiSpecException("Failed to read OpenAPI specification.", e);
        } catch (URISyntaxException e) {
            log.error("Invalid URI syntax in OpenAPI specification: {}", e.getMessage(), e);
            throw new InvalidOpenApiSpecException("Invalid URI in OpenAPI specification.", e);
        } catch (IllegalArgumentException e) {
            log.error("Invalid argument during OpenAPI registration: {}", e.getMessage(), e);
            throw new ToolRegistrationException("Invalid registration request: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("An unexpected error occurred during OpenAPI tool registration: {}", e.getMessage(), e);
            throw new ToolRegistrationException("Unexpected error during OpenAPI tool registration.", e);
        } finally {
            // Ensure streams are closed
            try {
                if (apiSpecStreamForExecutor != null) apiSpecStreamForExecutor.close();
                if (apiSpecStreamForConverter != null) apiSpecStreamForConverter.close();
            } catch (IOException e) {
                log.warn("Error closing input streams during OpenAPI registration: {}", e.getMessage());
            }
        }
    }
}
