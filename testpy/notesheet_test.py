#!/usr/bin/env python3
"""
Notesheet Original Test Script
Tests GET /notesheet/getoriginal endpoint.

Usage:
    python notesheet_test.py [session_id] [process_instance_id] [workitem_id]
    
Default test values:
    processInstanceId: e-Notes-000000000006-process
    workitemId: 1
"""

import requests
import sys
import json

# Add parent directory to path for config import
sys.path.insert(0, '..')
import config


def get_original_notesheet(session_id=None, process_instance_id=None, workitem_id=None):
    """Test GET /notesheet/getoriginal - Get Original Notesheet Document"""
    url = f"{config.BASE_URL}/notesheet/getoriginal"
    
    # Default test values
    pid = process_instance_id or "e-Notes-000000000006-process"
    wid = workitem_id or "1"
    
    sid = session_id or config.get_session_id()
    if not sid:
        print("ERROR: No session ID provided. Please login first.")
        print("Usage: python notesheet_test.py <session_id> [process_instance_id] [workitem_id]")
        return None
    
    headers = {
        "Content-Type": "application/json",
        "Accept": "application/json",
        "sessionId": str(sid)
    }
    
    params = {
        "processInstanceId": pid,
        "workitemId": wid
    }
    
    print(f"\n{'='*60}")
    print("NOTESHEET ORIGINAL TEST (GET /notesheet/getoriginal)")
    print(f"{'='*60}")
    print(f"URL: {url}")
    print(f"Session ID: {sid}")
    print(f"Process Instance ID: {pid}")
    print(f"Workitem ID: {wid}")
    print(f"{'='*60}\n")
    
    try:
        response = requests.get(url, headers=headers, params=params)
        
        print(f"Status Code: {response.status_code}")
        print(f"\nResponse:")
        print("-" * 40)
        
        try:
            data = response.json()
            print(json.dumps(data, indent=2))
            
            # Summary
            if data.get("success"):
                if data.get("found"):
                    print(f"\n✅ Document found!")
                    print(f"   File Path: {data.get('filePath')}")
                    print(f"   Document Name: {data.get('documentName')}")
                    print(f"   File Size: {data.get('fileSize')} bytes")
                else:
                    print(f"\n⚠️ Document not found: {data.get('message')}")
            else:
                print(f"\n❌ Error: {data.get('error')}")
            
            return data
        except json.JSONDecodeError:
            print(response.text)
            return None
            
    except requests.exceptions.ConnectionError:
        print(f"ERROR: Could not connect to {config.BASE_URL}")
        return None
    except Exception as e:
        print(f"ERROR: {str(e)}")
        return None


def test_with_login():
    """Login first, then test notesheet endpoint."""
    print("\n" + "="*60)
    print("RUNNING NOTESHEET TEST WITH LOGIN")
    print("="*60)
    
    # First, login to get session
    from login_test import login
    session_id = login()
    
    if not session_id:
        print("ERROR: Could not obtain session ID. Test aborted.")
        return
    
    # Now test notesheet
    get_original_notesheet(session_id)
    
    print("\n" + "="*60)
    print("NOTESHEET TEST COMPLETED")
    print("="*60)


if __name__ == "__main__":
    if len(sys.argv) > 1:
        session_id = sys.argv[1]
        process_instance_id = sys.argv[2] if len(sys.argv) > 2 else None
        workitem_id = sys.argv[3] if len(sys.argv) > 3 else None
        get_original_notesheet(session_id, process_instance_id, workitem_id)
    else:
        # Run with login
        test_with_login()
