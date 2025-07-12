package com.example.mcpclient;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class McpClientController {

    private final McpClientProperties properties;

    public McpClientController(McpClientProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/hello")
    public String hello() {
        return "Hello from MCP Client! Enabled tools: " + properties.getEnabledTools();
    }
}
