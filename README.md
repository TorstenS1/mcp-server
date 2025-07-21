# Spring AI MCP Server and Client

This project contains two main components:

*   **mcp-server**: A Spring Boot application that implements a Model Context Protocol (MCP) server. It exposes tools to AI models and can convert OpenAPI specifications into MCP tools.
*   **mcp-client**: A Spring Boot application that acts as a client to the MCP server. It dynamically discovers and uses tools exposed by the server to interact with a Large Language Model (LLM).
*   **python_mcp_server**: A first hot Python implementation equivalent of Spring mcp-server.

## mcp-server

The MCP server manages and exposes tools that can be used by AI models.

### Features

*   Exposes a list of available tools.
*   Can register new tools from OpenAPI specifications.
*   Allows for updating tool descriptions.
*   Configurable via a `mcp_server.yml` file.

### How to Run

1.  Build the project:
    ```bash
    cd mcp-server
    mvn clean install
    ```
2.  Run the application:
    ```bash
    java -jar target/mcp-server-1.0.0-SNAPSHOT.jar
    ```

## mcp-client

The MCP client connects to an LLM and uses the tools provided by the MCP server.

### Features

*   Connects to an OpenAI-compatible LLM.
*   Discovers and filters tools from the MCP server.
*   Provides a REST API to interact with the LLM.
*   Configurable via a `config.yml` file.

### How to Run

1.  Create a `config.yml` file in the root of the `mcp-client` project.
2.  Build the project:
    ```bash
    cd mcp-client
    mvn clean install
    ```
3.  Run the application:
    ```bash
    java -jar target/mcp-client-0.0.1-SNAPSHOT.jar
    ```

## How They Work Together

The `mcp-server` acts as a tool provider for the `mcp-client`. The client connects to the server to get a list of available tools, which it then uses to interact with the configured LLM. This allows for a separation of concerns, where the server manages the tools and the client focuses on the interaction with the LLM.

## Contributing

Contributions are welcome! Please feel free to submit a pull request.

## License

This project is licensed under the MIT License. See the `LICENSE` file for more information.