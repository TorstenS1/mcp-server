package com.example.mcpclient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LLMService {

    private final McpClientProperties properties;

    private final ChatClient chatClient;
    private static final Logger log = LoggerFactory.getLogger(LLMService.class);

    

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    public LLMService(McpClientProperties properties, ChatClient.Builder chatClientBuilder) {
        this.properties = properties;

        this.chatClient = chatClientBuilder
                .defaultSystem("Please answer short without not requested explanations.")
                .defaultTools(getEnabledTools()) // Registering the TimeTool as a default tool
                .build();
    }

    private Object getEnabledTools() {
        return null;

    }

    public String callLLM(String prompt) {
        // This is a placeholder for actual LLM interaction.
        // In a real application, you would use the properties.getOpenaiApiKey()
        // and properties.getOpenaiApiUrl() to configure your OpenAI client
        // and make API calls.
        System.out.println("Calling LLM with prompt: " + prompt);
        System.out.println("Using API Key: " + properties.getOpenaiApiKey());
        System.out.println("Using API URL: " + properties.getOpenaiApiUrl());
        System.out.println("Enabled Tools: " + properties.getEnabledTools());
        return "LLM response for: " + prompt + " (using configured tools)";
    }
}
