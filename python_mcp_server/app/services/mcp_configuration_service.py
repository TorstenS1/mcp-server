import yaml
from app.models import McpServerConfig, McpToolConfig
from typing import Dict, Any
from app.exceptions import ConfigurationLoadingException, ToolNotFoundException

class McpConfigurationService:
    """
    Service for loading and managing the MCP server configuration from a YAML file.
    """
    def __init__(self, config_path="mcp_server.yml"):
        """
        Initializes the McpConfigurationService.

        Args:
            config_path (str): The path to the MCP server configuration YAML file.
        """
        self.config_path = config_path

    def load_mcp_server_configuration(self) -> McpServerConfig:
        """
        Loads the MCP server configuration from the specified YAML file.

        Returns:
            McpServerConfig: An object representing the loaded configuration.

        Raises:
            ConfigurationLoadingException: If the file is not found, cannot be parsed, or any other error occurs during loading.
        """
        try:
            with open(self.config_path, 'r') as f:
                config = yaml.safe_load(f)
            return McpServerConfig(**config['mcp_server'])
        except FileNotFoundError as e:
            raise ConfigurationLoadingException(f"Configuration file not found: {self.config_path}") from e
        except yaml.YAMLError as e:
            raise ConfigurationLoadingException(f"Error parsing configuration file: {self.config_path}") from e
        except Exception as e:
            raise ConfigurationLoadingException(f"Unexpected error loading configuration: {e}") from e

    def update_tool_description_in_config(self, tool_name: str, new_description: str):
        """
        Updates the description of a specific tool within the configuration file.

        Args:
            tool_name (str): The name of the tool to update.
            new_description (str): The new description for the tool.

        Raises:
            ToolNotFoundException: If the tool is not found in the configuration file.
            ConfigurationLoadingException: If there's an error reading or writing the configuration file.
        """
        print(f"Attempting to update tool: {tool_name} with new description: {new_description}")
        try:
            with open(self.config_path, 'r') as f:
                config = yaml.safe_load(f)

            mcp_server_data = config.get('mcp_server', {})
            tools = mcp_server_data.get('tools', [])

            updated = False
            for tool in tools:
                print(f"Checking tool in config: {tool.get('name')}")
                if tool.get('name') == tool_name:
                    tool['description'] = new_description
                    updated = True
                    break

            if not updated:
                raise ToolNotFoundException(f"Tool {tool_name} not found in configuration file.")

            with open(self.config_path, 'w') as f:
                yaml.safe_dump(config, f)
        except FileNotFoundError as e:
            raise ConfigurationLoadingException(f"Configuration file not found: {self.config_path}") from e
        except yaml.YAMLError as e:
            raise ConfigurationLoadingException(f"Error parsing configuration file: {self.config_path}") from e
        except Exception as e:
            raise ConfigurationLoadingException(f"Unexpected error updating configuration: {e}") from e