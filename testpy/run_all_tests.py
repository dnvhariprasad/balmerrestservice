#!/usr/bin/env python3
"""
Run All Tests Script
Runs all endpoint tests in sequence.

Usage:
    python run_all_tests.py [username] [password]
"""

import sys
import config

# Import test modules
import login_test
import session_test
import queue_test
import disconnect_test


def main():
    print("\n" + "="*70)
    print("BALMER REST SERVICE - COMPLETE API TEST SUITE")
    print("="*70)
    print(f"Base URL: {config.BASE_URL}")
    print("="*70 + "\n")
    
    # Get credentials from command line or use defaults
    username = sys.argv[1] if len(sys.argv) > 1 else None
    password = sys.argv[2] if len(sys.argv) > 2 else None
    
    # Step 1: Login
    print("\n" + "#"*70)
    print("STEP 1: LOGIN")
    print("#"*70)
    session_id = login_test.login(username, password)
    
    if not session_id:
        print("\n❌ Login failed. Cannot continue with other tests.")
        return
    
    # Step 2: Session Tests
    print("\n" + "#"*70)
    print("STEP 2: SESSION TESTS")
    print("#"*70)
    session_test.get_session_info()
    session_test.get_session_stats()
    
    # Step 3: Queue Tests
    print("\n" + "#"*70)
    print("STEP 3: QUEUE TESTS")
    print("#"*70)
    queue_test.get_queue_list(session_id)
    queue_test.get_my_queue(session_id)
    queue_test.get_common_queue(session_id)
    queue_test.get_all_workitems(session_id)
    
    # Step 4: Disconnect
    print("\n" + "#"*70)
    print("STEP 4: DISCONNECT")
    print("#"*70)
    disconnect_test.disconnect(session_id, username or config.USERNAME)
    
    print("\n" + "="*70)
    print("✅ ALL TESTS COMPLETED")
    print("="*70 + "\n")


if __name__ == "__main__":
    main()
