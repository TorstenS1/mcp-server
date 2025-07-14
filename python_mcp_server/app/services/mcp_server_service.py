from typing import List, Dict, Callable, Any
from mcp.server.fastmcp import FastMCP
from mcp.server.fastmcp.tools.base import Tool as McpTool
from app.models import Tool, OpenApiRegistrationRequest
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
        self.mcp_server = FastMCP("McpServer")
        self.tools: Dict[str, Tool] = {} # Store our internal Tool model for API exposure
        self.config_tool_names: Dict[str, str] = {} # Maps generated tool name to config name

    def init_tools(self):
        """
        Initializes tools from the `mcp_server.yml` configuration file.
        This method clears existing tools and reloads them based on the configuration.
        """
        self.mcp_server = FastMCP("McpServer") # Re-initialize FastMCP for a clean state
        self.tools.clear()
        self.config_tool_names.clear()

        config = self.config_service.load_mcp_server_configuration()
        for tool_def in config.tools:
            try:
                mcp_tools_data = self.converter.convert_openapi_to_mcp_tools(tool_def.rest_api_url, "URL")
                for dynamic_function, tool_name, description, input_schema in mcp_tools_data:
                    if tool_name in self.tools:
                        print(f"Warning: Tool '{tool_name}' from configuration already exists. Skipping.")
                        continue
                    self.mcp_server.add_tool(
                        fn=dynamic_function,
                        name=tool_name,
                        description=description,
                    )
                    self.tools[tool_name] = Tool(
                        name=tool_name,
                        description=description,
                        input_schema=input_schema
                    )
                    self.config_tool_names[tool_name] = tool_def.name
            except Exception as e:
                print(f"Error initializing tool '{tool_def.name}' from config: {e}")

    def get_tools(self) -> List[Tool]:
        """
        Retrieves a list of all currently registered tools.

        Returns:
            List[Tool]: A list of Tool objects.
        """
        return [tool.model_dump() for tool in self.tools.values()]

    def get_tool(self, tool_id: str) -> Tool:
        """
        Retrieves a specific tool by its ID.

        Args:
            tool_id (str): The ID of the tool to retrieve.

        Returns:
            Tool: The Tool object if found, otherwise None.
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
            for dynamic_function, tool_name, description, input_schema in mcp_tools_data:
                if tool_name in self.tools:
                    raise ToolRegistrationException(f"Tool with name '{tool_name}' already exists.")
                self.mcp_server.add_tool(
                    fn=dynamic_function,
                    name=tool_name,
                    description=description,
                )
                self.tools[tool_name] = Tool(
                    name=tool_name,
                    description=description,
                    input_schema=input_schema
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

    async def execute_tool(self, tool_id: str, arguments: Dict[str, Any]) -> Any:
        """
        Executes a registered tool with the given arguments using the FastMCP server.
        """
        if tool_id not in self.mcp_server._tools:
            raise ToolNotFoundException(f"Tool '{tool_id}' not found in MCP server.")
        
        mcp_tool = self.mcp_server._tools[tool_id]
        
        try:
            # The function associated with the McpTool is directly callable
            result = await mcp_tool.function(**arguments)
            return result
        except Exception as e:
            raise ExternalApiException(f"Error executing tool '{tool_id}': {e}") from e

    def cleanup(self):
        pass