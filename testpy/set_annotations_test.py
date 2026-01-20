
import requests
import json
import sys
import os

# Configuration
BASE_URL = "http://localhost:8080"
LOGIN_URL = f"{BASE_URL}/login-wmConnectCabinet"
SET_ANNOTATIONS_URL = f"{BASE_URL}/notesheet/setannotations"
USERNAME = "supervisor"
PASSWORD = "Sedin@123456"
SOURCE_FILE = "../tmp/annotations_verify.json"
TARGET_DOC_INDEX = "1591"

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
            print("Login failed: Session ID not found in response.")
            print(json.dumps(data, indent=2))
            return None
    except Exception as e:
        print(f"Login error: {e}")
        return None

def set_annotations(session_id, doc_index, source_file):
    print(f"\nSetting annotations for document index: {doc_index} from {source_file}...")
    
    if not os.path.exists(source_file):
        print(f"Error: Source file {source_file} not found. Please run annotations_test.py first.")
        return

    with open(source_file, 'r') as f:
        annotations_data = json.load(f)

    headers = {'sessionId': str(session_id)}
    params = {'documentIndex': doc_index}
    
    try:
        print("Sending request...")
        resp = requests.post(SET_ANNOTATIONS_URL, headers=headers, params=params, json=annotations_data)
        print(f"Status: {resp.status_code}")
        
        try:
            data = resp.json()
            print("Response:")
            print(json.dumps(data, indent=2))
        except json.JSONDecodeError:
            print("Failed to parse JSON response")
            print(resp.text)
            
    except Exception as e:
        print(f"Error setting annotations: {e}")

if __name__ == "__main__":
    session_id = login()
    if session_id:
        set_annotations(session_id, TARGET_DOC_INDEX, SOURCE_FILE)
