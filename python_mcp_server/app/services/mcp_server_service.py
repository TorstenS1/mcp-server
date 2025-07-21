from typing import List, Dict, Callable, Any
from mcp.server.lowlevel.server import Server
from mcp.server.lowlevel.server import NotificationOptions
from mcp.server.lowlevel.server import ServerSession
from mcp.server.lowlevel.server import InitializationOptions
from mcp.server.lowlevel.server import RequestContext
from mcp.types import ServerResult
from mcp.types import EmptyResult, ErrorData, ListPromptsResult, ListResourcesResult, ListResourceTemplatesResult, ReadResourceResult, TextResourceContents, BlobResourceContents, CallToolResult, ListToolsResult, CompleteResult, Completion


import mcp.types as types
import asyncio
import json
import base64
import warnings

from typing import List, Dict, Callable, Any


from app.models import OpenApiRegistrationRequest
from app.services.mcp_configuration_service import McpConfigurationService
from app.services.rest_api_executor_service import RestApiExecutorService
from app.services.openapi_to_mcp_converter import OpenApiToMcpConverter
from app.exceptions import ToolNotFoundException, ToolRegistrationException, ExternalApiException, InvalidOpenApiSpecException

class McpServerService:
    """
    Manages the lifecycle of tools within the MCP Server, including initialization,
    registration, retrieval, and deletion.
    """
    def __init__(self):
        """
        Initializes the McpServerService with necessary dependencies.
        """
        self.config_service = McpConfigurationService()
        self.api_executor = RestApiExecutorService()
        self.converter = OpenApiToMcpConverter(self.api_executor)
        self.mcp_server = Server("McpServer")
        self.tools: Dict[str, types.Tool] = {}
        self.config_tool_names: Dict[str, str] = {} # Maps generated tool name to config name

    def init_tools(self):
        """
        Initializes tools from the `mcp_server.yml` configuration file.
        This method clears existing tools and reloads them based on the configuration.
        """
        self.mcp_server = Server("McpServer") # Re-initialize Server for a clean state
        self.tools.clear()
        self.config_tool_names.clear()

        config = self.config_service.load_mcp_server_configuration()
        for tool_def in config.tools:
            try:
                mcp_tools_data = self.converter.convert_openapi_to_mcp_tools(tool_def.rest_api_url, "URL")
                for dynamic_function, tool_name, description, inputSchema in mcp_tools_data:
                    if tool_name in self.tools:
                        print(f"Warning: Tool '{tool_name}' from configuration already exists. Skipping.")
                        continue
                    # Instead of directly adding, we'll use the decorator pattern
                    # The actual tool registration will happen when the server runs and discovers the decorated functions.
                    # For now, we just need to ensure our internal `tools` dictionary is correct.
                    # The low-level server expects a `list_tools` handler to be registered.
                    # We will implement this in `app/main.py` when we set up the server.
                    @self.mcp_server.call_tool()
                    async def _tool_handler(name: str, arguments: Dict[str, Any]) -> Any:
                        if name == tool_name:
                            return await dynamic_function(**arguments)
                        raise ToolNotFoundException(f"Tool '{name}' not found.")
                    self.tools[tool_name] = types.Tool(
                        name=tool_name,
                        description=description,
                        inputSchema=inputSchema
                    )
                    print(f"Initialized tool: {tool_name} with description: {description}")
                    self.config_tool_names[tool_name] = tool_def.name
            except Exception as e:
                print(f"Error initializing tool '{tool_def.name}' from config: {e}")

    def get_tools(self) -> List[types.Tool]:
        """
        Retrieves a list of all currently registered tools.

        Returns:
            List[types.Tool]: A list of Tool objects.
        """
        print(f"Retrieving {len(self.tools)} registered tools.")
        print(f"Registered tools: {list(self.tools.keys())}")
        if not self.tools:
            print("No tools registered.")
        else:
            for tool_name, tool in self.tools.items():
                print(f"Tool: {tool_name}, Description: {tool.description}")
        # Return a list of Tool objects
        return list(self.tools.values())

    def get_tool(self, tool_id: str) -> types.Tool:
        """
        Retrieves a specific tool by its ID.

        Args:
            tool_id (str): The ID of the tool to retrieve.

        Returns:
            types.Tool: The Tool object if found, otherwise None.
        """
        return self.tools.get(tool_id)

    def delete_tool(self, tool_id: str):
        """
        Deletes a tool from the in-memory list of registered tools.

        Args:
            tool_id (str): The ID of the tool to delete.

        Raises:
            ToolNotFoundException: If the tool with the given ID is not found.
        """
        if tool_id not in self.tools:
            raise ToolNotFoundException(f"Tool '{tool_id}' not found.")
        
        del self.tools[tool_id]
        
        if tool_id in self.config_tool_names:
            del self.config_tool_names[tool_id]

    def register_openapi(self, request: OpenApiRegistrationRequest):
        """
        Registers a new tool based on an OpenAPI specification.

        Args:
            request (OpenApiRegistrationRequest): The request containing the OpenAPI source and type.

        Raises:
            ToolRegistrationException: If a tool with the same name already exists or if there's an error during registration.
            InvalidOpenApiSpecException: If the provided OpenAPI specification is invalid.
        """
        try:
            mcp_tools_data = self.converter.convert_openapi_to_mcp_tools(request.source, request.type)
            for dynamic_function, tool_name, description, inputSchema in mcp_tools_data:
                if tool_name in self.tools:
                    raise ToolRegistrationException(f"Tool with name '{tool_name}' already exists.")
                # Instead of directly adding, we'll use the decorator pattern
                # The actual tool registration will happen when the server runs and discovers the decorated functions.
                # For now, we just need to ensure our internal `tools` dictionary is correct.
                @self.mcp_server.call_tool()
                async def _tool_handler(name: str, arguments: Dict[str, Any]) -> Any:
                    if name == tool_name:
                        return await dynamic_function(**arguments)
                    raise ToolNotFoundException(f"Tool '{name}' not found.")
                self.tools[tool_name] = types.Tool(
                    name=tool_name,
                    description=description,
                    inputSchema=inputSchema
                )
                self.config_tool_names[tool_name] = request.friendly_name if request.friendly_name else tool_name
        except InvalidOpenApiSpecException as e:
            raise e
        except Exception as e:
            raise ToolRegistrationException(f"Failed to register OpenAPI tool: {e}") from e

    def update_tool_description(self, tool_name: str, new_description: str):
        """
        Updates the description of a tool and persists the change to the configuration file.

        Args:
            tool_name (str): The name of the tool to update.
            new_description (str): The new description for the tool.

        Raises:
            ToolNotFoundException: If the tool is not found.
            ToolRegistrationException: If there's an error persisting the update.
        """
        if tool_name not in self.tools:
            raise ToolNotFoundException(f"Tool '{tool_name}' not found.")
        
        self.tools[tool_name].description = new_description
        
        # FastMCP does not provide a direct way to update tool descriptions dynamically
        # without re-registering the tool or restarting the server. 
        # The description is updated in our internal 'tools' dictionary and persisted to config.

        # Use the mapped config name for persistence
        config_name = self.config_tool_names.get(tool_name, tool_name)
        try:
            self.config_service.update_tool_description_in_config(config_name, new_description)
        except ToolNotFoundException as e:
            print(f"Warning: Tool '{tool_name}' not found in mcp_server.yml for persistence. {e}")
        except Exception as e:
            raise ToolRegistrationException(f"Failed to persist tool description update: {e}") from e

    

    def cleanup(self):
        pass