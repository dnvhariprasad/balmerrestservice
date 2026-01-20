
import requests
import json
import sys
import os

# Configuration
BASE_URL = "http://localhost:8080"
LOGIN_URL = f"{BASE_URL}/login-wmConnectCabinet"
ANNOTATIONS_URL = f"{BASE_URL}/notesheet/getannotations"
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
            print("Login failed: Session ID not found in response.")
            print(json.dumps(data, indent=2))
            return None
    except Exception as e:
        print(f"Login error: {e}")
        return None

def get_annotations(session_id, doc_index, dump_path):
    print(f"\nFetching annotations for document index: {doc_index}...")
    headers = {'sessionId': str(session_id)}
    params = {'documentIndex': doc_index}
    
    try:
        resp = requests.get(ANNOTATIONS_URL, headers=headers, params=params)
        print(f"Status: {resp.status_code}")
        
        try:
            data = resp.json()
            print("Response:")
            print(json.dumps(data, indent=2))
            
            # Dump to file
            print(f"Dumping results to {dump_path}...")
            os.makedirs(os.path.dirname(dump_path), exist_ok=True)
            with open(dump_path, "w") as f:
                json.dump(data, f, indent=2)
                
            return data
        except json.JSONDecodeError:
            print("Failed to parse JSON response")
            print(resp.text)
            return None
            
    except Exception as e:
        print(f"Error fetching annotations: {e}")
        return None

if __name__ == "__main__":
    doc_index = "1623"
    dump_path = "../tmp/annotations_verify.json"
    
    if len(sys.argv) > 1:
        doc_index = sys.argv[1]
        
    session_id = login()
    if session_id:
        get_annotations(session_id, doc_index, dump_path)
