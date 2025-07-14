# MCP Server (Python FastAPI)

This project implements a Multi-Capability Platform (MCP) server using Python FastAPI. It allows exposing external REST APIs as "tools" to AI models and other clients, leveraging OpenAPI specifications for tool definition and the MCP Python SDK for core MCP functionalities.

## Building and Running the Project

### Prerequisites
*   Python 3.8+
*   `pip` (Python package installer)

### Setup and Installation

1.  **Navigate to the Python directory:**
    ```bash
    cd python
    ```

2.  **Create and activate a virtual environment (recommended):**
    ```bash
    python3 -m venv venv
    source venv/bin/activate
    ```

3.  **Install dependencies:**
    ```bash
    pip install -r requirements.txt
    ```

### Running the Server

From the `python` directory, run the following command:

```bash
uvicorn app.main:app --reload
```
The server will typically run on `http://127.0.0.1:8000`. You can access the interactive API documentation at `http://127.0.0.1:8000/docs`.

**Note:** An SSE MCP Server is mounted at `http://127.0.0.1:8000/mcp-sse` to handle MCP client connections.

## API Endpoints

The following API endpoints are available:

*   `GET /api/tools`: Returns a list of all available tools.
*   `GET /api/tools/{id}`: Returns the tool with the specified ID.
*   `DELETE /api/tools/{id}`: Deletes the tool with the specified ID.
*   `POST /api/register-openapi`: Registers a new tool from an OpenAPI specification.
*   `POST /api/update-tool-description`: Updates the description of a tool.
*   `POST /api/tools/{tool_id}/execute`: Executes a registered tool with the given arguments.

## Configuring the Server

The server can be configured by creating a `mcp_server.yml` file in the `python` directory. An example `mcp_server.yml` might look like this:

```yaml
mcp_server:
  tools:
    - name: user-api
      rest_api_url: https://raw.githubusercontent.com/example/user-api/main/openapi.yml
```

## Adding New Tools

New tools can be added to the server in two ways:

1.  **By adding a new tool entry to the `mcp_server.yml` file.** Tools defined here will be loaded automatically when the server starts.
2.  **By using the `/api/register-openapi` endpoint** to register a new tool from an OpenAPI specification provided as a URL or a Base64 encoded string.

## Running the Tests

From the `python` directory, with your virtual environment activated, run the following command:

```bash
PYTHONPATH=. pytest
```

## Data Flow Description

The system acts as a Multi-Capability Platform (MCP) server that dynamically registers and executes external REST APIs as "tools" for clients. It leverages OpenAPI specifications to understand these external APIs and the MCP Python SDK (`FastMCP`) to expose them.

### 1. Tool Registration and Discovery

This flow describes how the server learns about available tools and presents them to a client.

