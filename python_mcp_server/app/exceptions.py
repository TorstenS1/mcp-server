class ToolNotFoundException(Exception):
    """
    Exception raised when a requested tool is not found.
    """
    pass

class ConfigurationLoadingException(Exception):
    """
    Exception raised when there is an error loading the MCP server configuration.
    """
    pass

class ToolRegistrationException(Exception):
    """
    Exception raised when there is an error during tool registration or unregistration.
    """
    pass

class ExternalApiException(Exception):
    """
    Exception raised when there is an error interacting with an external API.
    """
    pass

class InvalidOpenApiSpecException(Exception):
    """
    Exception raised when the provided OpenAPI specification is invalid or cannot be processed.
    """
    pass
