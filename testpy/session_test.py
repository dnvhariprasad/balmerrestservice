#!/usr/bin/env python3
"""
Session Test Script
Tests session management endpoints:
- POST /session (Get or Create Session)
- DELETE /session (Invalidate Session)
- GET /session/info (Get Session Info)
- GET /session/stats (Get Session Stats)

Usage:
    python session_test.py [test_name]
    
    test_name can be: create, invalidate, info, stats, all (default: all)
"""

import requests
import sys
import json
import config


def create_session(username=None, password=None):
    """Test POST /session - Get or Create Session (Cached)"""
    url = f"{config.BASE_URL}/session"
    
    user = username or config.USERNAME
    pwd = password or config.PASSWORD
    
    payload = {
        "userName": user,
        "password": pwd
    }
    
    print(f"\n{'='*60}")
    print("CREATE SESSION TEST (POST /session)")
    print(f"{'='*60}")
    print(f"URL: {url}")
    print(f"{'='*60}\n")
    
    try:
        response = requests.post(
            url,
            json=payload,
            headers={"Content-Type": "application/json"}
        )
        
        print(f"Status Code: {response.status_code}")
        print(f"\nResponse:")
        print("-" * 40)
        
        try:
            data = response.json()
            print(json.dumps(data, indent=2))
            
            if "sessionId" in data:
                config.set_session_id(data["sessionId"])
                return data["sessionId"]
        except json.JSONDecodeError:
            print(response.text)
            
        return None
        
    except requests.exceptions.ConnectionError:
        print(f"ERROR: Could not connect to {config.BASE_URL}")
        return None


def invalidate_session(username=None):
    """Test DELETE /session - Invalidate Session"""
    url = f"{config.BASE_URL}/session"
    
    user = username or config.USERNAME
    params = {"userName": user}
    
    print(f"\n{'='*60}")
    print("INVALIDATE SESSION TEST (DELETE /session)")
    print(f"{'='*60}")
    print(f"URL: {url}")
    print(f"Username: {user}")
    print(f"{'='*60}\n")
    
    try:
        response = requests.delete(url, params=params)
        
        print(f"Status Code: {response.status_code}")
        print(f"\nResponse:")
        print("-" * 40)
        
        try:
            print(json.dumps(response.json(), indent=2))
        except json.JSONDecodeError:
            print(response.text)
            
        return response.status_code == 200
        
    except requests.exceptions.ConnectionError:
        print(f"ERROR: Could not connect to {config.BASE_URL}")
        return False


def get_session_info(username=None):
    """Test GET /session/info - Get Session Info"""
    url = f"{config.BASE_URL}/session/info"
    
    user = username or config.USERNAME
    params = {"userName": user}
    
    print(f"\n{'='*60}")
    print("SESSION INFO TEST (GET /session/info)")
    print(f"{'='*60}")
    print(f"URL: {url}")
    print(f"Username: {user}")
    print(f"{'='*60}\n")
    
    try:
        response = requests.get(url, params=params)
        
        print(f"Status Code: {response.status_code}")
        print(f"\nResponse:")
        print("-" * 40)
        
        try:
            print(json.dumps(response.json(), indent=2))
        except json.JSONDecodeError:
            print(response.text)
            
        return response.status_code == 200
        
    except requests.exceptions.ConnectionError:
        print(f"ERROR: Could not connect to {config.BASE_URL}")
        return False


def get_session_stats():
    """Test GET /session/stats - Get Session Cache Stats"""
    url = f"{config.BASE_URL}/session/stats"
    
    print(f"\n{'='*60}")
    print("SESSION STATS TEST (GET /session/stats)")
    print(f"{'='*60}")
    print(f"URL: {url}")
    print(f"{'='*60}\n")
    
    try:
        response = requests.get(url)
        
        print(f"Status Code: {response.status_code}")
        print(f"\nResponse:")
        print("-" * 40)
        
        try:
            print(json.dumps(response.json(), indent=2))
        except json.JSONDecodeError:
            print(response.text)
            
        return response.status_code == 200
        
    except requests.exceptions.ConnectionError:
        print(f"ERROR: Could not connect to {config.BASE_URL}")
        return False


def run_all_tests():
    """Run all session tests."""
    print("\n" + "="*60)
    print("RUNNING ALL SESSION TESTS")
    print("="*60)
    
    # Test 1: Create session
    session_id = create_session()
    
    # Test 2: Get session info
    get_session_info()
    
    # Test 3: Get session stats
    get_session_stats()
    
    # Test 4: Invalidate session
    invalidate_session()
    
    # Test 5: Verify session is gone
    get_session_info()
    
    print("\n" + "="*60)
    print("ALL SESSION TESTS COMPLETED")
    print("="*60)


if __name__ == "__main__":
    test_name = sys.argv[1] if len(sys.argv) > 1 else "all"
    
    if test_name == "create":
        create_session()
    elif test_name == "invalidate":
        invalidate_session()
    elif test_name == "info":
        get_session_info()
    elif test_name == "stats":
        get_session_stats()
    else:
        run_all_tests()
