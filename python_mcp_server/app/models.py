from pydantic import BaseModel, Field
from typing import List, Dict, Any, Optional

class Tool(BaseModel):
    """
    Represents a tool that can be managed and executed by the MCP Server.
    """
    name: str = Field(..., description="The unique name of the tool (e.g., operationId from OpenAPI).")
    description: str = Field(..., description="A brief description of what the tool does.")
    input_schema: Dict[str, Any] = Field(..., description="JSON schema defining the expected input for the tool.")

class OpenApiRegistrationRequest(BaseModel):
    """
    Request model for registering a new tool from an OpenAPI specification.
    """
    source: str = Field(..., description="The source of the OpenAPI specification (URL or Base64 encoded string).")
    type: str = Field(..., description="The type of the source ('URL' or 'BASE64').")
    friendly_name: Optional[str] = Field(None, description="An optional friendly name for the tool, used for persistence.")

class UpdateToolDescriptionRequest(BaseModel):
    """
    Request model for updating the description of an existing tool.
    """
    tool_name: str = Field(..., description="The name of the tool whose description is to be updated.")
    new_description: str = Field(..., description="The new description for the tool.")

class McpToolConfig(BaseModel):
    """
    Represents a single tool configuration entry in mcp_server.yml.
    """
    name: str = Field(..., description="The name of the tool as defined in the configuration.")
    rest_api_url: str = Field(..., description="The URL to the OpenAPI specification for this tool.")

class McpServerConfig(BaseModel):
    """
    Represents the overall MCP server configuration from mcp_server.yml.
    """
    tools: List[McpToolConfig] = Field(default_factory=list, description="A list of tools to be loaded by the MCP server.")
