#!/usr/bin/env python3
"""
Queue Test Script
Tests queue management endpoints:
- GET /queue/myqueue (My Queue Work Items)
- GET /queue/commonqueue (Common Queue Work Items)
- GET /queue/allworkitems (All Work Items)
- GET /queue/list (User's Accessible Queues)
- GET /queue/{queueId}/items (Specific Queue Items)

Usage:
    python queue_test.py [test_name] [session_id]
    
    test_name: myqueue, commonqueue, allworkitems, list, items, all (default: all)
    session_id: optional, uses stored session if not provided
"""

import requests
import sys
import json
import config


def get_my_queue(session_id=None):
    """Test GET /queue/myqueue - Get My Queue Work Items"""
    url = f"{config.BASE_URL}/queue/myqueue"
    
    sid = session_id or config.get_session_id()
    if not sid:
        print("ERROR: No session ID. Please login first.")
        return None
    
    headers = config.get_headers()
    headers["sessionId"] = str(sid)
    
    print(f"\n{'='*60}")
    print("MY QUEUE TEST (GET /queue/myqueue)")
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
            return data
        except json.JSONDecodeError:
            print(response.text)
            return None
            
    except requests.exceptions.ConnectionError:
        print(f"ERROR: Could not connect to {config.BASE_URL}")
        return None


def get_common_queue(session_id=None):
    """Test GET /queue/commonqueue - Get Common Queue Work Items"""
    url = f"{config.BASE_URL}/queue/commonqueue"
    
    sid = session_id or config.get_session_id()
    if not sid:
        print("ERROR: No session ID. Please login first.")
        return None
    
    headers = config.get_headers()
    headers["sessionId"] = str(sid)
    
    print(f"\n{'='*60}")
    print("COMMON QUEUE TEST (GET /queue/commonqueue)")
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
            return data
        except json.JSONDecodeError:
            print(response.text)
            return None
            
    except requests.exceptions.ConnectionError:
        print(f"ERROR: Could not connect to {config.BASE_URL}")
        return None


def get_all_workitems(session_id=None):
    """Test GET /queue/allworkitems - Get All Work Items (My + Common)"""
    url = f"{config.BASE_URL}/queue/allworkitems"
    
    sid = session_id or config.get_session_id()
    if not sid:
        print("ERROR: No session ID. Please login first.")
        return None
    
    headers = config.get_headers()
    headers["sessionId"] = str(sid)
    
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
            return data
        except json.JSONDecodeError:
            print(response.text)
            return None
            
    except requests.exceptions.ConnectionError:
        print(f"ERROR: Could not connect to {config.BASE_URL}")
        return None


def get_queue_list(session_id=None):
    """Test GET /queue/list - Get User's Accessible Queues"""
    url = f"{config.BASE_URL}/queue/list"
    
    sid = session_id or config.get_session_id()
    if not sid:
        print("ERROR: No session ID. Please login first.")
        return None
    
    headers = config.get_headers()
    headers["sessionId"] = str(sid)
    
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
            return data
        except json.JSONDecodeError:
            print(response.text)
            return None
            
    except requests.exceptions.ConnectionError:
        print(f"ERROR: Could not connect to {config.BASE_URL}")
        return None


def get_queue_items(queue_id, queue_name="TestQueue", session_id=None):
    """Test GET /queue/{queueId}/items - Get Work Items from Specific Queue"""
    url = f"{config.BASE_URL}/queue/{queue_id}/items"
    
    sid = session_id or config.get_session_id()
    if not sid:
        print("ERROR: No session ID. Please login first.")
        return None
    
    headers = config.get_headers()
    headers["sessionId"] = str(sid)
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
            return data
        except json.JSONDecodeError:
            print(response.text)
            return None
            
    except requests.exceptions.ConnectionError:
        print(f"ERROR: Could not connect to {config.BASE_URL}")
        return None


def run_all_tests(session_id=None):
    """Run all queue tests."""
    print("\n" + "="*60)
    print("RUNNING ALL QUEUE TESTS")
    print("="*60)
    
    # First, login to get session
    if not session_id and not config.get_session_id():
        print("\nNo session ID provided. Attempting login...")
        from login_test import login
        session_id = login()
        
    if not session_id:
        session_id = config.get_session_id()
        
    if not session_id:
        print("ERROR: Could not obtain session ID. Tests aborted.")
        return
    
    # Test 1: Get queue list
    queue_list_response = get_queue_list(session_id)
    
    # Test 2: Get my queue
    get_my_queue(session_id)
    
    # Test 3: Get common queue
    get_common_queue(session_id)
    
    # Test 4: Get all work items
    get_all_workitems(session_id)
    
    # Test 5: Get specific queue items (if we have a queue)
    if queue_list_response and "queues" in queue_list_response:
        queues = queue_list_response["queues"]
        if queues and len(queues) > 0:
            first_queue = queues[0]
            get_queue_items(
                first_queue.get("queueId", 1),
                first_queue.get("queueName", "Queue"),
                session_id
            )
    
    print("\n" + "="*60)
    print("ALL QUEUE TESTS COMPLETED")
    print("="*60)


if __name__ == "__main__":
    test_name = sys.argv[1] if len(sys.argv) > 1 else "all"
    session_id = sys.argv[2] if len(sys.argv) > 2 else None
    
    if test_name == "myqueue":
        get_my_queue(session_id)
    elif test_name == "commonqueue":
        get_common_queue(session_id)
    elif test_name == "allworkitems":
        get_all_workitems(session_id)
    elif test_name == "list":
        get_queue_list(session_id)
    elif test_name == "items":
        queue_id = sys.argv[2] if len(sys.argv) > 2 else 1
        session_id = sys.argv[3] if len(sys.argv) > 3 else None
        get_queue_items(queue_id, "Queue", session_id)
    else:
        run_all_tests(session_id)
