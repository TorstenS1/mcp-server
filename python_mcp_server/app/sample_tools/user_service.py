from fastapi import FastAPI

app = FastAPI(
    title="User Service API",
    description="A sample API for managing user information.",
    version="1.0.0",
)

@app.get("/api/v1/users/{userId}", summary="Get user by ID")
def get_user_by_id(userId: str):
    """
    Retrieves user information by user ID.

    Args:
        userId (str): The ID of the user to retrieve.

    Returns:
        dict: A dictionary containing the user ID and name.
    """
    return {"userId": userId, "name": "Test User"}
