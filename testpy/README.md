# Python Test Scripts for Balmer REST Service

This folder contains Python test scripts for testing all REST API endpoints.

## Prerequisites

Install the `requests` library:

```bash
pip install requests
```

## Configuration

Edit `config.py` to set your server URL and credentials:

```python
BASE_URL = "http://localhost:8080"
USERNAME = "your_username"
PASSWORD = "your_password"
```

## Available Scripts

| Script               | Description                                          |
| -------------------- | ---------------------------------------------------- |
| `config.py`          | Configuration file with server URL and credentials   |
| `login_test.py`      | Test login endpoint (`POST /login-wmConnectCabinet`) |
| `disconnect_test.py` | Test disconnect/logout endpoint (`POST /disconnect`) |
| `session_test.py`    | Test all session endpoints                           |
| `queue_test.py`      | Test all queue endpoints                             |
| `run_all_tests.py`   | Run all tests in sequence                            |

## Usage Examples

### Run all tests:

```bash
python run_all_tests.py username password
```

### Test individual endpoints:

```bash
# Login
python login_test.py username password

# Session tests
python session_test.py all          # Run all session tests
python session_test.py create       # Create session only
python session_test.py info         # Get session info
python session_test.py stats        # Get session stats
python session_test.py invalidate   # Invalidate session

# Queue tests
python queue_test.py all            # Run all queue tests
python queue_test.py myqueue        # Get my queue items
python queue_test.py commonqueue    # Get common queue items
python queue_test.py allworkitems   # Get all work items
python queue_test.py list           # Get queue list

# Disconnect
python disconnect_test.py session_id username
```

## Session Management

The scripts automatically store the session ID after login. Other scripts will use this stored session ID if no explicit session ID is provided.
