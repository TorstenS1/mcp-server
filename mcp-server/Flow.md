```mermaid
graph TD
    subgraph Tool Registration Flow
        A[Client via REST] -- POST /api/register-openapi --> B(McpServerController);
        B -- registerOpenApi() --> C(McpServerService);
        C -- loadMcpServerConfiguration() --> D(McpConfigurationService);
        D -- loads mcp_server.yml --> E(YAML File);
        C -- convertOpenApiToMcpTools() --> F(OpenApiToMcpConverter);
        C -- initialize() --> G(RestApiExecutorService);
        C -- addTool() --> H(McpSyncServer);
    end

    subgraph Tool Execution Flow (REST API Call)
        I[AI Model] -- Calls Tool --> H;
        H -- Executes Handler --> C;
        C -- callTool() --> G;
        G -- Executes REST Call --> J(External REST API);
        J -- Returns Result --> G;
        G -- Returns Result --> C;
        C -- Returns Result --> H;
        H -- Returns Result --> I;
    end
```

## Explanation

### Tool Registration Flow

1.  A client sends a `POST` request to the `/api/register-openapi` endpoint of the `McpServerController`.
2.  The `McpServerController` calls the `registerOpenApi()` method of the `McpServerService`.
3.  The `McpServerService` calls the `loadMcpServerConfiguration()` method of the `McpConfigurationService` to load the `mcp_server.yml` file.
4.  The `McpServerService` then calls the `convertOpenApiToMcpTools()` method of the `OpenApiToMcpConverter` to convert the OpenAPI specification into an MCP tool.
5.  The `McpServerService` calls the `initialize()` method of the `RestApiExecutorService` to initialize the REST API.
6.  Finally, the `McpServerService` calls the `addTool()` method of the `McpSyncServer` to register the new tool.

### Tool Execution Flow

1.  An AI model calls a tool that is exposed by the MCP server.
2.  The `McpSyncServer` receives the tool call and executes the corresponding handler in the `McpServerService`.
3.  The `McpServerService`'s `callTool()` method is invoked, which in turn calls the `executeApiCall()` method of the `RestApiExecutorService`.
4.  The `RestApiExecutorService` executes the REST API call to the external service and returns the result to the `McpServerService`.
5.  The `McpServerService` returns the result to the `McpSyncServer`, which in turn returns the result to the AI model.
