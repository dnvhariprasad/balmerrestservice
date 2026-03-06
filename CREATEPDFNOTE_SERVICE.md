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
- `processInstanceId` (query): e.g., `e-Notes-000000000093-process`
- `workitemId` (query): e.g., `1`
- `sessionId` (header, optional): If not provided or 0, falls back to service account. Session expiry is auto-retried once.

**Response:**
```json
{
  "success": true,
  "originalDocIndex": "1669",
  "notedocumentIndex": "1668",
  "newVersion": "1.5",
  "pdfPath": "/tmp/notesheets/newNoteContent-uuid.pdf",
  "commentsPath": "/tmp/comments/comments-uuid.json",
  "annotationsPreserved": true,
  "viewHyperlinksAdded": 0
}
```

### GET /notesheet/downloadwithannotations
Downloads a document with annotations burned into the PDF.

**Parameters:**
- `documentIndex` (query): Document index
- `sessionId` (header): Session ID from login

**Response:** PDF file with annotations rendered

---

### Variant: createPdfNoteWithExtraSection
Used by the Legal endpoint (`/legal/printform`) to insert an extra HTML section (case details)
before the supporting documents table. The extra section HTML is sanitized through
`sanitizeHtmlForPdf()` before rendering. Only applied when the `processInstanceId` contains "legal".

### Note: View Hyperlinks Currently Disabled
Step 7 (adding View hyperlink annotations via OmniDocs) is currently commented out in the implementation.
`viewHyperlinksAdded` will always be 0.

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
│  5. Generate PDF with Aspose.PDF                                         │
│     └─> Original HTML + Extra Section (legal only) + Supporting Docs    │
│         Table + Comments                                                 │
│     └─> HTML is sanitized via sanitizeHtmlForPdf() before rendering     │
│     └─> Documents starting with "notesheet" are skipped                 │
│     └─> Aspose converts HTML to PDF using ScaleToPageWidth layout       │
│                                                                          │
│  6. Extract View Cell Positions                                          │
│     └─> Aspose TextFragmentAbsorber searches for "View" text           │
│     └─> Returns page number + coordinates for each View cell            │
│                                                                          │
│  7. Checkout/Checkin with Annotation Preservation                        │
│     └─> Get existing annotations                                         │
│     └─> Filter out old ViewLinks annotations                            │
│     └─> Checkout document                                                │
│     └─> Upload new PDF                                                   │
│     └─> Checkin document                                                 │
│     └─> Restore filtered annotations                                     │
│                                                                          │
│  8. Add View Hyperlink Annotations (currently disabled)                  │
│     └─> Build annotation buffer with hyperlink coordinates              │
│     └─> Call NGOAddAnnotation API                                       │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Key Files

| File | Purpose |
|------|---------|
| `NoteSheetService.java` | Main service: PDF generation, HTML sanitization, View position extraction |
| `NoteSheetController.java` | REST endpoints |
| `DocumentOpsService.java` | Checkout/checkin, annotation filtering |
| `document_list_template.html` | Supporting Documents table template |
| `document_row_template.html` | Table row template (empty View cell) |
| `comment_template.html` | Comments section wrapper template |
| `comment_row_template.html` | Individual comment row template |
| `legal_printform.html` | Legal case details extra section template |
| `test_createpdfnote.sh` | Test script for the endpoint |

---

## PDF Generation (Aspose.PDF)

### Library
PDF rendering uses **Aspose.PDF for Java** (replaces the earlier Flying Saucer + iText stack).

