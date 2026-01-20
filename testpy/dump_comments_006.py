
import requests
import json
import os
import sys

# Configuration
BASE_URL = "http://localhost:8080"
LOGIN_URL = f"{BASE_URL}/login-wmConnectCabinet"
COMMENTS_URL = f"{BASE_URL}/notesheet/getcomments"

# Target Details
WORKITEM_ID = "1" 
PROCESS_INSTANCE_ID = "e-Notes-000000000006-process"
USERNAME = "supervisor"
PASSWORD = "Sedin@123456"
OUTPUT_DIR = "../tmp/comments"
OUTPUT_FILE = os.path.join(OUTPUT_DIR, f"comments_{PROCESS_INSTANCE_ID}_{WORKITEM_ID}.json")

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

def dump_comments(session_id):
    print(f"Getting comments for {PROCESS_INSTANCE_ID} / {WORKITEM_ID}...")
    
    params = {
        'workitemId': WORKITEM_ID,
        'processInstanceId': PROCESS_INSTANCE_ID
    }
    headers = {'sessionId': str(session_id)}
    
    try:
        resp = requests.get(COMMENTS_URL, headers=headers, params=params)
        print(f"Status: {resp.status_code}")
        
        if resp.status_code == 200:
            data = resp.json()
            
            # Ensure output directory exists
            if not os.path.exists(OUTPUT_DIR):
                os.makedirs(OUTPUT_DIR)
                
            with open(OUTPUT_FILE, 'w') as f:
                json.dump(data, f, indent=2)
                
            print(f"Comments dumped to: {os.path.abspath(OUTPUT_FILE)}")
            print("Preview:")
            print(json.dumps(data, indent=2))
        else:
            print("Failed to retrieve comments")
            print(resp.text)
            
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    session_id = login()
    if session_id:
        dump_comments(session_id)
