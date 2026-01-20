
import requests
import json

# Configuration
BASE_URL = "http://localhost:8080"
LOGIN_URL = f"{BASE_URL}/login-wmConnectCabinet"
COMMENTS_URL = f"{BASE_URL}/notesheet/getcomments"

# Use known valid work item
WORKITEM_ID = "1" 
PROCESS_INSTANCE_ID = "e-Notes-000000000004-process"
USERNAME = "supervisor"
PASSWORD = "Sedin@123456"

def login():
    print(f"Logging in as {USERNAME}...")
    try:
        resp = requests.post(LOGIN_URL, json={'userName': USERNAME, 'password': PASSWORD})
        resp.raise_for_status()
        data = resp.json()
        session_id = data.get('wmConnectResponse', {}).get('Participant', {}).get('SessionId')
        if session_id:
            print(f"Login successful. Session ID: {session_id}")
            return session_id
        else:
            print("Login failed.")
            return None
    except Exception as e:
        print(f"Login error: {e}")
        return None

def get_comments(session_id):
    print(f"Getting comments for {PROCESS_INSTANCE_ID} / {WORKITEM_ID}...")
    
    params = {
        'workitemId': WORKITEM_ID,
        'processInstanceId': PROCESS_INSTANCE_ID
    }
    headers = {'sessionId': str(session_id)}
    
    try:
        resp = requests.get(COMMENTS_URL, headers=headers, params=params)
        print(f"Status: {resp.status_code}")
        
        try:
            data = resp.json()
            print("Response:")
            print(json.dumps(data, indent=2))
        except json.JSONDecodeError:
            print("Failed to parse JSON")
            print(resp.text)
            
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    session_id = login()
    if session_id:
        get_comments(session_id)
