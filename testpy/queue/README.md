# Queue Test Scripts

Individual Python test scripts for queue-related endpoints.

## Prerequisites

Install the `requests` library:

```bash
pip install requests
```

## Configuration

Edit `../config.py` (parent folder) to set your server URL and credentials.

## Available Scripts

| Script                 | Endpoint                | Description                      |
| ---------------------- | ----------------------- | -------------------------------- |
| `myqueue_test.py`      | GET /queue/myqueue      | Get My Queue work items          |
| `commonqueue_test.py`  | GET /queue/commonqueue  | Get Common Queue work items      |
| `allworkitems_test.py` | GET /queue/allworkitems | Get all work items (My + Common) |
| `queuelist_test.py`    | GET /queue/list         | Get user's accessible queues     |
| `queueitems_test.py`   | GET /queue/{id}/items   | Get items from specific queue    |

## Usage

```bash
# Run from the queue folder
cd testpy/queue

# My Queue
python myqueue_test.py <session_id>

# Common Queue
python commonqueue_test.py <session_id>

# All Workitems
python allworkitems_test.py <session_id>

# Queue List
python queuelist_test.py <session_id>

# Specific Queue Items
python queueitems_test.py <queue_id> [queue_name] [session_id]
```

## Getting a Session ID

First run login from the parent folder:

```bash
cd testpy
python login_test.py username password
```

Then use the returned session ID with these scripts.
