#!/usr/bin/env python3
"""
Create PDF Note Test Script
Tests POST /notesheet/createpdfnote endpoint.

This endpoint creates a PDF from the original notesheet with:
- Supporting documents table (S.NO., Document Name, Document Index)
- Comments history table

Usage:
    python createpdfnote_test.py [process_instance_id] [workitem_id]

Default test values:
    processInstanceId: e-Notes-000000000045-process
    workitemId: 1
"""

import requests
import sys
import json

# Add parent directory to path for config import
sys.path.insert(0, '..')
import config


def create_pdf_note(process_instance_id=None, workitem_id=None):
    """Test POST /notesheet/createpdfnote - Create PDF Note with Documents and Comments"""
    url = f"{config.BASE_URL}/notesheet/createpdfnote"

    # Default test values
    pid = process_instance_id or "e-Notes-000000000045-process"
    wid = workitem_id or "1"

    headers = {
        "Content-Type": "application/json",
        "Accept": "application/json"
    }

    params = {
        "processInstanceId": pid,
        "workitemId": wid
    }

    print(f"\n{'='*60}")
    print("CREATE PDF NOTE TEST (POST /notesheet/createpdfnote)")
    print(f"{'='*60}")
    print(f"URL: {url}")
    print(f"Process Instance ID: {pid}")
    print(f"Workitem ID: {wid}")
    print(f"Note: Uses service account credentials from server config")
    print(f"{'='*60}\n")

    try:
        response = requests.post(url, headers=headers, params=params)

        print(f"Status Code: {response.status_code}")
        print(f"\nResponse:")
        print("-" * 40)

        try:
            data = response.json()
            print(json.dumps(data, indent=2))

            # Summary
            if data.get("success"):
                print(f"\n{'='*60}")
                print("SUCCESS! PDF Note Created")
                print(f"{'='*60}")
                print(f"   Original Doc Index: {data.get('originalDocIndex')}")
                print(f"   Note Document Index: {data.get('notedocumentIndex')}")
                print(f"   New Version: {data.get('newVersion')}")
                print(f"   PDF Path: {data.get('pdfPath')}")
                print(f"   Comments Path: {data.get('commentsPath')}")
                print(f"   Annotations Preserved: {data.get('annotationsPreserved')}")
            else:
                print(f"\n{'='*60}")
                print("FAILED!")
                print(f"{'='*60}")
                print(f"   Error: {data.get('error')}")

            return data
        except json.JSONDecodeError:
            print(response.text)
            return None

    except requests.exceptions.ConnectionError:
        print(f"ERROR: Could not connect to {config.BASE_URL}")
        print("Make sure the Spring Boot application is running.")
        return None
    except Exception as e:
        print(f"ERROR: {str(e)}")
        return None


if __name__ == "__main__":
    process_instance_id = sys.argv[1] if len(sys.argv) > 1 else None
    workitem_id = sys.argv[2] if len(sys.argv) > 2 else None
    create_pdf_note(process_instance_id, workitem_id)
