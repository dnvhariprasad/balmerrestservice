
import requests
import json
import os

# Configuration
BASE_URL = "http://localhost:8080"
LOGIN_URL = f"{BASE_URL}/login-wmConnectCabinet"
DEBUG_URL = f"{BASE_URL}/notesheet/debug/workitem"

# Use known valid work item (from previous tests)
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

def debug_workitem(session_id):
    print(f"Dumping work item details for {PROCESS_INSTANCE_ID} / {WORKITEM_ID}...")
    
    params = {
        'workitemId': WORKITEM_ID,
        'processInstanceId': PROCESS_INSTANCE_ID
    }
    headers = {'sessionId': str(session_id)}
    
    try:
        resp = requests.get(DEBUG_URL, headers=headers, params=params)
        print(f"Status: {resp.status_code}")
        print(resp.text)
        
        if resp.status_code == 200:
            data = resp.json()
            dump_file = data.get('dumpFile')
            if dump_file and os.path.exists(dump_file):
                print(f"\nDump file created at: {dump_file}")
                # Print last few lines or summary?
                # Just print file path for now.
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    session_id = login()
    if session_id:
        debug_workitem(session_id)
