#!/usr/bin/env python3
"""
Disconnect Test Script
Tests the /disconnect endpoint.

Usage:
    python disconnect_test.py [session_id] [username]
    
Requires a valid session ID from login.
"""

import requests
import sys
import json
import config


def disconnect(session_id=None, username=None):
    """
    Calls the disconnect endpoint to logout user.
    """
    url = f"{config.BASE_URL}/disconnect"
    
    # Use provided values or fallback to config
    sid = session_id or config.get_session_id()
    user = username or config.USERNAME
    
    if not sid:
        print("ERROR: No session ID provided. Please login first.")
        return False
    
    params = {"name": user}
    headers = {
        "sessionId": str(sid),
        "Content-Type": "application/json"
    }
    
    print(f"\n{'='*60}")
    print("DISCONNECT TEST")
    print(f"{'='*60}")
    print(f"URL: {url}")
    print(f"Session ID: {sid}")
    print(f"Username: {user}")
    print(f"{'='*60}\n")
    
    try:
        response = requests.post(url, params=params, headers=headers)
        
        print(f"Status Code: {response.status_code}")
        print(f"\nResponse:")
        print("-" * 40)
        
        try:
            data = response.json()
            print(json.dumps(data, indent=2))
        except json.JSONDecodeError:
            print(response.text)
            
        return response.status_code == 200
        
    except requests.exceptions.ConnectionError:
        print(f"ERROR: Could not connect to {config.BASE_URL}")
        return False
    except Exception as e:
        print(f"ERROR: {str(e)}")
        return False


if __name__ == "__main__":
    session_id = sys.argv[1] if len(sys.argv) > 1 else None
    username = sys.argv[2] if len(sys.argv) > 2 else None
    
    success = disconnect(session_id, username)
    
    if success:
        print("\n✅ Disconnect successful!")
    else:
        print("\n❌ Disconnect failed.")
