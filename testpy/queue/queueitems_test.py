#!/usr/bin/env python3
"""
Queue Items Test Script
Tests GET /queue/{queueId}/items endpoint.

Usage:
    python queueitems_test.py <queue_id> [queue_name] [session_id]
"""

import requests
import sys
import json

# Add parent directory to path for config import
sys.path.insert(0, '..')
import config


def get_queue_items(queue_id, queue_name="Queue", session_id=None):
    """Test GET /queue/{queueId}/items - Get Work Items from Specific Queue"""
    url = f"{config.BASE_URL}/queue/{queue_id}/items"
    
    sid = session_id or config.get_session_id()
    if not sid:
        print("ERROR: No session ID provided. Please login first.")
        print("Usage: python queueitems_test.py <queue_id> [queue_name] [session_id]")
        return None
    
    headers = {
        "Content-Type": "application/json",
        "Accept": "application/json",
        "sessionId": str(sid)
    }
    
    params = {"queueName": queue_name}
    
    print(f"\n{'='*60}")
    print(f"QUEUE ITEMS TEST (GET /queue/{queue_id}/items)")
    print(f"{'='*60}")
    print(f"URL: {url}")
    print(f"Queue ID: {queue_id}")
    print(f"Queue Name: {queue_name}")
    print(f"Session ID: {sid}")
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
            if "workItems" in data:
                count = len(data["workItems"])
                print(f"\n📋 Found {count} work items in queue '{queue_name}'")
            
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
    if len(sys.argv) < 2:
        print("Usage: python queueitems_test.py <queue_id> [queue_name] [session_id]")
        print("Example: python queueitems_test.py 1 'My Queue' 123456789")
        sys.exit(1)
    
    queue_id = sys.argv[1]
    queue_name = sys.argv[2] if len(sys.argv) > 2 else "Queue"
    session_id = sys.argv[3] if len(sys.argv) > 3 else None
    
    result = get_queue_items(queue_id, queue_name, session_id)
    
    if result and result.get("success"):
        print("\n✅ Queue Items test passed!")
    else:
        print("\n❌ Queue Items test failed.")
