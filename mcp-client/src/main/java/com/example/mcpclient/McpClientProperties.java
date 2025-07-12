package com.example.mcpclient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

@Getter
@Setter
@ConfigurationProperties(prefix = "mcp.client")
public class McpClientProperties {

    @Autowired
    private ResourceLoader resourceLoader;

    private String openaiApiKey;
    private String openaiApiUrl;
    private List<String> enabledTools;

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());


    @Value("${config.directory:/agents}")
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
        Resource resource = resourceLoader.getResource(configFilePath);

        JsonNode rootNode = yamlMapper.readTree(resource.getInputStream());


    }
}
