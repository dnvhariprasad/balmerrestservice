#!/usr/bin/env python3
"""
All Workitems Test Script
Tests GET /queue/allworkitems endpoint.

Usage:
    python allworkitems_test.py [session_id]
"""

import requests
import sys
import json

# Add parent directory to path for config import
sys.path.insert(0, '..')
import config


def get_all_workitems(session_id=None):
    """Test GET /queue/allworkitems - Get All Work Items (My + Common)"""
    url = f"{config.BASE_URL}/queue/allworkitems"
    
    sid = session_id or config.get_session_id()
    if not sid:
        print("ERROR: No session ID provided. Please login first.")
        print("Usage: python allworkitems_test.py <session_id>")
        return None
    
    headers = {
        "Content-Type": "application/json",
        "Accept": "application/json",
        "sessionId": str(sid)
    }
    
    print(f"\n{'='*60}")
    print("ALL WORKITEMS TEST (GET /queue/allworkitems)")
    print(f"{'='*60}")
    print(f"URL: {url}")
    print(f"Session ID: {sid}")
    print(f"{'='*60}\n")
    
    try:
        response = requests.get(url, headers=headers)
        
        print(f"Status Code: {response.status_code}")
        print(f"\nResponse:")
        print("-" * 40)
        
        try:
            data = response.json()
            print(json.dumps(data, indent=2))
            
            # Summary
            if "workItems" in data:
                total = len(data["workItems"])
                my_count = data.get("myQueueCount", 0)
                common_count = data.get("commonQueueCount", 0)
                print(f"\n📋 Summary:")
                print(f"   My Queue: {my_count}")
                print(f"   Common Queue: {common_count}")
                print(f"   Total: {total}")
            
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


if __name__ == "__main__":
    session_id = sys.argv[1] if len(sys.argv) > 1 else None
    result = get_all_workitems(session_id)
    
    if result and result.get("success"):
        print("\n✅ All Workitems test passed!")
    else:
        print("\n❌ All Workitems test failed.")
