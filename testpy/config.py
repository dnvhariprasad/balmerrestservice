"""
Configuration file for REST API testing.
Modify these values according to your environment.
"""
import os

# Server Configuration
# Can be overridden via environment variable: export BALMER_API_URL=http://localhost:8089
BASE_URL = os.getenv("BALMER_API_URL", "http://localhost:8089")

# Credentials
USERNAME = "supervisor"
PASSWORD = "Sedin@123456"

# Session storage (populated by login)
SESSION_ID = None


def get_headers(include_session=True):
    """Get common headers for API requests."""
    headers = {
        "Content-Type": "application/json",
        "Accept": "application/json"
    }
    if include_session and SESSION_ID:
        headers["sessionId"] = str(SESSION_ID)
    return headers


def set_session_id(session_id):
    """Store session ID from login response."""
    global SESSION_ID
    SESSION_ID = session_id
    print(f"Session ID stored: {SESSION_ID}")


def get_session_id():
    """Get stored session ID."""
    return SESSION_ID
