from fastapi import FastAPI, HTTPException, Request, status
from fastapi.responses import JSONResponse
from typing import Dict, Any, List
import asyncio
from starlette.routing import Mount
from app.services.mcp_server_service import McpServerService
from app.models import OpenApiRegistrationRequest, UpdateToolDescriptionRequest
from app.exceptions import (
    ToolNotFoundException,
    ConfigurationLoadingException,
    ToolRegistrationException,
    ExternalApiException,
    InvalidOpenApiSpecException
)

app = FastAPI(
    title="MCP Server",
    description="This is the Multi-Capability Platform (MCP) Server. It manages and exposes external tools via a unified API.",
    version="1.0.0",
)
mcp_server_service = McpServerService()

from fastapi import FastAPI, HTTPException, Request, status
from fastapi.responses import JSONResponse
from typing import Dict, Any, List
import asyncio
from starlette.routing import Mount
from app.services.mcp_server_service import McpServerService
from app.services.mcp_configuration_service import McpConfigurationService
from app.models import McpServerConfig, OpenApiRegistrationRequest, UpdateToolDescriptionRequest
from app.exceptions import (
    ToolNotFoundException,
    ConfigurationLoadingException,
    ToolRegistrationException,
    ExternalApiException,
    InvalidOpenApiSpecException
)
from mcp.server.sse import SseServerTransport
from mcp.server.streamable_http import StreamableHTTPServerTransport
from starlette.responses import Response
import mcp.types as types
import uvicorn

app = FastAPI(
    title="MCP Server",
    description="This is the Multi-Capability Platform (MCP) Server. It manages and exposes external tools via a unified API.",
    version="1.0.0",
)
config_service = McpConfigurationService()
mcp_server_config = config_service.load_mcp_server_configuration()
mcp_server_service = McpServerService()

@app.on_event("startup")
async def startup_event():
    mcp_server_service.init_tools()

    @mcp_server_service.mcp_server.list_tools()
    async def list_all_tools() -> list[types.Tool]:
        # This function will return the tools that were loaded from the config
        # or registered via OpenAPI.
        # We need to convert our internal Tool model to mcp.types.Tool
        mcp_tools = []
        for tool_name, tool_data in mcp_server_service.tools.items():
            mcp_tools.append(types.Tool(
                name=tool_name,
                description=tool_data.description,
                inputSchema=tool_data.input_schema,
            ))

        print(f"Returning {len(mcp_tools)} tools from list_all_tools.")
        print(f"Registered tools: {', '.join(tool.name for tool in mcp_tools)}")
        print(f"Registered tools: {', '.join(tool.description for tool in mcp_tools)}")
        print(f"Registered tools: {', '.join(tool.inputSchema for tool in mcp_tools)}")

        return mcp_tools

if mcp_server_config.transport_type == "sse":
    transport = SseServerTransport()
elif mcp_server_config.transport_type == "streamable-http":
    transport = StreamableHttpServerTransport()

    @app.post("/mcp")
    async def streamable_http_endpoint(request: Request):
        async with transport.connect_streamable_http(
            request.scope, request.receive, request._send
        ) as (read_stream, write_stream):
            await mcp_server_service.mcp_server.run(
                read_stream,
                write_stream,
                mcp_server_service.mcp_server.create_initialization_options(stateless=True)
            )
        return Response()

else:
    raise ValueError(f"Unsupported transport type: {mcp_server_config.transport_type}")



if __name__ == "__main__":
    uvicorn.run(app, host=mcp_server_config.host, port=mcp_server_config.port)





@app.exception_handler(ToolNotFoundException)
async def tool_not_found_exception_handler(request: Request, exc: ToolNotFoundException):
    """
    Handles ToolNotFoundException and returns a 404 Not Found response.
    """
    return JSONResponse(
        status_code=status.HTTP_404_NOT_FOUND,
        content={"message": str(exc)},
    )

@app.exception_handler(ConfigurationLoadingException)
async def configuration_loading_exception_handler(request: Request, exc: ConfigurationLoadingException):
    """
    Handles ConfigurationLoadingException and returns a 500 Internal Server Error response.
    """
    return JSONResponse(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content={"message": str(exc)},
    )

@app.exception_handler(ToolRegistrationException)
async def tool_registration_exception_handler(request: Request, exc: ToolRegistrationException):
    """
    Handles ToolRegistrationException and returns a 400 Bad Request response.
    """
    return JSONResponse(
        status_code=status.HTTP_400_BAD_REQUEST,
        content={"message": str(exc)},
    )

@app.exception_handler(ExternalApiException)
async def external_api_exception_handler(request: Request, exc: ExternalApiException):
    """
    Handles ExternalApiException and returns a 500 Internal Server Error response.
    """
    return JSONResponse(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content={"message": str(exc)},
    )

