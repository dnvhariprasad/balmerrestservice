#!/usr/bin/env python3
"""
Login Test Script
Tests the /login-wmConnectCabinet endpoint.

Usage:
    python login_test.py [username] [password]
    
If no arguments provided, uses values from config.py
"""

import requests
import sys
import json
import config


def login(username=None, password=None):
    """
    Calls the login endpoint and returns the response.
    Stores session ID in config for use by other scripts.
    """
    url = f"{config.BASE_URL}/login-wmConnectCabinet"
    
    # Use provided credentials or fallback to config
    user = username or config.USERNAME
    pwd = password or config.PASSWORD
    
    payload = {
        "userName": user,
        "password": pwd
    }
    
    print(f"\n{'='*60}")
    print("LOGIN TEST")
    print(f"{'='*60}")
    print(f"URL: {url}")
    print(f"Username: {user}")
    print(f"{'='*60}\n")
    
    try:
        response = requests.post(
            url,
            json=payload,
            headers={"Content-Type": "application/json", "Accept": "application/json"}
        )
        
        print(f"Status Code: {response.status_code}")
        print(f"\nResponse:")
        print("-" * 40)
        
        try:
            data = response.json()
            print(json.dumps(data, indent=2))
            
            # Extract and store session ID
            if "wmConnectResponse" in data:
                wm_response = data["wmConnectResponse"]
                if "Participant" in wm_response and "SessionId" in wm_response["Participant"]:
                    session_id = wm_response["Participant"]["SessionId"]
                    config.set_session_id(session_id)
                    return session_id
            
            # Alternative path for session endpoint
            if "sessionId" in data:
                config.set_session_id(data["sessionId"])
                return data["sessionId"]
                
        except json.JSONDecodeError:
            print(response.text)
            
        return None
        
    except requests.exceptions.ConnectionError:
        print(f"ERROR: Could not connect to {config.BASE_URL}")
        print("Make sure the server is running.")
        return None
    except Exception as e:
        print(f"ERROR: {str(e)}")
        return None


if __name__ == "__main__":
    # Get credentials from command line or use defaults
    username = sys.argv[1] if len(sys.argv) > 1 else None
    password = sys.argv[2] if len(sys.argv) > 2 else None
    
    session_id = login(username, password)
    
    if session_id:
        print(f"\n✅ Login successful! Session ID: {session_id}")
    else:
        print("\n❌ Login failed or no session ID returned.")