### Headless Mode Requirement
Aspose.PDF internally calls `java.awt.GraphicsEnvironment.getScreenDevices()`, which fails when
`java.awt.headless=true` (Spring Boot's default). The application startup explicitly sets:
```java
System.setProperty("java.awt.headless", "false");
SpringApplication app = new SpringApplication(BalmerrestserviceApplication.class);
app.setHeadless(false);
```

### HTML-to-PDF Conversion
```java
HtmlLoadOptions htmlOptions = new HtmlLoadOptions();
htmlOptions.getPageInfo().setWidth(PageSize.getA4().getWidth());
htmlOptions.getPageInfo().setHeight(PageSize.getA4().getHeight());
htmlOptions.getPageInfo().getMargin().setLeft(40);
htmlOptions.getPageInfo().getMargin().setRight(40);
htmlOptions.getPageInfo().getMargin().setTop(30);
htmlOptions.getPageInfo().getMargin().setBottom(30);
htmlOptions.setPageLayoutOption(HtmlPageLayoutOption.ScaleToPageWidth);
```

- **`ScaleToPageWidth`**: Scales content to fit within the A4 page width. This prevents content
  overflow from wide tables or Word-pasted HTML.
- Page margins are set on the Aspose `PageInfo` (not CSS body margins, which are zeroed out to
  avoid double margins).

### HTML Sanitization (`sanitizeHtmlForPdf`)
Before rendering, all HTML content (original notesheet + extra sections) passes through
`sanitizeHtmlForPdf()` which fixes Word/Froala-generated HTML that would overflow the PDF page:

| Rule | What it fixes | How |
|------|--------------|-----|
| 1a-c | Table widths set in px/pt/cm/in | Replaces with `width: 100%` |
| 2a-c | Cell/column absolute widths | Removes, letting `table-layout:auto` distribute |
| 3a-d | Word-specific CSS (`mso-*`, conditional comments, XML tags, `Mso*` classes) | Strips entirely |
| 4a-b | Image overflow (px widths, HTML width/height attrs) | Replaces with `max-width:100%; width:auto` |
| 5a-b | `min-width` absolute values, `position:absolute` on tables | Removes |
| 5c | Large `text-indent` (>40px, e.g. Word's 768px) | Removes values >40px, preserves <=40px |
| 5d | Negative `margin-left`/`margin-right` | Removes |
| 5e | Shorthand `margin` with negative sides | Zeros out negative sides, preserves positive |
| 6a | Chains of 3+ `&nbsp;` (Word uses up to 72 for alignment) | Collapses to single space |
| 6b | Remaining `&nbsp;` pairs | Replaces with ` &nbsp;` to allow line breaks |
| 7 | Empty `style=""` attributes left after cleanup | Removes |

---

## Coordinate System

### The Challenge
Two coordinate systems must be aligned:
1. **Aspose.PDF**: Origin at bottom-left, Y increases upward, units in points
2. **OmniDocs Annotations**: Origin at top-left, Y increases downward, units in points

### Solution: Aspose TextFragmentAbsorber
View cell positions are extracted dynamically from the generated PDF using Aspose's text search:

```java
TextFragmentAbsorber absorber = new TextFragmentAbsorber("View");
document.getPages().accept(absorber);

for (TextFragment fragment : absorber.getTextFragments()) {
    double x = fragment.getRectangle().getLLX();  // Lower-left X
    double y = fragment.getRectangle().getLLY();  // Lower-left Y (bottom-up)
    int pageNum = fragment.getPage().getNumber();
    double pageHeight = fragment.getPage().getRect().getHeight();

    // Convert Aspose (bottom-up) to OmniDocs (top-down)
    double omniY = pageHeight - y;
}
```

This replaces the old fixed-coordinate approach (`FIRST_ROW_Y + rowIndex * ROW_HEIGHT`) which
broke whenever the table position shifted.

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
HyperlinkURL=http://server:port/balmerrestservice/docs/viewer?docIndex=1670
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
| `HyperlinkURL` | Target URL (uses `docs.viewer.base.url` config) |
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
  <td style="text-align: center;">{{SNO}}</td>
  <td>{{DOCUMENT_NAME}}</td>
  <td style="text-align: center;"></td>  <!-- Empty - hyperlink added as annotation -->
</tr>
```

**Note:** The View cell is intentionally empty. The "View" text appears as a hyperlink annotation, not HTML text.

### legal_printform.html
Used by `createPdfNoteWithExtraSection` for legal process cases. Contains `{{PLACEHOLDER}}`
tokens (e.g., `{{CASEID}}`, `{{CASETITLE}}`, `{{JURISDICTION}}`) that are replaced with work
item attributes at runtime.

---

## Testing

### Using the Test Script
```bash
# Default parameters
./test_createpdfnote.sh

# Custom parameters
./test_createpdfnote.sh e-Notes-000000000093-process 1

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
curl -X POST "http://localhost:8089/notesheet/createpdfnote?processInstanceId=e-Notes-000000000093-process&workitemId=1"

# Download with annotations
curl -H "sessionId: $SESSION_ID" \
  "http://localhost:8089/notesheet/downloadwithannotations?documentIndex=1668" \
  -o annotated.pdf
```

---

## Troubleshooting

### HeadlessException on startup
Aspose.PDF requires `java.awt.headless=false`. Ensure `BalmerrestserviceApplication.java` sets
this before Spring context starts. See "Headless Mode Requirement" section above.

### PDF content cut off or overflowing
Word-pasted HTML is the usual cause. Check the debug HTML file saved in the temp directory
(`debug-*.html`) and look for:
- Large `text-indent` values (>40px)
- Negative margins (`margin: 0px -51px ...`)
- Chains of `&nbsp;` characters
- Absolute table/cell widths

These should be caught by `sanitizeHtmlForPdf()`. If new patterns appear, add rules there.

### Aspose evaluation watermark
Without a license, Aspose adds a red watermark to generated PDFs. To remove it, place a valid
license file and call `new License().setLicense(licensePath)` before PDF generation.

### View hyperlinks appear in wrong position
- View position extraction uses `TextFragmentAbsorber` to search for "View" text in the PDF
- If the Supporting Documents table changes layout, positions update automatically
- Check `extractViewPositionsFromPdf()` in `NoteSheetService.java`

### Duplicate View hyperlinks
- ViewLinks filtering may not be working
- Check `filterViewHyperlinkAnnotations()` in DocumentOpsService

---

## Configuration Properties

```properties
# application.properties — key settings for PDF generation
ibps.base.url=http://${ibps.server.host}:${ibps.server.port}/iBPSRestFulWebServices/ibps/Restful/fosasoft
ibps.cabinet.name=fosasoft
omnidocs.api.url=${omnidocs.base.url}/executeAPIJSON
omniadddocument.api.url=${omnidocs.base.url}/addDocumentJSON
omnigetdocument.api.url=${omnidocs.base.url}/getDocumentStreamJSON
notesheet.document.class=notesheet_original
notesheet.temp.directory=./tmp/notesheets
docs.viewer.base.url=http://${docs.viewer.server.host}:${docs.viewer.server.port}${docs.viewer.context.path}/docs/viewer
service.account.username=supervisor
service.account.password=JqVxhLUw0FWr1qD9YNpsvQ==  # AES-encrypted (key: BalmerLawrie2025)
```

---

## Key Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| Aspose.PDF for Java | 24.12 | HTML-to-PDF conversion, text extraction, annotation rendering |
| docx4j | 6.1.2 / 8.3.7 | Word document processing |
| Apache POI | 5.2.5 | Word/Excel file handling |
| Jackson XML | — | XML-to-JSON conversion |

**Removed libraries** (replaced by Aspose.PDF):
- Flying Saucer (HTML-to-PDF) — replaced by Aspose HtmlLoadOptions
- iText 2.1.7 — replaced by Aspose PDF manipulation
- OpenHTMLtoPDF — replaced by Aspose
- PDFBox 2.0.29 — replaced by Aspose TextFragmentAbsorber

---

## Version History

| Date | Change |
|------|--------|
| 2026-01-20 | Initial implementation with Flying Saucer + fixed coordinate calculation |
| 2026-01-20 | Added downloadwithannotations endpoint |
| 2026-01-20 | Removed hyperlink styling from document names |
| 2026-03-05 | Migrated from Flying Saucer/iText/PDFBox to Aspose.PDF |
| 2026-03-05 | Replaced fixed coordinates with Aspose TextFragmentAbsorber |
| 2026-03-05 | Added sanitizeHtmlForPdf() for Word-pasted HTML overflow fixes |
| 2026-03-05 | Fixed headless mode: set java.awt.headless=false for Aspose |
| 2026-03-05 | Added legal printform extra section support |

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
