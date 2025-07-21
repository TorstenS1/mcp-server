package de.augmentia.mcpclient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.*;

@Getter
@Setter
@PropertySource("file:config.yml")
@ConfigurationProperties(prefix = "client")
public class McpClientProperties {

    @Autowired
    private ResourceLoader resourceLoader;

    private String openaiApiKey;
    private String openaiApiUrl;
    private List<String> enabledTools;

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());


    @Value("${config.directory:}")
    private String configDirectory;

    private final Properties configurations = new Properties();
    private HashMap<String, Object> envMap = new HashMap<>();

    private String configFilePath;

    /**
     * Sets a configuration property.
     *
     * @param key   The key of the property.
     * @param value The value of the property.
     */
    public void set(Object key, Object value) {
        configurations.put(key, value);
    }

    /**
     * Retrieves a configuration property as a String.
     *
     * @param key The key of the property.
     * @return The property value, or null if not found.
     */
    public String getProperty(String key) {
        return configurations.getProperty(key);
    }

    /**
     * Retrieves a configuration property as an Object.
     *
     * @param key The key of the property.
     * @return The property value, or null if not found.
     */
    public Object get(Object key) {
        return configurations.get(key);
    }

    /**
     * Checks if a configuration property exists.
     *
     * @param key The key of the property.
     * @return True if the property exists, false otherwise.
     */
    public boolean contains(Object key) {
        return configurations.containsKey(key);
    }

    public void readConfigFile(String configFilePath) throws java.io.IOException {
        envMap = new HashMap<>();
        Resource resource = resourceLoader.getResource("file:" + configFilePath);

        JsonNode rootNode = yamlMapper.readTree(resource.getInputStream());
        JsonNode client = Optional.ofNullable(rootNode.get("client"))
                .orElseThrow(() -> new IOException("Missing 'client' section in configuration file."));

        JsonNode llm = Optional.ofNullable(client.get("llm"))
                .orElseThrow(() -> new IOException("Missing 'llm' section in configuration file."));
        JsonNode api_key = Optional.ofNullable(llm.get("api-key"))
                .orElseThrow(() -> new IOException("Missing 'api_key' in 'llm' section in configuration file."));
        JsonNode api_url = Optional.ofNullable(llm.get("base-url"))
                .orElseThrow(() -> new IOException("Missing 'api_url' in 'llm' section in configuration file."));
        JsonNode model = Optional.ofNullable(llm.get("model_name"))
                .orElseThrow(() -> new IOException("Missing 'model' in 'llm' section in configuration file."));

        this.setOpenaiApiKey(api_key.asText());
        this.setOpenaiApiUrl(api_url.asText());
        this.set("llm.model_name", model.asText());

        this.loadToolsConfig(client);
        this.loadEnv(client);
        this.loadPrompts(client);

        this.loadMcpServerConfig(client);

    }

    //mcp_server:
    //url: "http://localhost:8089/mcp"
    //port_number: 8110

    private void loadMcpServerConfig(JsonNode client) {
        JsonNode prompts = client.get("prompts");

        JsonNode mcpServerNode = client.get("mcp_server");
        if (mcpServerNode != null) {
            String url = mcpServerNode.get("url").asText();
            if (url != null && !url.isEmpty()) {
                configurations.put("mcp_server.url", url);
            } else {
                throw new IllegalArgumentException("Missing or empty 'url' in 'mcp_server' section in configuration file.");
            }
        }
    }

    private void loadPrompts(JsonNode client) throws IOException {
        JsonNode prompts = client.get("prompts");
        if (prompts != null && prompts.isArray()) {
            // Iterate over all fields (key-value pairs) in the JsonNode
            Iterator<Map.Entry<String, JsonNode>> fields = prompts.fields();
            String promptName = "system";
            String prompt = "You are a helpful assistant.";
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if ("name".equalsIgnoreCase(field.getKey()) && field.getValue().isTextual()) {
                    promptName = field.getValue().asText();
                    continue;
                }
                if ("prompt".equalsIgnoreCase(field.getKey()) && field.getValue().isTextual()) {
                    prompt = field.getValue().asText();
                    continue;
                }

                configurations.put("prompt." + promptName, prompt);
            }
        } else {
            throw new IOException("Missing or invalid 'prompts' section in configuration file.");
        }
    }

    private void loadEnv(JsonNode client) {
        JsonNode envs = client.get("env");
        if (envs != null && envs.isObject()) {
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

                envMap.put("env." + key, value);
            }
        }
        configurations.putAll(envMap);
    }

    /**
     * Loads tool configurations.
     *
     * @param clientNode The "agent" JsonNode.
     */
    private void loadToolsConfig(JsonNode clientNode) {
        JsonNode toolsNode = clientNode.get("tools");
        if (toolsNode != null && toolsNode.isArray()) {
            enabledTools = new ArrayList<>();
            for (JsonNode toolNode : toolsNode) {
                Optional.ofNullable(toolNode.get("name")).map(JsonNode::asText).ifPresent(enabledTools::add);
            }
        }
    }

}
