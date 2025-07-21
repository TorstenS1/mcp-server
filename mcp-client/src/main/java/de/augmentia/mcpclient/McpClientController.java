package de.augmentia.mcpclient;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@Slf4j
public class McpClientController {

    private final McpClientProperties properties;
    private final LLMService llmService;


    public McpClientController(McpClientProperties properties, LLMService llmService) {
        this.properties = properties;
        this.llmService = llmService;

    }

    @GetMapping("/hello")
    public String hello() {
        return "Hello from MCP Client! Enabled tools: " + properties.getEnabledTools();
    }

    @PostMapping("/prompt")
    public String prompt(@RequestBody String userPrompt) {
        return llmService.callLLM(userPrompt);
    }

    @GetMapping("/getTools")
    public String testTools() {
        log.info("1) Get Available Tools: ");
        List<SyncMcpToolCallbackProvider> allToolCallbackProviders = llmService.getAllToolCallbackProviders();
        return allToolCallbackProviders.stream()
                .map(SyncMcpToolCallbackProvider::toString)
                .collect(Collectors.joining("\n"));

    }

}
