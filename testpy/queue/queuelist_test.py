#!/usr/bin/env python3
"""
Queue List Test Script
Tests GET /queue/list endpoint.

Usage:
    python queuelist_test.py [session_id]
"""

import requests
import sys
import json

# Add parent directory to path for config import
sys.path.insert(0, '..')
import config


def get_queue_list(session_id=None):
    """Test GET /queue/list - Get User's Accessible Queues"""
    url = f"{config.BASE_URL}/queue/list"
    
    sid = session_id or config.get_session_id()
    if not sid:
        print("ERROR: No session ID provided. Please login first.")
        print("Usage: python queuelist_test.py <session_id>")
        return None
    
    headers = {
        "Content-Type": "application/json",
        "Accept": "application/json",
        "sessionId": str(sid)
    }
    
    print(f"\n{'='*60}")
    print("QUEUE LIST TEST (GET /queue/list)")
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
            if "queues" in data:
                count = len(data["queues"])
                print(f"\n📋 Found {count} accessible queues:")
                for q in data["queues"]:
                    print(f"   - {q.get('queueName', 'N/A')} (ID: {q.get('queueId', 'N/A')})")
            
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
    result = get_queue_list(session_id)
    
    if result and result.get("success"):
        print("\n✅ Queue List test passed!")
    else:
        print("\n❌ Queue List test failed.")
