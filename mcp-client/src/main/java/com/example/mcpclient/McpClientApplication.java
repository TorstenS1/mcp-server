package com.example.mcpclient;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(McpClientProperties.class)
public class McpClientApplication {

    @Autowired
    private McpClientProperties mcpClientProperties;

    public static void main(String[] args) {
        SpringApplication.run(McpClientApplication.class, args);
    }

    @PostConstruct
    public void readConfigOnStartup() {
        try {
            mcpClientProperties.readConfigFile("config.yml");
        } catch (java.io.IOException e) {
            System.err.println("Error reading config file: " + e.getMessage());
        }
    }
}
