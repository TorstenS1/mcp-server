
# MCP Server

This project is a Spring Boot application that implements a Model Context Protocol (MCP) server. The server can expose tools to AI models, and it can convert OpenAPI specifications into MCP tools.

## Building and Running the Project

To build the project, run the following command:

```bash
mvn clean install
```

To run the project, run the following command:

```bash
java -jar target/mcp-server-1.0.0.jar
```

## API Endpoints

The following API endpoints are available:

*   `GET /api/tools`: Returns a list of all available tools.
*   `GET /api/tools/{id}`: Returns the tool with the specified ID.
*   `DELETE /api/tools/{id}`: Deletes the tool with the specified ID.
*   `POST /api/register-openapi`: Registers a new tool from an OpenAPI specification.
*   `POST /api/update-tool-description`: Updates the description of a tool.

## Configuring the Server

The server can be configured by creating a `mcp_server.yml` file in the `src/main/resources` directory. The following properties can be configured:

*   `mcp_server.name`: The name of the server.
*   `mcp_server.url`: The URL of the server.
*   `mcp_server.port_number`: The port number of the server.
*   `mcp_server.tools`: A list of tools to expose.
*   `mcp_server.env`: A map of environment variables.

## Adding New Tools

New tools can be added to the server in two ways:

1.  **By adding a new tool to the `mcp_server.yml` file.**
2.  **By using the `/api/register-openapi` endpoint to register a new tool from an OpenAPI specification.**

## Running the Tests

To run the tests, run the following command:

```bash
mvn test
```
