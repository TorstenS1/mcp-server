import pytest
from fastapi.testclient import TestClient
from app.main import app, mcp_server_service # Import mcp_server_service
from app.models import OpenApiRegistrationRequest, UpdateToolDescriptionRequest
import base64
import yaml

client = TestClient(app)

# Define the expected tool name based on FastAPI's default operationId generation
EXPECTED_TOOL_NAME = "get_user_by_id_api_v1_users__userId__get"

@pytest.fixture(autouse=True)
def reset_mcp_server_service():
    # This fixture will run before each test
    mcp_server_service.init_tools() # Re-initialize the service for a clean state
    yield

def test_get_tools():
    response = client.get("/api/tools")
    assert response.status_code == 200
    assert isinstance(response.json(), list)
    # No hardcoded ping tool anymore

def test_register_openapi_url():
    # This test now verifies that the tool from mcp_server.yml is loaded on startup
    tools_response = client.get("/api/tools")
    assert tools_response.status_code == 200
    tools = tools_response.json()
    assert any(tool["name"] == EXPECTED_TOOL_NAME for tool in tools)

def test_register_openapi_base64():
    # A minimal OpenAPI spec for testing
    openapi_spec_content = """
openapi: 3.0.0
info:
  title: Test API
  version: 1.0.0
servers:
  - url: http://localhost:8002
paths:
  /test:
    get:
      operationId: get_test_data
      summary: Get test data
      responses:
        '200':
          description: Successful response
"""
    base64_encoded_spec = base64.b64encode(openapi_spec_content.encode('utf-8')).decode('utf-8')

    request_data = {
        "type": "BASE64",
        "source": base64_encoded_spec,
        "friendly_name": "Test API",
        "description": "API for testing base64 registration"
    }
    response = client.post("/api/register-openapi", json=request_data)
    assert response.status_code == 200
    assert response.json() == {"message": "Tool registered successfully"}

    # Verify the tool is registered
    tools_response = client.get("/api/tools")
    assert tools_response.status_code == 200
    tools = tools_response.json()
    assert any(tool["name"] == "get_test_data" for tool in tools)

def test_register_duplicate_tool():
    # Register a unique tool first
    unique_openapi_spec_content = """
openapi: 3.0.0
info:
  title: Unique Test API
  version: 1.0.0
servers:
  - url: http://localhost:8003
paths:
  /unique_test:
    get:
      operationId: get_unique_test_data
      summary: Get unique test data
      responses:
        '200':
          description: Successful response
"""
    unique_base64_encoded_spec = base64.b64encode(unique_openapi_spec_content.encode('utf-8')).decode('utf-8')

    unique_request_data = {
        "type": "BASE64",
        "source": unique_base64_encoded_spec,
        "friendly_name": "Unique Test API",
        "description": "API for testing unique registration"
    }
    response = client.post("/api/register-openapi", json=unique_request_data)
    assert response.status_code == 200

    # Attempt to register the same unique tool again
    response = client.post("/api/register-openapi", json=unique_request_data)
    assert response.status_code == 400 # Expecting a bad request due to duplicate
    assert "already exists" in response.json()["message"]

def test_update_tool_description():
    # The tool is already registered by the fixture
    request_data = {
        "tool_name": EXPECTED_TOOL_NAME,
        "new_description": "Updated description for user API"
    }
    response = client.post("/api/update-tool-description", json=request_data)
    assert response.status_code == 200
    assert response.json() == {"message": "Tool description updated successfully"}

    # Verify the description is updated in memory
    tool_response = client.get(f"/api/tools/{EXPECTED_TOOL_NAME}")
    assert tool_response.status_code == 200
    assert tool_response.json()["description"] == "Updated description for user API"

    # Verify the description is updated in the config file
    with open("mcp_server.yml", 'r') as f: # Corrected path
        config = yaml.safe_load(f)
    
    found_in_config = False
    for tool_config in config['mcp_server']['tools']:
        if tool_config['name'] == "user-api": # The name in mcp_server.yml is "user-api"
            assert tool_config['description'] == "Updated description for user API"
            found_in_config = True
            break
    assert found_in_config

def test_delete_tool():
    # The tool is already registered by the fixture
    response = client.delete(f"/api/tools/{EXPECTED_TOOL_NAME}")
    assert response.status_code == 200
    assert response.json() == {"message": f"Tool {EXPECTED_TOOL_NAME} deleted successfully"}

    # Verify the tool is deleted from our internal service
    tools_response = client.get("/api/tools")
    assert tools_response.status_code == 200
    tools = tools_response.json()
    assert not any(tool["name"] == EXPECTED_TOOL_NAME for tool in tools)