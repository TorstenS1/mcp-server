import yaml
import urllib.request
import base64
from typing import List, Tuple, Dict, Any, Callable, get_type_hints
import inspect
from mcp.types import Tool as McpTool

from app.exceptions import InvalidOpenApiSpecException, ExternalApiException
from app.services.rest_api_executor_service import RestApiExecutorService

class OpenApiToMcpConverter:
    """
    Converts OpenAPI specifications into a list of internal Tool objects.
    Handles loading OpenAPI specs from URLs or BASE64 encoded strings, and resolves $ref references.
    """
    def __init__(self, api_executor: RestApiExecutorService):
        """
        Initializes the converter with a RestApiExecutorService instance.
        """
        self.openapi_spec = None # To store the full OpenAPI spec for $ref resolution
        self.api_executor = api_executor

    def convert_openapi_to_mcp_tools(self, source: str, source_type: str = "URL") -> List[Tuple[Callable[..., Any], str, str, Dict]]:
        """
        Converts an OpenAPI specification into a list of tuples containing dynamic functions and their metadata.

        Args:
            source (str): The source of the OpenAPI specification (URL or BASE64 encoded string).
            source_type (str): The type of the source ('URL' or 'BASE64'). Defaults to 'URL'.

        Returns:
            List[Tuple[Callable[..., Any], str, str, Dict]]: A list of tuples, each containing:
                - The dynamic function for the tool.
                - The name of the tool.
                - The description of the tool.
                - The input schema of the tool.

        Raises:
            InvalidOpenApiSpecException: If the OpenAPI spec cannot be loaded or is invalid.
        """
        try:
            if source_type == "URL":
                with urllib.request.urlopen(source) as response:
                    self.openapi_spec = yaml.safe_load(response.read().decode()) # Store the spec
            elif source_type == "BASE64":
                decoded_bytes = base64.b64decode(source)
                self.openapi_spec = yaml.safe_load(decoded_bytes.decode('utf-8'))
            else:
                raise InvalidOpenApiSpecException("Unsupported source type. Must be 'URL' or 'BASE64'.")
        except Exception as e:
            raise InvalidOpenApiSpecException(f"Failed to load OpenAPI spec from source: {e}") from e

        base_url = self.openapi_spec.get("servers", [{"url": ""}])[0]["url"]
        # Initialize the executor with the base_url and openapi_spec
        self.api_executor.initialize(base_url, openapi_spec=self.openapi_spec)

        mcp_tools_data = []

        for path, path_item in self.openapi_spec.get("paths", {}).items():
            for method, operation in path_item.items():
                if method in ["get", "post", "put", "delete", "patch"]:
                    tool_name, description, inputSchema, dynamic_function = self._extract_tool_data_from_operation(base_url, path, method, operation)
                    print(f"Extracted tool data: {tool_name}") # Debug print
                    mcp_tools_data.append((dynamic_function, tool_name, description, inputSchema))
        
        print(f"Total tools extracted: {len(mcp_tools_data)}") # Debug print

        return mcp_tools_data

    def _extract_tool_data_from_operation(self, base_url: str, path: str, method: str, operation: Dict) -> Tuple[str, str, Dict, Callable[..., Any]]:
        """
        Extracts tool data (name, description, inputSchema, dynamic_function) from a single OpenAPI operation.

        Args:
            base_url (str): The base URL of the API.
            path (str): The OpenAPI path for the operation.
            method (str): The HTTP method for the operation (e.g., 'get', 'post').
            operation (Dict): The OpenAPI operation object.

        Returns:
            Tuple[str, str, Dict, Callable[..., Any]]: A tuple containing:
                - The name of the tool.
                - The description of the tool.
                - The input schema of the tool.
                - The dynamic function for the tool.
        """
        operation_id = operation.get("operationId", f"{method}_{path.replace('/', '_').strip('_')}")
        description = operation.get("description", operation.get("summary", ""))
        inputSchema = self._create_inputSchema(operation)
        dynamic_function = self._create_dynamic_function_for_operation(base_url, operation_id, inputSchema)

        return operation_id, description, inputSchema, dynamic_function

    def _create_dynamic_function_for_operation(self, base_url: str, tool_name: str, inputSchema: Dict) -> Callable[..., Any]:
        """
        Creates a dynamic Python function that executes the REST API call for a given OpenAPI operation
        using the RestApiExecutorService, with a signature derived from the inputSchema.
        """
        api_executor = self.api_executor # Capture the executor instance

        # Create function parameters from inputSchema
        parameters = []
        for param_name, param_props in inputSchema.get("properties", {}).items():
            # Attempt to infer type from schema, default to Any
            param_type = Any
            if "type" in param_props:
                if param_props["type"] == "string":
                    param_type = str
                elif param_props["type"] == "integer":
                    param_type = int
                elif param_props["type"] == "number":
                    param_type = float
                elif param_props["type"] == "boolean":
                    param_type = bool
                elif param_props["type"] == "array":
                    param_type = List[Any] # Or more specific if items type is available
                elif param_props["type"] == "object":
                    param_type = Dict[str, Any]

            # Determine if parameter is required
            default_value = inspect.Parameter.empty
            if param_name not in inputSchema.get("required", []):
                default_value = None # Or a more appropriate default

            parameters.append(inspect.Parameter(
                param_name,
                inspect.Parameter.POSITIONAL_OR_KEYWORD,
                default=default_value,
                annotation=param_type
            ))

        # Create the dynamic function
        async def dynamic_api_call(**kwargs):
            try:
                result = await api_executor.execute_api_call(base_url, tool_name, kwargs)
                return result
            except ExternalApiException as e:
                raise Exception(f"API call failed: {e}") from e

        # Apply the new signature to the dynamic function
        dynamic_api_call.__signature__ = inspect.Signature(parameters)

        return dynamic_api_call

    def _create_inputSchema(self, operation: Dict) -> Dict:
        """
        Creates the input schema for a tool based on an OpenAPI operation.

        Args:
            operation (Dict): The OpenAPI operation object.

        Returns:
            Dict: The generated input schema.
        """
        schema = {"type": "object", "properties": {}, "required": []}
        properties = schema["properties"]
        required_params = schema["required"]

        # Add parameters (path, query, header, cookie)
        parameters = operation.get("parameters", [])
        for param in parameters:
            param_name = param["name"]
            param_schema = self._convert_json_schema_to_map(param.get("schema", {}))
            if "description" not in param_schema and "description" in param:
                param_schema["description"] = param["description"]

            properties[param_name] = param_schema
            if param.get("required", False):
                required_params.append(param_name)

        # Add request body parameters
        request_body = operation.get("requestBody", {})
        if request_body:
            content = request_body.get("content", {})
            json_content = content.get("application/json", {})
            if json_content:
                body_schema = json_content.get("schema", {})
                converted_body_schema = self._convert_json_schema_to_map(body_schema)

                if converted_body_schema.get("type") == "object":
                    for prop_name, prop_schema in converted_body_schema.get("properties", {}).items():
                        properties[prop_name] = prop_schema
                    if "required" in converted_body_schema:
                        required_params.extend(converted_body_schema["required"])
                else:
                    # If the body is not an object (e.g., a string or array directly)
                    # We'll put it under a generic 'body' key for now.
                    properties["body"] = converted_body_schema
                    if request_body.get("required", False):
                        required_params.append("body")

        return schema

    def _convert_json_schema_to_map(self, schema_node: Dict) -> Dict:
        """Recursively converts a JSON schema node into a Python dictionary,
        handling $ref and nested structures."""
        if not isinstance(schema_node, dict):
            return schema_node # Return as is if not a dictionary (e.g., a primitive value)

        if "$ref" in schema_node:
            ref_path = schema_node["$ref"]
            resolved_schema = self._resolve_ref(ref_path)
            # Merge any other properties from the original schema_node
            # with the resolved schema, giving precedence to original properties.
            return {**resolved_schema, **{k: v for k, v in schema_node.items() if k != "$ref"}}

        converted_schema = {}
        for key, value in schema_node.items():
            if key == "properties" and isinstance(value, dict):
                converted_properties = {}
                for prop_name, prop_schema in value.items():
                    converted_properties[prop_name] = self._convert_json_schema_to_map(prop_schema)
                converted_schema[key] = converted_properties
            elif key == "items" and isinstance(value, dict):
                converted_schema[key] = self._convert_json_schema_to_map(value)
            elif isinstance(value, dict):
                converted_schema[key] = self._convert_json_schema_to_map(value)
            elif isinstance(value, list):
                converted_schema[key] = [self._convert_json_schema_to_map(item) for item in value]
            else:
                converted_schema[key] = value
        return converted_schema

    def _resolve_ref(self, ref_path: str) -> Dict:
        """
        Resolves a JSON schema $ref path within the loaded OpenAPI spec.

        Args:
            ref_path (str): The $ref path (e.g., '#/components/schemas/MySchema').

        Returns:
            Dict: The resolved schema definition.
        """
        # Example ref_path: '#/components/schemas/Tool'
        parts = ref_path.split('/')
        current_node = self.openapi_spec

        for part in parts[1:]: # Skip '#'
            if isinstance(current_node, dict) and part in current_node:
                current_node = current_node[part]
            else:
                # Handle cases where the ref path is invalid or not found
                print(f"Warning: Could not resolve reference part '{part}' in '{ref_path}'")
                return {} # Return empty dict or raise an error