1.  **OpenAPI Definition Source**: The process begins with an OpenAPI definition, either loaded from `mcp_server.yml` (at server startup) or provided dynamically via a BASE64 encoded string or URL (at runtime).
2.  **`McpConfigurationService`**: If loading from `mcp_server.yml`, this service reads the configuration to identify the `rest_api_url` for each tool's OpenAPI specification.
3.  **`OpenApiToMcpConverter`**:
    *   Fetches and parses the OpenAPI specification.
    *   For each operation (endpoint) defined in the OpenAPI spec, it extracts metadata like `operationId` (which becomes the tool's `name`), `description`, and `input_schema`.
    *   Crucially, it **dynamically creates a Python function** for each operation. This function is designed to act as the "executor" for that specific tool, encapsulating the logic to call the actual external REST API.
    *   It returns a tuple containing this dynamic function, the tool's name, description, and input schema.
4.  **`McpServerService`**:
    *   Receives these tuples from the `OpenApiToMcpConverter`.
    *   It registers the dynamically created function with the `FastMCP` server using `self.mcp_server.add_tool()`. `FastMCP` uses the function's signature (which was dynamically generated based on the OpenAPI `input_schema`) to understand the tool's parameters.
    *   It also maintains an internal list (`self.tools`) of Pydantic `Tool` models, which are simplified representations of the tools, suitable for exposure via the FastAPI endpoints.
5.  **Client Discovery**: When an MCP client (e.g., a UI or another service) requests the list of available tools (e.g., `GET /api/tools`), FastAPI routes this request to the `McpServerService`. The service retrieves the internal `Tool` models, converts them to JSON, and sends them back to the client.

### 2. Tool Execution (via FastAPI REST API)

This flow describes how a client requests a tool to be executed via the FastAPI REST API and how that request is translated into an external REST API call.

1.  **Client Execution Request (FastAPI)**: An MCP client sends a request to execute a specific tool (e.g., `POST /api/tools/{tool_id}/execute`) along with the necessary arguments.
2.  **FastAPI Endpoint**: FastAPI receives the request and routes it to `McpServerService.execute_tool()`.
3.  **`McpServerService.execute_tool()`**:
    *   It retrieves the corresponding `McpTool` object (which contains the dynamically created function) from the `FastMCP` server.
    *   It then directly invokes the `function` attribute of this `McpTool` object, passing the client-provided arguments.
4.  **Dynamic Function (within `OpenApiToMcpConverter`)**:
    *   This is the dynamically generated Python function that was created during tool registration.
    *   It receives the arguments and, using the `RestApiExecutorService` instance it was initialized with, calls `execute_api_call()`.
5.  **`RestApiExecutorService`**:
    *   Receives the `base_url`, `tool_name` (operationId), and `arguments`.
    *   It looks up the full OpenAPI specification (which it stored during its own initialization by the `OpenApiToMcpConverter`).
    *   It identifies the specific REST API path and HTTP method corresponding to the `tool_name`.
    *   It constructs the complete HTTP request (URL, query parameters, request body) based on the OpenAPI definition and the provided arguments.
    *   It makes the actual HTTP request to the **External REST API** using the `requests` library.
    *   It handles the HTTP response, including error handling, and returns the parsed JSON response.
6.  **Result Propagation**: The result from `RestApiExecutorService` is returned back through the dynamic function, then to `McpServerService.execute_tool()`, and finally, FastAPI sends it as a JSON response back to the MCP client.

### 3. Tool Execution (via SSE MCP Client)

This flow describes how an MCP client connects via Server-Sent Events (SSE) and executes a tool.

1.  **MCP Client Connection**: An MCP client establishes an SSE connection to the FastAPI server at the `/mcp-sse` endpoint (e.g., `http://127.0.0.1:8000/mcp-sse`).
2.  **`FastMCP` (SSE Transport)**: The `FastMCP` instance, mounted within the FastAPI application, handles the SSE communication. It receives MCP messages, including tool execution requests.
3.  **`FastMCP` (Tool Invocation)**: When `FastMCP` receives a tool execution request, it identifies the requested tool and invokes its associated dynamic function.
4.  **Dynamic Function (within `OpenApiToMcpConverter`)**: (Same as step 4 in FastAPI execution flow) This dynamically generated Python function is executed. It uses the `RestApiExecutorService` to make the actual REST API call to the external service.
5.  **`RestApiExecutorService`**: (Same as step 5 in FastAPI execution flow) Executes the REST API call and returns the result.
6.  **Result Propagation (back to SSE Client)**: The result flows back through the dynamic function to `FastMCP`. `FastMCP` then sends the result back to the connected MCP client via the SSE connection.

## Data Flow Diagram

```mermaid
graph TD
    subgraph Tool Registration & Discovery
        A[OpenAPI Definition<br>(mcp_server.yml / BASE64 / URL)] --> B(McpConfigurationService);
        B -- Provides OpenAPI Source --> C(OpenApiToMcpConverter);
        C -- Parses OpenAPI & Creates --> D{Dynamic Function<br>+ Tool Metadata};
        D -- (fn, name, desc, schema) --> E[McpServerService];
        E -- Registers Tool --> F[FastMCP Server];
        E -- Stores Internal Pydantic Tool Model --> G[FastAPI /api/tools Endpoint];
        G -- Serves JSON Tool Descriptions --> H[MCP Client (Discovery)];
    end

    subgraph Tool Execution (FastAPI REST API)
        I[MCP Client (Execution Request)] --> J(FastAPI /api/tools/{tool_id}/execute Endpoint);
        J -- Calls execute_tool() --> K[McpServerService.execute_tool()];
        K -- Invokes Dynamic Function<br>(from FastMCP Server) --> L{Dynamic Function<br>(within OpenApiToMcpConverter)};
        L -- Calls execute_api_call() --> M[RestApiExecutorService];
        M -- Makes HTTP Request --> N[External REST API];
        N -- HTTP Response --> M;
        M -- Returns API Result --> L;
        L -- Returns Execution Result --> K;
        K -- Sends JSON Response --> J;
        J -- Sends JSON Response --> I;
    end

    subgraph Tool Execution (SSE MCP Client)
        O[MCP Client (SSE Connection)] --> P(FastAPI /mcp-sse Endpoint);
        P -- Handled by FastMCP SSE Transport --> F;
        F -- Invokes Dynamic Function --> L;
        L -- Calls execute_api_call() --> M;
        M -- Makes HTTP Request --> N;
        N -- HTTP Response --> M;
        M -- Returns API Result --> L;
        L -- Returns Execution Result --> F;
        F -- Sends Result via SSE --> P;
        P -- Sends Result via SSE --> O;
    end
```