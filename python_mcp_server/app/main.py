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

@app.on_event("startup")
async def startup_event():
    """
    Initializes tools from the configuration file when the FastAPI application starts up.
    """
    mcp_server_service.init_tools()

@app.on_event("shutdown")
async def shutdown_event():
    """
    Handles application shutdown events.
    """
    mcp_server_service.cleanup()

# Mount the FastMCP SSE application
app.mount("/mcp-sse", mcp_server_service.mcp_server.sse_app())

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
    return mcp_server_service.get_tools()

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
        result = await mcp_server_service.execute_tool(tool_id, arguments)
        return {"result": result}
    except ToolNotFoundException as e:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e))
    except ExternalApiException as e:
        raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=f"An unexpected error occurred during tool execution: {e}")