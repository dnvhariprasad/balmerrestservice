import urllib.request
import json
import subprocess
import sys

BASE_URL = "http://74.225.130.20:8080/OmniDocsRestWS/rest/services"
EXECUTE_API_URL = f"{BASE_URL}/executeAPIJSON"
CHECKIN_API_URL = f"{BASE_URL}/checkInDocumentJSON"
USER = "supervisor"
PASS = "Sedin@123456"
CABINET = "fosasoft"
DOC_INDEX = "1623"

def post_json(url, data):
    req = urllib.request.Request(
        url, 
        data=json.dumps(data).encode('utf-8'), 
        headers={'Content-Type': 'application/json'}
    )
    try:
        with urllib.request.urlopen(req) as response:
            return json.loads(response.read().decode('utf-8'))
    except urllib.error.HTTPError as e:
        print(f"HTTP Error: {e.code} {e.reason}")
        print(e.read().decode('utf-8'))
        sys.exit(1)
    except Exception as e:
        print(f"Error: {e}")
        sys.exit(1)

def login():
    url = "http://localhost:8081/login-wmConnectCabinet"
    payload = {"userName": USER, "password": PASS}
    resp = post_json(url, payload)
    sid = resp.get("wmConnectResponse", {}).get("Participant", {}).get("SessionId")
    if sid:
        print(f"Login Success. SessionId: {sid}")
        return sid
    else:
        print(f"Login Failed. Response: {resp}")
        sys.exit(1)

def get_current_version(session_id):
    print("Getting Current Version via Checkout/Undo...")
    # Reuse checkout logic to get version
    version = checkout_document(session_id)
    # Immediately Undo so we can Inject
    force_undo_checkout(session_id)
    return version

def force_undo_checkout(session_id):
    print("Forcing Undo Checkout...")
    undo_input = {
        "NGOCheckinCheckoutExt_Input": {
            "Option": "NGOCheckinCheckoutExt",
            "CabinetName": CABINET,
            "UserDBId": session_id,
            "CheckInOutFlag": "U", 
            "Documents": {"Document": {"DocumentIndex": DOC_INDEX}}
        }
    }
    undo_payload = {"NGOExecuteAPIBDO": {"inputData": undo_input, "base64Encoded": "N", "locale": "en_US"}}
    post_json(EXECUTE_API_URL, undo_payload)
    # Ignore errors (e.g., if not checked out)

def checkout_document(session_id):
    print("Checking Out Document...")
    input_data = {
        "NGOCheckinCheckoutExt_Input": {
            "Option": "NGOCheckinCheckoutExt",
            "CabinetName": CABINET,
            "UserDBId": session_id,
            "CurrentDateTime": "2025-12-11 14:00:00",
            "CheckInOutFlag": "Y", 
            "SupAnnotVersion": "N",
            "Documents": {"Document": {"DocumentIndex": DOC_INDEX}}
        }
    }
    payload = {"NGOExecuteAPIBDO": {"inputData": input_data, "base64Encoded": "N", "locale": "en_US"}}
    resp = post_json(EXECUTE_API_URL, payload)
    output = resp.get("NGOExecuteAPIResponseBDO", {}).get("outputData", {}).get("NGOCheckinCheckoutExt_Output", {})
    
    status = output.get("Status")
    if status == "0":
        print("Checkout Success.")
        doc = output.get("Documents", {}).get("Document", {})
        return {
            "version": doc.get("DocumentVersionNo"),
            "volume_id": doc.get("VolumeId"),
            "site_id": doc.get("SiteId"), # Might be missing, check defaults
            "parent_folder_index": doc.get("ParentFolderIndex")
        }
    elif status == "-50146" or status == "50011":
        print("Document already checked out (unexpected). performing Undo and Retry.")
        force_undo_checkout(session_id)
        return checkout_document(session_id)
    else:
        print(f"Checkout Failed: {resp}")
        sys.exit(1)

