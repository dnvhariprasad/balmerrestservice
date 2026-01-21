# CreatePdfNote Service Documentation

## Overview

The `createpdfnote` endpoint generates a PDF notesheet with:
1. Original notesheet content (from OmniDocs)
2. Supporting Documents table with clickable "View" hyperlinks
3. Comments/approval history
4. Preserved annotations from the original document

The "View" links are OmniDocs **Hyperlink Annotations** that open the corresponding supporting document in the viewer.

---

## API Endpoints

### POST /notesheet/createpdfnote
Creates/updates the PDF notesheet with View hyperlink annotations.

**Parameters:**
- `processInstanceId` (query): e.g., `e-Notes-000000000044-process`
- `workitemId` (query): e.g., `1`

**Response:**
```json
{
  "success": true,
  "originalDocIndex": "1669",
  "notedocumentIndex": "1668",
  "newVersion": "1.5",
  "pdfPath": "/tmp/notesheets/newNoteContent-uuid.pdf",
  "annotationsPreserved": true,
  "viewHyperlinksAdded": 2
}
```

### GET /notesheet/downloadwithannotations
Downloads a document with annotations burned into the PDF.

**Parameters:**
- `documentIndex` (query): Document index
- `sessionId` (header): Session ID from login

**Response:** PDF file with annotations rendered

---

