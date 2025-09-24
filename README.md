# Spring AI MCP Server and Client

This project contains two main components:

*   **mcp-server**: A Spring Boot application that implements a Model Context Protocol (MCP) server. It exposes tools to AI models and can convert OpenAPI specifications into MCP tools.
*   **mcp-client**: A Spring Boot application that acts as a client to the MCP server. It dynamically discovers and uses tools exposed by the server to interact with a Large Language Model (LLM).
*   **python_mcp_server**: A first hot Python implementation equivalent of Spring mcp-server.

## Getting Started

This project is designed to be run using Docker Compose, which orchestrates all the services (Java MCP Server, Python MCP Server, Sample Tools App, and MCP Client).

### Prerequisites

*   **Docker and Docker Compose:** You must have Docker and Docker Compose installed on your system.
*   **OpenAI API Key:** The `mcp-client` requires a valid OpenAI API key. Before running, edit `mcp-client/config.yml` and replace `YOUR_OPENAI_API_KEY` with your actual key.

### Building Projects

To build all the Java projects and install Python dependencies, use the provided build script:

```bash
chmod +x build-all.sh
./build-all.sh
```

### Running the Project

To start all services (Java MCP Server, Python MCP Server, Sample Tools App, and MCP Client) using Docker Compose:

```bash
docker-compose up --build
```

Once running, the services will be accessible on the following ports on your host machine:
*   **Java MCP Server:** `http://localhost:18001`
*   **Python MCP Server:** `http://localhost:18002`
*   **Sample Tools App:** `http://localhost:18003`
*   **MCP Client:** `http://localhost:18004`

## How They Work Together

The `mcp-server` acts as a tool provider for the `mcp-client`. The client connects to the server to get a list of available tools, which it then uses to interact with the configured LLM. This allows for a separation of concerns, where the server manages the tools and the client focuses on the interaction with the LLM.

## How to Register New Tools

Once you have a service with an OpenAPI specification, you can register it as a tool in either the Python or Java MCP server.

### Python MCP Server

Add the tool to the `tools` list in the `python_mcp_server/mcp_server.yml` file. For example, to add the `sample-tools-app`:

```yaml
mcp_server:
  host: 0.0.0.0
  port: 18002
  tools:
  - description: API for managing users and orders
    name: sample-tools
    rest_api_url: http://sample-tools-app:18003/v3/api-docs
```

### Java MCP Server

The Java MCP Server can load tools on startup from an `mcp_server.yml` file located in its `/config` directory. An example is provided at `mcp-server/config/mcp_server.yml`.

Alternatively, you can dynamically register tools by sending a POST request to the `/api/register-openapi` endpoint. When running in Docker, use the service name (e.g., `sample-tools-app`) as the hostname for the `source` URL.

```bash
curl -X POST http://localhost:18001/api/register-openapi \
-H "Content-Type: application/json" \
-d '{
      "type": "URL",
      "source": "http://sample-tools-app:18003/v3/api-docs",
      "friendlyName": "sample-tools",
      "description": "Tools for managing users and orders"
    }'
```

## End-to-End Testing

This project includes a full end-to-end test to verify that all components (`mcp-server-java`, `sample-tools-app`, and `mcp-client`) are working together correctly.

### Prerequisites

1.  **Docker and Docker Compose:** You must have Docker and Docker Compose installed.
2.  **OpenAI API Key:** The `mcp-client` requires a valid OpenAI API key to function. Before running the test, you must edit the `mcp-client/config.yml` file and replace the placeholder `YOUR_OPENAI_API_KEY` with your actual key.

### Running the Test

A shell script `test-e2e.sh` is provided to automate the entire process.

1.  **Make the script executable:**
    ```bash
    chmod +x test-e2e.sh
    ```

2.  **Run the script:**
    ```bash
    ./test-e2e.sh
    ```

The script will:
- Build and start all the necessary services in Docker.
- Wait for the services to initialize.
- Send a prompt to the `mcp-client` that requires it to use a tool from the `sample-tools-app`.
- Print the final response from the LLM.
- Shut down and clean up all the services.

## Contributing

Contributions are welcome! Please feel free to submit a pull request.

## License

This project is licensed under the MIT License. See the `LICENSE` file for more information.
