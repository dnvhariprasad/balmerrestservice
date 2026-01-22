#!/usr/bin/env python3
"""
Test script for createpdfnote endpoint.
Creates PDF note and downloads the result for verification.
"""

import requests
import json
import sys
import os

# Configuration
BASE_URL = "http://localhost:8080"
USERNAME = "ashish"
PASSWORD = "Sedin@123456"
PROCESS_INSTANCE_ID = "e-Notes-000000000055-process"
WORKITEM_ID = "1"

def login():
    """Login and get session ID."""
    url = f"{BASE_URL}/login-wmConnectCabinet"
    payload = {"userName": USERNAME, "password": PASSWORD}

    print(f"Logging in as {USERNAME}...")
    response = requests.post(url, json=payload, headers={"Content-Type": "application/json"})
    data = response.json()

    session_id = data.get("wmConnectResponse", {}).get("Participant", {}).get("SessionId")
    if session_id:
        print(f"Login successful. Session ID: {session_id}")
        return session_id
    else:
        print(f"Login failed: {data}")
        return None

def create_pdf_note(session_id):
    """Call createpdfnote endpoint."""
    url = f"{BASE_URL}/notesheet/createpdfnote"
    params = {
        "processInstanceId": PROCESS_INSTANCE_ID,
        "workitemId": WORKITEM_ID
    }
    headers = {
        "Content-Type": "application/json",
        "sessionId": str(session_id)
    }

    print(f"\nCalling createpdfnote...")
    print(f"  processInstanceId: {PROCESS_INSTANCE_ID}")
    print(f"  workitemId: {WORKITEM_ID}")

    response = requests.post(url, params=params, headers=headers)
    data = response.json()

    print(f"\nResponse:")
    print(json.dumps(data, indent=2))

    return data

def get_annotations(session_id, document_index):
    """Get annotations for a document."""
    url = f"{BASE_URL}/notesheet/getannotations"
    params = {
        "documentIndex": document_index
    }
    headers = {
        "Content-Type": "application/json",
        "sessionId": str(session_id)
    }

    print(f"\nGetting annotations for document {document_index}...")
    response = requests.get(url, params=params, headers=headers)
    data = response.json()

    print(f"\nAnnotations Response:")
    print(json.dumps(data, indent=2))

    return data

def download_document(session_id, document_index, output_path):
    """Download document content."""
    url = f"{BASE_URL}/notesheet/download"
    params = {
        "documentIndex": document_index,
        "sessionId": session_id
    }

    print(f"\nDownloading document {document_index}...")
    response = requests.get(url, params=params)

    if response.status_code == 200:
        with open(output_path, 'wb') as f:
            f.write(response.content)
        print(f"Document saved to: {output_path}")
        return True
    else:
        print(f"Download failed: {response.status_code} - {response.text}")
        return False

def main():
    # Step 1: Login
    session_id = login()
    if not session_id:
        sys.exit(1)

    # Step 2: Create PDF note
    result = create_pdf_note(session_id)

    if not result.get("success"):
        print(f"\nError: {result.get('error')}")
        print(f"Details: {result.get('details')}")
        sys.exit(1)

    # Step 3: Get document index from result
    note_doc_index = result.get("notedocumentIndex")
    print(f"\nNote document index: {note_doc_index}")

    # Step 4: Get annotations to verify
    if note_doc_index:
        annotations = get_annotations(session_id, note_doc_index)

        # Parse and display View hyperlink coordinates
        if annotations.get("success"):
            annot_data = annotations.get("annotations", {})
            groups = annot_data.get("AnnotationGroup", [])
            if not isinstance(groups, list):
                groups = [groups]

            print("\n" + "="*60)
            print("VIEW HYPERLINK ANNOTATIONS ANALYSIS")
            print("="*60)

            for group in groups:
                group_name = group.get("AnnotGroupName", "")
                buffer = group.get("AnnotationBuffer", "")

                if "ViewLinks" in group_name or "HyperlinkName=View" in buffer:
                    print(f"\nGroup: {group_name}")
                    print(f"Buffer:\n{buffer}")

                    # Parse coordinates from buffer
                    lines = buffer.split('\n')
                    current_hyperlink = None
                    for line in lines:
                        if line.startswith('[') and 'Hyperlink' in line:
                            current_hyperlink = line
                            print(f"\n{current_hyperlink}")
                        elif '=' in line and current_hyperlink:
                            key, value = line.split('=', 1)
                            if key in ['X1', 'Y1', 'X2', 'Y2']:
                                print(f"  {key}={value}")

    print("\n" + "="*60)
    print("TEST COMPLETE")
    print("="*60)

if __name__ == "__main__":
    main()
