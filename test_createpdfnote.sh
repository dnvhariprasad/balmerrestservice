#!/bin/bash
# Test script for createpdfnote endpoint
# Usage: ./test_createpdfnote.sh [processInstanceId] [workitemId]

BASE_URL="${BALMER_API_URL:-http://localhost:8089}"
USERNAME="supervisor"
PASSWORD="Sedin@123456"
PROCESS_INSTANCE_ID="${1:-e-Notes-000000000044-process}"
WORKITEM_ID="${2:-1}"

echo "=============================================="
echo "CreatePdfNote Test Script"
echo "=============================================="
echo "Base URL: $BASE_URL"
echo "Process Instance ID: $PROCESS_INSTANCE_ID"
echo "Workitem ID: $WORKITEM_ID"
echo ""

# Step 1: Login to get session ID
echo "Step 1: Logging in as $USERNAME..."
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/login-wmConnectCabinet" \
  -H "Content-Type: application/json" \
  -d "{\"userName\":\"$USERNAME\",\"password\":\"$PASSWORD\"}")

# Extract session ID using python (more reliable than jq for nested JSON)
SESSION_ID=$(echo "$LOGIN_RESPONSE" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    print(d.get('wmConnectResponse', {}).get('Participant', {}).get('SessionId', ''))
except:
    print('')
")

if [ -z "$SESSION_ID" ]; then
    echo "ERROR: Failed to get session ID"
    echo "Response: $LOGIN_RESPONSE"
    exit 1
fi

echo "Session ID: $SESSION_ID"
echo ""

# Step 2: Call createpdfnote endpoint
echo "Step 2: Calling createpdfnote..."
echo "URL: $BASE_URL/notesheet/createpdfnote"
echo ""

RESPONSE=$(curl -s -X POST "$BASE_URL/notesheet/createpdfnote?processInstanceId=$PROCESS_INSTANCE_ID&workitemId=$WORKITEM_ID&sessionId=$SESSION_ID" \
  -H "Content-Type: application/json")

# Pretty print the response
echo "Response:"
echo "$RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$RESPONSE"

echo ""
echo "=============================================="
echo "Test Complete"
echo "=============================================="

# Extract success status
SUCCESS=$(echo "$RESPONSE" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    print('true' if d.get('success') else 'false')
except:
    print('false')
")

if [ "$SUCCESS" = "true" ]; then
    echo "Result: SUCCESS"
    # Extract document index for viewing annotations
    DOC_INDEX=$(echo "$RESPONSE" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    print(d.get('notedocumentIndex', ''))
except:
    print('')
")
    if [ -n "$DOC_INDEX" ]; then
        echo "Note Document Index: $DOC_INDEX"
        echo ""

        # Step 3: Download document with annotations
        echo "=============================================="
        echo "Step 3: Downloading document with annotations..."
        echo "=============================================="

        # Create tmp directory if it doesn't exist
        SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
        TMP_DIR="$SCRIPT_DIR/tmp"
        mkdir -p "$TMP_DIR"

        OUTPUT_FILE="$TMP_DIR/notesheet_${DOC_INDEX}_annotated.pdf"

        echo "Downloading to: $OUTPUT_FILE"

        HTTP_CODE=$(curl -s -w "%{http_code}" -o "$OUTPUT_FILE" \
            -X GET "$BASE_URL/notesheet/downloadwithannotations?documentIndex=$DOC_INDEX" \
            -H "sessionId: $SESSION_ID")

        if [ "$HTTP_CODE" = "200" ]; then
            FILE_SIZE=$(wc -c < "$OUTPUT_FILE" | tr -d ' ')
            echo "Download SUCCESS: $FILE_SIZE bytes"
            echo "File saved to: $OUTPUT_FILE"

            # Open the PDF if on macOS
            if [[ "$OSTYPE" == "darwin"* ]]; then
                echo ""
                echo "Opening PDF..."
                open "$OUTPUT_FILE"
            fi
        else
            echo "Download FAILED: HTTP $HTTP_CODE"
            rm -f "$OUTPUT_FILE"
        fi
    fi
else
    echo "Result: FAILED"
    exit 1
fi
