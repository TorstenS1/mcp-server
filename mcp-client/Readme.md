# Spring AI MCP Client Example

This project is a Spring Boot application that demonstrates the use of the Model Context Protocol (MCP) to interact with a Large Language Model (LLM) like OpenAI's GPT. It acts as a client to an MCP server, dynamically discovering and using tools exposed by the server.

## Features

*   Connects to an OpenAI-compatible LLM.
*   Connects to an MCP server to discover available tools.
*   Filters and uses only the tools enabled in the configuration.
*   Provides a REST API to interact with the LLM.
*   Configurable through an external `config.yml` file.

## How it Works

The application is structured as a standard Spring Boot project with the following key components:

*   **`McpClientApplication`**: The main entry point of the application. It loads the configuration from `config.yml` on startup.
*   **`McpClientProperties`**: A Spring `@ConfigurationProperties` class that loads and holds the application's configuration from `config.yml`. It reads LLM details (API key, URL, model), enabled tools, MCP server URL, and system prompts.
*   **`LLMService`**: This is the core service of the application. It initializes the `OpenAiChatModel` and the `McpSyncClient`. It discovers tools from the MCP server, filters them based on the `enabledTools` list in the configuration, and creates `SyncMcpToolCallbackProvider` instances. It then builds a `ChatClient` with the configured system prompt and the filtered tool callbacks. The `callLLM` method uses this `ChatClient` to send prompts to the LLM.
*   **`McpClientController`**: This controller exposes REST endpoints for interacting with the application.

## Configuration

The application is configured via a `config.yml` file located in the root of the project directory. Here is an example of the configuration structure:

```yaml
client:
  llm:
    model_name: "gpt-4"
    openai-api-url: https://api.openai.com/v1
    openai-api-key: YOUR_OPENAI_API_KEY
  mcp_server:
    url: "http://mcp-server-java:18001/mcp/messages"
  enabledTools:
    - "getAllUsers"
    - "getUserById"
    - "getOrderDetails"
  prompt:
    system: "You are a helpful assistant that can access tools to get information about users and orders. When asked a question, use the available tools to find the answer."
```

## API Endpoints

The following REST endpoints are available:

*   **`GET /hello`**: Returns a greeting and the list of enabled tools.
*   **`POST /prompt`**: Takes a plain text user prompt in the request body and returns the LLM's response as a string.
*   **`GET /getTools`**: Returns a list of the names of the tools that are currently available and configured.

## How to Run

This client is designed to be run as part of the overall project using Docker Compose. Refer to the main `README.md` in the project root for instructions on how to build and run the entire project.