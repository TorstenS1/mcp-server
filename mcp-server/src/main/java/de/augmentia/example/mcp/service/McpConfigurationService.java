package de.augmentia.example.mcp.service;

import de.augmentia.example.mcp.exception.ConfigurationLoadingException;
import de.augmentia.example.mcp.model.OpenApiDef;
import de.augmentia.example.mcp.model.McpServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Service responsible for loading and parsing the MCP server configuration
 * from 'mcp_server.yml'. It supports loading from a file path or a classpath resource.
 */
@Service
@Slf4j
public class McpConfigurationService {

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @Autowired
    private ResourceLoader resourceLoader;

    @Value("${config.directory:}")
    private String configFilePath;

    @Value("${config.resource.path:mcp_server.yml}")
    private String configResourcePath;

    private File loadedConfigFile; // To store the file if loaded from path
    private boolean loadedFromResource; // To indicate if loaded from resource

    private HashMap<String, String> envMap = new HashMap<>();

    /**
     * Reads the MCP server configurations from the configured file path or classpath resource.
     * Prioritizes loading from the file path if specified and exists, otherwise falls back to the classpath resource.
     *
     * @return A {@link McpServer} object containing the parsed configuration.
     * @throws ConfigurationLoadingException if the configuration cannot be loaded from any source or is malformed.
     */
    public McpServer loadMcpServerConfiguration() {
        JsonNode mcpConfigNode = null;

        try {
            Resource resource = resourceLoader.getResource(configFilePath + "/" + configResourcePath);
            if (resource.exists()) {
                mcpConfigNode = yamlMapper.readTree(resource.getInputStream());
                loadedConfigFile = null; // Not a file, so no file to store
                loadedFromResource = true;
                log.info("Loaded MCP server configuration from resource: {}", configFilePath + "/" + configResourcePath);
            } else {
                log.error("Config resource not found: {}. Cannot load MCP server configuration.", configResourcePath);
                throw new ConfigurationLoadingException("Cannot load MCP server configuration: Resource not found.");
            }
        } catch (IOException e) {
            log.error("Error loading config from resource: {}", configResourcePath, e);
            throw new ConfigurationLoadingException("Error loading MCP server configuration from resource.", e);
        }

        if (mcpConfigNode == null) {
            throw new ConfigurationLoadingException("Failed to load MCP server configuration from any source.");
        }

        return parseMcpServerConfiguration(mcpConfigNode);
    }

    /**
     * Parses the JSON node representing the MCP server configuration into an {@link McpServer} object.
     *
     * @param mcpConfigNode The JsonNode containing the MCP server configuration.
     * @return A populated {@link McpServer} object.
     * @throws ConfigurationLoadingException if the configuration format is invalid.
     */
    private McpServer parseMcpServerConfiguration(JsonNode mcpConfigNode) {
        McpServer mcpServer = new McpServer();
        envMap = new HashMap<>();
        try {
            JsonNode serverNode = mcpConfigNode.get("mcp_server");
            if (serverNode == null) {
                throw new ConfigurationLoadingException("Missing 'mcp_server' section in configuration.");
            }

            JsonNode envs=serverNode.get("env");
            if (envs.isObject()) {
                // Iterate over all fields (key-value pairs) in the JsonNode
                Iterator<Map.Entry<String, JsonNode>> fields = envs.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    String key = field.getKey();
                    JsonNode valueNode = field.getValue();

                    // Convert the JsonNode value to a String.
                    // .asText() is generally safe and will convert numbers, booleans, etc., to their string representation.
                    // For complex objects or arrays, it will give their JSON string representation.
                    String value = valueNode.asText();

                    envMap.put(key, value);
                }
            }
            mcpServer.setEnvironmentVariables(envMap);

            mcpServer.setName(serverNode.get("name").asText());
            mcpServer.setUrl(serverNode.get("url").asText());
            mcpServer.setPort_number(serverNode.get("port_number").asInt());

            JsonNode toolsNode = serverNode.get("tools");
            if (toolsNode != null && toolsNode.isArray()) {
                for (JsonNode toolNode : toolsNode) {
                    OpenApiDef tool = new OpenApiDef();
                    tool.setName(toolNode.get("name").asText());
                    tool.setDescription(toolNode.get("description").asText());
                    tool.setRestApiUrl(toolNode.get("rest_api_url").asText());
                    mcpServer.addTool(tool);
                }
            } else if (toolsNode != null) { // If 'tools' exists but is not an array
                throw new ConfigurationLoadingException("Invalid 'tools' configuration format. Must be an array.");
            }
            // If toolsNode is null, it means no tools are defined, which is acceptable.

        } catch (Exception e) {
            throw new ConfigurationLoadingException("Error parsing MCP server configuration: " + e.getMessage(), e);
        }
        return mcpServer;
    }

    /**
     * Returns the file from which the configuration was loaded, if it was loaded from a file path.
     *
     * @return The loaded configuration file, or null if loaded from a resource or not yet loaded.
     */
    public File getLoadedConfigFile() {
        return loadedConfigFile;
    }

    /**
     * Indicates whether the configuration was loaded from a classpath resource.
     *
     * @return true if loaded from a resource, false otherwise.
     */
    public boolean isLoadedFromResource() {
        return loadedFromResource;
    }

    /**
     * Returns the environment variables map parsed from the configuration.
     *
     * @return A HashMap containing environment variable names and their values.
     */
    public HashMap<String, String> getEnvironmentVariables() {
        return envMap;
    }
}