def get_annotations(session_id, version):
    print(f"Getting Annotations for Version {version}...")
    input_data = {
        "NGOGetAnnotationGroupList_Input": {
            "Option": "NGOGetAnnotationGroupList",
            "CabinetName": CABINET,
            "UserDBId": session_id,
            "DocumentIndex": DOC_INDEX,
            "PageNo": "1", # Match the injected page
            "PreviousAnnotationIndex": "0",
            "VersionNo": version,
            "SortOrder": "A",
            "NoOfRecordsToFetch": "100"
        }
    }
    payload = {"NGOExecuteAPIBDO": {"inputData": input_data, "base64Encoded": "N", "locale": "en_US"}}
    resp = post_json(EXECUTE_API_URL, payload)
    output = resp.get("NGOExecuteAPIResponseBDO", {}).get("outputData", {}).get("NGOGetAnnotationGroupList_Output", {})
    print(f"DEBUG: Annotation Output: {output}")
    
    if output.get("Status") == "0":
        groups = output.get("AnnotationGroups", {})
        if not groups:
            print("No annotations found.")
            return None
        print(f"Annotations found.")
        return groups
    else:
        print(f"Get Annotations Failed: {output}")
        return None

def checkin_new_version(session_id, doc_info):
    print("Checking in New Version via curl...")
    # Use captured info or safe defaults
    vol_id = doc_info.get("volume_id", "1")
    if not vol_id: vol_id = "1"
    
    site_id = doc_info.get("site_id", "1")
    if not site_id: site_id = "1"

    data = {
        "cabinetName": CABINET,
        "userDBId": session_id,
        "documentIndex": DOC_INDEX,
        "checkInOutFlag": "N",
        "majorVersion": "N",
        "volumeId": vol_id, 
        "siteId": site_id,
        "supAnnotVersion": "N",
        "createdByAppName": "pdf"
    }
    
    # Construct curl command
    # Use NGOCheckInDocumentBDO instead of NGOAddDocumentBDO
    json_part = f"NGOCheckInDocumentBDO={json.dumps(data)};type=application/json"
    cmd = [
        "curl", "-s", "-X", "POST",
        "-F", json_part,
        "-F", "file=@/Users/hariprasad/Workspace/BLNoteComments/samplefiles/sample.pdf;type=application/pdf",
        CHECKIN_API_URL
    ]
    
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        print("Curl failed:", result.stderr)
        sys.exit(1)
        
    try:
        resp = json.loads(result.stdout)
        status = resp.get("NGOCheckInDocumentResponseBDO", {}).get("status")
        if status == "0":
            new_version = resp.get("NGOCheckInDocumentResponseBDO", {}).get("documentVersionNo")
            print(f"Checkin Success. New Version: {new_version}")
            return new_version
        else:
            print(f"Checkin Failed: {resp}")
            sys.exit(1)
    except json.JSONDecodeError:
        print("Failed to decode curl output:", result.stdout)
        sys.exit(1)

def restore_annotations(session_id, version, groups_data):
    print(f"Restoring Annotations on Version {version} using NGOAddAnnotation loop...")
    
    groups = groups_data.get("AnnotationGroup")
    if not groups:
        return

    if isinstance(groups, dict):
        groups = [groups]
    
    for g in groups:
        print(f"Restoring group: {g.get('AnnotGroupName')}...")
        
        # specific fix for 'AnnotationType' which might be empty string for Image
        annot_type = g.get("AnnotationType")
        if annot_type is None: annot_type = ""
        
        input_data = {
            "NGOAddAnnotation_Input": {
                "Option": "NGOAddAnnotation",
                "CabinetName": CABINET,
                "UserDBId": session_id,
                "DocumentIndex": DOC_INDEX,
                "AnnotationGroup": {
                    "AnnotationType": annot_type,
                    "PageNo": g.get("PageNo"),
                    "AnnotGroupName": g.get("AnnotGroupName"),
                    "AccessType": g.get("AccessType"),
                    "AnnotationBuffer": g.get("AnnotationBuffer")
                },
                "MajorVersion": "N"
            }
        }
        
        payload = {"NGOExecuteAPIBDO": {"inputData": input_data, "base64Encoded": "N", "locale": "en_US"}}
        resp = post_json(EXECUTE_API_URL, payload)
        output = resp.get("NGOExecuteAPIResponseBDO", {}).get("outputData", {}).get("NGOAddAnnotation_Output", {})
        status = output.get("Status")
        
        if status == "0":
            print(f"Restore Success for {g.get('AnnotGroupName')}.")
        elif status == "-50090":
             print(f"Annotation {g.get('AnnotGroupName')} already exists. Skipping.")
        else:
            print(f"Restore Failed for {g.get('AnnotGroupName')}: {status} - {output.get('Error')}")