## Architecture Flow

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         createPdfNote Flow                               │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  1. Get Original Notesheet                                               │
│     └─> fetchWorkItemAttributes() → notesheet_original attribute         │
│     └─> downloadDocument() → PDF content                                 │
│                                                                          │
│  2. Get Supporting Documents                                             │
│     └─> fetchWorkItemAttributes() → attachment folder                    │
│     └─> listFolderContents() → document list                            │
│                                                                          │
│  3. Get Comments History                                                 │
│     └─> getComments() → approval/comment history                        │
│                                                                          │
│  4. Get Notesheet Document ID                                            │
│     └─> "notesheet" attribute (format: folderIndex#docIndex)            │
│                                                                          │
│  5. Generate PDF with Flying Saucer                                      │
│     └─> Original HTML + Supporting Docs Table + Comments                │
│     └─> Track View cell positions for annotations                       │
│                                                                          │
│  6. Checkout/Checkin with Annotation Preservation                        │
│     └─> Get existing annotations                                         │
│     └─> Filter out old ViewLinks annotations                            │
│     └─> Checkout document                                                │
│     └─> Upload new PDF                                                   │
│     └─> Checkin document                                                 │
│     └─> Restore filtered annotations                                     │
│                                                                          │
│  7. Add View Hyperlink Annotations                                       │
│     └─> Build annotation buffer with hyperlink coordinates              │
│     └─> Call NGOAddAnnotation API                                       │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Key Files

| File | Purpose |
|------|---------|
| `NoteSheetService.java` | Main service with createPdfNote, annotation buffer building |
| `NoteSheetController.java` | REST endpoints |
| `DocumentOpsService.java` | Checkout/checkin, annotation filtering |
| `document_list_template.html` | Supporting Documents table template |
| `document_row_template.html` | Table row template (empty View cell) |
| `test_createpdfnote.sh` | Test script for the endpoint |

---

## Coordinate System

### The Challenge
Three different coordinate systems must be aligned:
1. **Flying Saucer (HTML to PDF)**: Origin at top-left, Y increases downward, units in "dots"
2. **PDFBox**: Origin at bottom-left, Y increases upward, units in points
3. **OmniDocs Annotations**: Origin at **top-left**, Y increases **downward**, units in points

### Solution: Fixed Position Calculation
After extensive calibration, we use **fixed coordinates** instead of dynamic text extraction:

```java
// Calibrated constants (from manual annotation testing)
final int VIEW_X1 = 675;      // X position of View column
final int VIEW_WIDTH = 40;    // Width of hyperlink area
final int VIEW_HEIGHT = 15;   // Height of hyperlink area
final int FIRST_ROW_Y = 336;  // Y position of first data row
final int ROW_HEIGHT = 30;    // Spacing between rows

// Calculate position for each row
for (int rowIndex = 0; rowIndex < docIndices.size(); rowIndex++) {
    int x1 = VIEW_X1;
    int y1 = FIRST_ROW_Y + (rowIndex * ROW_HEIGHT);
    int x2 = x1 + VIEW_WIDTH;
    int y2 = y1 + VIEW_HEIGHT;
}
```

### Calibration Data
- User manually placed "TT" annotation on first View: `X1=675, Y1=334`
- PDFBox found View text at: `x=513, y=262`
- Page dimensions: 612x792 points (US Letter)

---

## OmniDocs Annotation Format

### Hyperlink Annotation Buffer Structure
```ini
[ViewLinksAnnotationHeader]
TotalAnnotations=2
NoOfHyperlinks=2

[ViewLinksHyperlink1]
X1=675
Y1=336
X2=715
Y2=351
Color=11141120
TimeOrder=2026,01,20,10,30,45
MouseSensitivity=1
AnnotationGroupID=ViewLinks
UserID=system
Rights=VM
HyperlinkName=View
HyperlinkURL=http://google.com
Height=-15
Width=0
Escapement=0
Orientation=0
Weight=400
Italic=0
Underlined=0
StrikeOut=0
CharSet=0
OutPrecision=0
ClipPrecision=0
Quality=1
PitchAndFamily=49
FontName=Arial
FontColor=11141120

[ViewLinksHyperlink2]
X1=675
Y1=366
...
```

### Key Fields
| Field | Description |
|-------|-------------|
| `X1, Y1, X2, Y2` | Bounding box coordinates (top-left origin) |
| `Color` | BGR color value (11141120 = blue) |
| `HyperlinkName` | Display text ("View") |
| `HyperlinkURL` | Target URL |
| `AnnotationGroupID` | Group name for filtering |
| `StrikeOut` | 0=normal, 1=strikethrough |

---

## Annotation Filtering

When updating the notesheet, we must:
1. **Preserve** user annotations (stamps, lines, text)
2. **Remove** old ViewLinks annotations (to avoid duplicates)

### Filter Logic (DocumentOpsService.java)
```java
private JsonNode filterViewHyperlinkAnnotations(JsonNode annotations) {
    // 1. If group name is "ViewLinks" → remove entire group
    // 2. If buffer contains "HyperlinkName=View" → remove those hyperlinks
    // 3. Recalculate TotalAnnotations and NoOfHyperlinks counts
}
```

---

## HTML Templates

### document_list_template.html
```html
<table border="1" cellpadding="5" style="width: 100%; border-collapse: collapse;">
  <colgroup>
    <col style="width: 10%" />
    <col style="width: 75%" />
    <col style="width: 15%" />
  </colgroup>
  <tr>
    <td colspan="3" style="background: #e0e0e0; font-weight: bold;">
      Supporting Documents
    </td>
  </tr>
  <tr style="background: #f0f0f0; font-weight: bold;">
    <td style="text-align: center;">S.No</td>
    <td>Document Name</td>
    <td style="text-align: center;">View</td>
  </tr>
  {{DOCUMENT_ROWS}}
</table>
```

### document_row_template.html
```html
<tr>
  <td style="text-align: center; width: 10%">{{SNO}}</td>
  <td style="word-wrap: break-word; word-break: break-all; width: 75%">{{DOCUMENT_NAME}}</td>
  <td style="text-align: center; width: 15%"></td>  <!-- Empty - hyperlink added as annotation -->
</tr>
```

**Note:** The View cell is intentionally empty. The "View" text appears as a hyperlink annotation, not HTML text.

---

## Testing

### Using the Test Script
```bash
# Default parameters
./test_createpdfnote.sh

# Custom parameters
./test_createpdfnote.sh e-Notes-000000000044-process 1

# With custom API URL
BALMER_API_URL=http://192.168.1.100:8089 ./test_createpdfnote.sh
```

### Test Script Flow
1. Login to get session ID
2. Call createpdfnote endpoint
3. Download PDF with annotations to `./tmp/`
4. Open PDF automatically (macOS)

### Manual Testing with curl
```bash
# Login
SESSION_ID=$(curl -s -X POST "http://localhost:8089/login-wmConnectCabinet" \
  -H "Content-Type: application/json" \
  -d '{"userName":"supervisor","password":"Sedin@123456"}' | \
  python3 -c "import sys,json; print(json.load(sys.stdin)['wmConnectResponse']['Participant']['SessionId'])")

# Create PDF Note
curl -X POST "http://localhost:8089/notesheet/createpdfnote?processInstanceId=e-Notes-000000000044-process&workitemId=1"

# Download with annotations
curl -H "sessionId: $SESSION_ID" \
  "http://localhost:8089/notesheet/downloadwithannotations?documentIndex=1668" \
  -o annotated.pdf
```

---

## Troubleshooting

### View hyperlinks appear in wrong position
- Check `FIRST_ROW_Y` and `ROW_HEIGHT` constants in `extractViewPositionsFromPdf()`
- The Supporting Documents table position may have shifted

### Strikethrough on View text
- Another annotation (line/stamp) is overlapping the View position
- Check for manual user annotations crossing the View column

### 404 on downloadwithannotations
- Verify `versionNo: ""` (empty string for latest version)
- Check server logs for download errors

### Duplicate View hyperlinks
- ViewLinks filtering may not be working
- Check `filterViewHyperlinkAnnotations()` in DocumentOpsService

---

## Configuration Properties

```properties
# application.properties
omnidocs.api.url=http://omnidocs-server/omnidocs
omnidocs.base.url=http://omnidocs-server/omnidocs
omnigetdocument.api.url=${omnidocs.base.url}/getDocumentStreamJSON
ibps.getWorkItem.url=http://ibps-server/ibps/getWorkItemData
notesheet.document.class=Notesheet Original
notesheet.temp.directory=${java.io.tmpdir}/notesheets
docs.viewer.base.url=http://viewer-server/docs/viewer
```

---

## Version History

| Date | Change |
|------|--------|
| 2026-01-20 | Initial implementation with fixed coordinate calculation |
| 2026-01-20 | Added downloadwithannotations endpoint |
| 2026-01-20 | Removed hyperlink styling from document names |

---

## Related APIs (OmniDocs)

| API | Purpose |
|-----|---------|
| `NGOGetDocumentStreamJSON` | Download document content |
| `NGOCheckOutDocument` | Lock document for editing |
| `NGOCheckInDocument` | Upload new version |
| `NGOGetAnnotation` | Get annotation groups |
| `NGOAddAnnotation` | Add annotation to document |
| `NGOGetFolderContentList` | List folder contents |
