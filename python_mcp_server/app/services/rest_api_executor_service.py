import json
import requests
from typing import Dict, Any, Optional, Tuple
import yaml
from app.exceptions import ExternalApiException

class RestApiExecutorService:
    """
    Service responsible for executing API calls to external REST APIs based on their OpenAPI specifications.
    """
    def __init__(self):
        """
        Initializes the RestApiExecutorService.
        """
        self.openapi_specs: Dict[str, Any] = {}

    def initialize(self, base_url: str, openapi_spec_url: Optional[str] = None, openapi_spec: Optional[Dict] = None):
        """
        Initializes the service with an OpenAPI specification for a given base URL.

        Args:
            base_url (str): The base URL of the API.
            openapi_spec_url (Optional[str]): The URL to the OpenAPI specification. Required if openapi_spec is not provided.
            openapi_spec (Optional[Dict]): The OpenAPI specification as a dictionary. Required if openapi_spec_url is not provided.

        Raises:
            ValueError: If neither openapi_spec_url nor openapi_spec is provided.
            ExternalApiException: If there's an error fetching the OpenAPI spec from the URL.
        """
        if openapi_spec_url:
            try:
                response = requests.get(openapi_spec_url)
                response.raise_for_status()
                self.openapi_specs[base_url] = yaml.safe_load(response.text)
            except requests.exceptions.RequestException as e:
                raise ExternalApiException(f"Failed to fetch OpenAPI spec from {openapi_spec_url}: {e}") from e
            except Exception as e:
                raise ExternalApiException(f"Unexpected error fetching OpenAPI spec: {e}") from e
        elif openapi_spec:
            self.openapi_specs[base_url] = openapi_spec
        else:
            raise ValueError("Either openapi_spec_url or openapi_spec must be provided.")

    def execute_api_call(self, base_url: str, tool_name: str, arguments: Dict) -> Dict:
        """
        Executes an API call to an external service.

        Args:
            base_url (str): The base URL of the API to call.
            tool_name (str): The name of the tool (operationId) to execute.
            arguments (Dict): A dictionary of arguments for the API call.

        Returns:
            Dict: The JSON response from the API call.

        Raises:
            ExternalApiException: If the OpenAPI spec is not found, the operation is not found,
                                  or if there's an error during the API call.
        """
        openapi_spec = self.openapi_specs.get(base_url)
        if not openapi_spec:
            raise ExternalApiException(f"OpenAPI spec not found for base URL: {base_url}")

        # Find the operation in the OpenAPI spec based on tool_name
        path, method, operation = self._find_operation_by_tool_name(openapi_spec, tool_name)
        if not path or not method or not operation:
            raise ExternalApiException(f"Operation not found for tool: {tool_name}")

        url = f"{base_url}{path}"
        
        # Prepare parameters, headers, and request body
        processed_url, query_params, headers = self._process_parameters(url, operation, arguments)
        request_body, content_type = self._process_request_body(operation, arguments)

        if content_type:
            headers["Content-Type"] = content_type

        # TODO: Implement authentication headers based on OpenAPI security schemes
        # For example, if API Key in header:
        # if "apiKey" in openapi_spec.get("securitySchemes", {}):
        #     headers["X-API-Key"] = "YOUR_API_KEY"

        try:
            if method.lower() == "get":
                response = requests.get(processed_url, params=query_params, headers=headers)
            elif method.lower() == "post":
                response = requests.post(processed_url, params=query_params, json=request_body if content_type == "application/json" else None, data=request_body if content_type != "application/json" else None, headers=headers)
            elif method.lower() == "put":
                response = requests.put(processed_url, params=query_params, json=request_body if content_type == "application/json" else None, data=request_body if content_type != "application/json" else None, headers=headers)
            elif method.lower() == "delete":
                response = requests.delete(processed_url, params=query_params, headers=headers)
            elif method.lower() == "patch":
                response = requests.patch(processed_url, params=query_params, json=request_body if content_type == "application/json" else None, data=request_body if content_type != "application/json" else None, headers=headers)
            else:
                raise ValueError(f"Unsupported HTTP method: {method}")

            response.raise_for_status() # Raise an HTTPError for bad responses (4xx or 5xx)
            return response.json()
        except requests.exceptions.RequestException as e:
            raise ExternalApiException(f"API call failed: {e}") from e
        except Exception as e:
            raise ExternalApiException(f"An unexpected error occurred during API call: {e}") from e

    def _find_operation_by_tool_name(self, openapi_spec: Dict, tool_name: str) -> Tuple[Optional[str], Optional[str], Optional[Dict]]:
        """
        Finds an OpenAPI operation by its tool name (operationId).

        Args:
            openapi_spec (Dict): The OpenAPI specification.
            tool_name (str): The operationId of the tool.

        Returns:
            Tuple[str, str, Dict]: A tuple containing the path, method, and operation object if found, otherwise (None, None, None).
        """
        for path, path_item in openapi_spec.get("paths", {}).items():
            for method, operation in path_item.items():
                operation_id = operation.get("operationId", f"{method}_{path.replace('/', '_').strip('_')}")
                if operation_id == tool_name:
                    return path, method, operation
        return None, None, None

    def _process_parameters(self, url: str, operation: Dict, arguments: Dict) -> Tuple[str, Dict, Dict]:
        """
        Processes path, query, and header parameters from arguments.

        Args:
            url (str): The base URL.
            operation (Dict): The OpenAPI operation object.
            arguments (Dict): The arguments for the API call.

        Returns:
            Tuple[str, Dict, Dict]: The processed URL, query parameters, and headers.
        """
        query_params = {}
        headers = {}

        for param in operation.get("parameters", []):
            param_name = param["name"]
            param_in = param["in"]

            if param_name in arguments:
                value = arguments[param_name]
                if param_in == "path":
                    url = url.replace(f"{{{param_name}}}", str(value))
                elif param_in == "query":
                    query_params[param_name] = value
                elif param_in == "header":
                    headers[param_name] = str(value)
                # TODO: Handle 'cookie' parameters if needed
        return url, query_params, headers

    def _process_request_body(self, operation: Dict, arguments: Dict) -> Tuple[Any, Optional[str]]:
        """
        Processes the request body based on the OpenAPI specification.

        Args:
            operation (Dict): The OpenAPI operation object.
            arguments (Dict): The arguments for the API call.

        Returns:
            Tuple[Any, Optional[str]]: The request body and its content type.
        """
        request_body_spec = operation.get("requestBody", {})
        content = request_body_spec.get("content", {})

        if "application/json" in content:
            schema = content["application/json"].get("schema", {})
            body = {}
            if schema.get("type") == "object":
                for prop_name in schema.get("properties", {}).keys():
                    if prop_name in arguments:
                        body[prop_name] = arguments[prop_name]
            elif "body" in arguments: # For non-object bodies (e.g., string, array directly passed as 'body')
                body = arguments["body"]
            return body, "application/json"
        elif "application/x-www-form-urlencoded" in content:
            # For form data, arguments are directly the form fields
            body = {}
            schema = content["application/x-www-form-urlencoded"].get("schema", {})
            if schema.get("type") == "object":
                for prop_name in schema.get("properties", {}).keys():
                    if prop_name in arguments:
                        body[prop_name] = arguments[prop_name]
            return body, "application/x-www-form-urlencoded"
        # TODO: Handle other content types like multipart/form-data, text/plain, etc.
        
        return None, None