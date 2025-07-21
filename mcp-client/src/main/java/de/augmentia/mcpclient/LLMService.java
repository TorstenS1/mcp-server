package de.augmentia.mcpclient;

import io.micrometer.observation.ObservationRegistry;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;


@Service
public class LLMService {

    private final McpClientProperties properties;

    private ChatClient chatClient;
    private static final Logger log = LoggerFactory.getLogger(LLMService.class);

    @Autowired
    private OpenAiChatProperties props;

    public LLMService(McpClientProperties properties, RestClient.Builder restClientBuilder, ToolCallingManager toolCallingManager, RetryTemplate retryTemplate,           // Auto-configured by Spring AI
                      ObservationRegistry observationRegistry) {
        this.properties = properties;

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(properties.getOpenaiApiUrl())
                .apiKey(properties.getOpenaiApiKey())
                .completionsPath("/chat/completions")
                .restClientBuilder(restClientBuilder)
                .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(properties.getProperty("llm.model_name"))
                .temperature(0.2d)
                .build();

        // Create the OpenAiChatModel, passing the custom OpenAiApi and options
        ToolCallingManager retry;
        RetryTemplate opserver;
        OpenAiChatModel chatModel = new OpenAiChatModel(openAiApi, options, toolCallingManager, retryTemplate, observationRegistry);

        initializeMcpClients(chatModel);

    }

    public void initializeMcpClients(OpenAiChatModel openAiChatModel) {
        log.info("Initializing MCP clients from database...");

        // Get the filtered tool providers
        List<SyncMcpToolCallbackProvider> filteredToolProviders = getAllToolCallbackProviders();

        // Build the ChatClient with the system prompt and filtered tools
        String systemPrompt = properties.getProperty("prompt.system");
        if (systemPrompt == null || systemPrompt.isEmpty()) {
            log.warn("System prompt not found in configuration. Using default system prompt.");
            systemPrompt = "You are a helpful assistant"; // Fallback default
        }

        this.chatClient = ChatClient.builder(openAiChatModel)
//                .defaultOptions(OpenAiChatOptions.builder()
//                        .model(properties.getProperty("llm.model_name"))
//                        .build())
                .defaultSystem(systemPrompt)
                .defaultToolCallbacks(filteredToolProviders.toArray(new SyncMcpToolCallbackProvider[0]))
                .build();

        log.info("MCP Client Service initialized with {} MCP clients and {} tool providers.", 1, filteredToolProviders.size());
    }

    /**
     * Retrieves a list of {@link SyncMcpToolCallbackProvider} instances.
     * These providers are filtered based on the tools configured in the application,
     * ensuring that only relevant MCP tools are exposed to the ChatClient.
     *
     * @return A list of configured SyncMcpToolCallbackProvider instances.
     */
    public List<SyncMcpToolCallbackProvider> getAllToolCallbackProviders() {

        McpSyncClient syncClient = createMcpClient((String) properties.get("mcp_server.url"));
        List<McpSchema.Tool> tools = syncClient.listTools().tools().stream().toList();

        // Define a predicate to filter tools based on the allowedToolNames list.
        // This ensures only tools explicitly configured are considered.
        BiPredicate<McpSyncClient, McpSchema.Tool> toolFilter =
                (client, tool) -> properties.getEnabledTools().contains(tool.name());

        // Stream through cached MCP clients and create a SyncMcpToolCallbackProvider for each,
        // applying the defined tool filter.
        List<SyncMcpToolCallbackProvider> filteredProviders = new ArrayList<>();
        // Collecting providers based on the configured tools and their associated MCP clients.
        // This ensures that only tools that are configured in the application are included in the
        filteredProviders.add(new SyncMcpToolCallbackProvider(toolFilter, syncClient));

        return filteredProviders;
    }


    public String callLLM(String prompt) {
        if (chatClient == null) {
            log.error("ChatClient is not initialized. Cannot inquire.");
            return "Error: ChatClient not available.";
        }
        log.info("Inquiring ChatClient with message: {} ", prompt);
        String content = chatClient
                .prompt()
                .user(prompt)
                .call()
                .content();

        log.info("ChatClient response: {}", content);
        return content;

    }

    /**
     * Creates and initializes a new McpSyncClient for the given URL.
     *
     * @param url The URL of the MCP server.
     * @return An initialized McpSyncClient instance.
     */
    private McpSyncClient createMcpClient(String url) {
        var client = McpClient
                .sync(HttpClientSseClientTransport.builder(url).build())
                .build();
        client.initialize();
        return client;
    }


}