@app.exception_handler(InvalidOpenApiSpecException)
async def invalid_openapi_spec_exception_handler(request: Request, exc: InvalidOpenApiSpecException):
    """
    Handles InvalidOpenApiSpecException and returns a 400 Bad Request response.
    """
    return JSONResponse(
        status_code=status.HTTP_400_BAD_REQUEST,
        content={"message": str(exc)},
    )

@app.exception_handler(Exception)
async def generic_exception_handler(request: Request, exc: Exception):
    """
    Handles any other unhandled exceptions and returns a 500 Internal Server Error response.
    """
    return JSONResponse(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content={"message": "An unexpected error occurred. Please try again later."},
    )

@app.get("/api/tools", response_model=List[Dict[str, Any]], summary="Get all registered tools")
async def get_tools():
    """
    Retrieves a list of all registered tools.
    """
    return [tool.model_dump() for tool in mcp_server_service.get_tools()]

@app.get("/api/tools/{id}", response_model=Dict[str, Any], summary="Get a specific tool by ID")
async def get_tool(id: str):
    """
    Retrieves details of a specific tool by its ID.

    Args:
        id (str): The ID of the tool to retrieve.

    Raises:
        ToolNotFoundException: If the tool with the given ID is not found.
    """
    tool = mcp_server_service.get_tool(id)
    if tool:
        return tool.model_dump()
    else:
        raise ToolNotFoundException(f"Tool '{id}' not found")

@app.delete("/api/tools/{id}", summary="Delete a tool by ID")
async def delete_tool(id: str):
    """
    Deletes a tool by its ID.

    Args:
        id (str): The ID of the tool to delete.

    Raises:
        ToolNotFoundException: If the tool with the given ID is not found.
        ToolRegistrationException: If there's an error during tool deletion.
    """
    try:
        mcp_server_service.delete_tool(id)
        return {"message": f"Tool {id} deleted successfully"}
    except ToolNotFoundException as e:
        raise ToolNotFoundException(str(e))
    except Exception as e:
        raise ToolRegistrationException(f"Failed to delete tool: {e}")

@app.post("/api/register-openapi", summary="Register a new tool from an OpenAPI specification")
async def register_openapi(request: OpenApiRegistrationRequest):
    """
    Registers a new tool using an OpenAPI specification provided via URL or BASE64.

    Args:
        request (OpenApiRegistrationRequest): The request body containing the OpenAPI source and type.

    Raises:
        ToolRegistrationException: If a tool with the same name already exists or if there's an error during registration.
        InvalidOpenApiSpecException: If the provided OpenAPI specification is invalid.
    """
    try:
        mcp_server_service.register_openapi(request)
        return {"message": "Tool registered successfully"}
    except ValueError as e: # Catch ValueError for duplicate tool names
        raise ToolRegistrationException(str(e))
    except Exception as e:
        raise ToolRegistrationException(f"Failed to register tool: {e}")

@app.post("/api/update-tool-description", summary="Update the description of an existing tool")
async def update_tool_description(request: UpdateToolDescriptionRequest):
    """
    Updates the description of an existing tool.

    Args:
        request (UpdateToolDescriptionRequest): The request body containing the tool name and new description.

    Raises:
        ToolNotFoundException: If the tool with the given name is not found.
        ToolRegistrationException: If there's an error during the update.
    """
    try:
        mcp_server_service.update_tool_description(request.tool_name, request.new_description)
        return {"message": "Tool description updated successfully"}
    except ValueError as e:
        raise ToolNotFoundException(str(e))
    except Exception as e:
        raise ToolRegistrationException(f"Failed to update tool description: {e}")

@app.post("/api/tools/{tool_id}/execute", summary="Execute a tool")
async def execute_tool(tool_id: str, arguments: Dict[str, Any]):
    """
    Execute a registered tool with the given arguments.
    """
    try:
        # The low-level server does not expose a direct execute_tool method.
        # Instead, the client sends a CallToolRequest to the SSE endpoint.
        # For the REST API, we'll simulate this by directly calling the dynamic function
        # associated with the tool.
        tool = mcp_server_service.get_tool(tool_id)
        if not tool:
            raise ToolNotFoundException(f"Tool '{tool_id}' not found")
        
        # Find the registered handler for this tool
        # This is a bit of a hack, as the low-level server doesn't expose handlers directly
        # We'll assume the handler is registered and can be called.
        # In a real scenario, you might want to refactor how tools are stored/accessed.
        # For now, we'll rely on the fact that the `call_tool` decorator registers a handler.
        # We need to find the specific handler for this tool_id.
        # This is a simplified approach and might need more robust handling.
        
        # The dynamic function is not directly accessible from mcp_server_service.tools
        # as it's wrapped by the @mcp_server.call_tool() decorator.
        # We need to find a way to invoke the underlying dynamic function.
        # For now, we'll just raise an error, as direct execution via REST API is not the primary MCP way.
        raise NotImplementedError("Direct tool execution via REST API is not supported in the low-level MCP server. Use SSE.")

    except ToolNotFoundException as e:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e))
    except ExternalApiException as e:
        raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=f"An unexpected error occurred during tool execution: {e}")