def set_annotations(session_id, version, groups_data):
    # Wrapper to maintain compatibility with main call
    restore_annotations(session_id, version, groups_data)

def inject_test_annotation(session_id, version):
    print(f"Injecting Test Annotation on Version {version} using NGOAddAnnotation...")
    # Attempt 1: Add Note Annotation
    input_data = {
        "NGOAddAnnotation_Input": {
            "Option": "NGOAddAnnotation",
            "CabinetName": CABINET,
            "UserDBId": session_id,
            "DocumentIndex": DOC_INDEX,
            "AnnotationGroup": {
                "AnnotationType": "N", # Note
                "PageNo": "1",
                "AnnotGroupName": "TestGroup",
                "AccessType": "S", # Shared
                "AnnotationBuffer": "TotalAnnotations=1\n[Annotation 0]\nType=Note\nX=100\nY=100\nText=TestAnnotation"
            },
            "MajorVersion": "N" # Do not force major version (unless checkin does)
        }
    }
    payload = {"NGOExecuteAPIBDO": {"inputData": input_data, "base64Encoded": "N", "locale": "en_US"}}
    resp = post_json(EXECUTE_API_URL, payload)
    status = resp.get("NGOExecuteAPIResponseBDO", {}).get("outputData", {}).get("NGOAddAnnotation_Output", {}).get("Status")
    
    if status == "0":
        print("Injection Success (Type N).")
        return
    
    print(f"Injection Type N Failed: {status} - {resp}")
    print("Retrying with Image Annotation (Type '') and sample buffer...")
    
    # Attempt 2: Add Image Annotation (Rectangle)
    input_data_img = {
        "NGOAddAnnotation_Input": {
            "Option": "NGOAddAnnotation",
            "CabinetName": CABINET,
            "UserDBId": session_id,
            "DocumentIndex": DOC_INDEX,
            "AnnotationGroup": {
                "AnnotationType": "", # Image/Stamp
                "PageNo": "1",
                "AnnotGroupName": "TestImgGroup",
                "AccessType": "S",
                # Buffer from documentation example
                "AnnotationBuffer": "TotalAnnotations=1NoOfRectangle=1[DefaultRectangle1]Rights=VMThickness=3CornerStyle=0"
            },
             "MajorVersion": "N"
        }
    }
    payload_img = {"NGOExecuteAPIBDO": {"inputData": input_data_img, "base64Encoded": "N", "locale": "en_US"}}
    resp_img = post_json(EXECUTE_API_URL, payload_img)
    status_img = resp_img.get("NGOExecuteAPIResponseBDO", {}).get("outputData", {}).get("NGOAddAnnotation_Output", {}).get("Status")
    
    if status_img == "0":
        print("Injection Success (Type Image).")
    else:
        print(f"Injection Type Image Failed: {status_img} - {resp_img}")

def main():
    sid = login()
    
    # 1. Ensure clean state
    force_undo_checkout(sid)
    curr_version = get_current_version(sid)
    
    # 2. Inject Test Annotation (On Unlocked Document)
    # inject_test_annotation(sid, curr_version)
    
    # 3. Checkout (to start copy process)
    # We re-fetch version/status via checkout
    doc_info = checkout_document(sid) # Now returns dict
    curr_version = doc_info.get("version")
    
    annotations = get_annotations(sid, curr_version)
    
    if not annotations:
        print("Aborting: No annotations to preserve/copy. Please add annotations to the document first.")
        # Proceeding would erase nothing, but we want to PROVE copy works.
        # But if user says they added them, they should be there.
        sys.exit(0)
        
    new_version = checkin_new_version(sid, doc_info) # Pass doc_info
    set_annotations(sid, new_version, annotations)
    
    final_annots = get_annotations(sid, new_version)
    if final_annots:
        print("SUCCESS: Annotations restored on new version.")
    else:
        print("FAILURE: Annotations not found on new version.")

if __name__ == "__main__":
    main()
