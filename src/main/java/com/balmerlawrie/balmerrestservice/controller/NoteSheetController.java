package com.balmerlawrie.balmerrestservice.controller;

import com.balmerlawrie.balmerrestservice.service.NoteSheetService;
import com.balmerlawrie.balmerrestservice.service.SessionManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for Notesheet document operations.
 */
@RestController
@RequestMapping("/notesheet")
@Tag(name = "Notesheet", description = "Notesheet document operations")
public class NoteSheetController {

        @Autowired
        private NoteSheetService noteSheetService;

        @Autowired
        private SessionManager sessionManager;

        private final ObjectMapper mapper = new ObjectMapper();

        @Operation(summary = "Get Original Notesheet", description = "Retrieves the original notesheet document from the work item's attachment folder. "
                        +
                        "Returns the file path where the document is saved temporarily.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Request processed successfully. Check 'found' field to see if document was located.", content = @Content(mediaType = "application/json", examples = {
                                        @ExampleObject(name = "Document Found", value = "{\"success\":true,\"found\":true,\"filePath\":\"/tmp/notesheets/abc123.pdf\",\"documentName\":\"notesheet.pdf\"}"),
                                        @ExampleObject(name = "Document Not Found", value = "{\"success\":true,\"found\":false,\"message\":\"Notesheet Original document not found\"}")
                        })),
                        @ApiResponse(responseCode = "400", description = "Invalid parameters")
        })
        @GetMapping(value = "/getoriginal", produces = MediaType.APPLICATION_JSON_VALUE)
        public ResponseEntity<JsonNode> getOriginalNotesheet(
                        @Parameter(description = "Work Item ID", required = true, example = "e-Notes-000000000005-process") @RequestParam String workitemId,

                        @Parameter(description = "Process Instance ID", required = true, example = "e-Notes-000000000005-process") @RequestParam String processInstanceId,

                        @Parameter(description = "Session ID from login", required = true) @RequestHeader("sessionId") long sessionId) {

                if (workitemId == null || workitemId.isEmpty()) {
                        return ResponseEntity.badRequest().body(
                                        createError("Missing workitemId parameter"));
                }

                if (processInstanceId == null || processInstanceId.isEmpty()) {
                        return ResponseEntity.badRequest().body(
                                        createError("Missing processInstanceId parameter"));
                }

                JsonNode result = noteSheetService.getOriginalNotesheet(processInstanceId, workitemId, sessionId);

                // Always return 200 OK - check 'success' and 'found' fields for status
                return ResponseEntity.ok(result);
        }

        @Operation(summary = "Get Document Annotations", description = "Retrieves annotations for a specific document from OmniDocs. "
                        +
                        "Returns a list of annotations and the path to the temporary JSON file containing them.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Request processed successfully.", content = @Content(mediaType = "application/json", examples = {
                                        @ExampleObject(name = "Annotations Found", value = "{\"success\":true,\"found\":true,\"documentIndex\":\"1623\",\"filePath\":\"/tmp/annotations/88afbfd4-492c-4755-ab54-cf0826044f4c_annotations_1623.json\",\"annotations\":{\"AnnotationGroup\":{\"LoginUserRights\":\"111111111111\",\"AnnotationBuffer\":\"TotalAnnotations=1...\",\"Owner\":\"padmin\",\"CreationDateTime\":\"2025-12-11 15:51:58.85\",\"AnnotGroupIndex\":\"64\",\"AnnotGroupName\":\"padmin\",\"PageNo\":\"1\",\"UserInfo\":{\"Type\":\"U\",\"FamilyName\":\"user\",\"PersonalName\":\"Admin\",\"Index\":\"4\",\"Name\":\"padmin\"},\"FinalizedBy\":\"0\",\"AccessType\":\"I\",\"AnnotationType\":\"A\",\"OwnerIndex\":\"4\"}}}"),
                                        @ExampleObject(name = "No Annotations", value = "{\"success\":true,\"found\":true,\"documentIndex\":\"1623\",\"filePath\":\"...\",\"annotations\":{}}")
                        })),
                        @ApiResponse(responseCode = "400", description = "Invalid parameters")
        })
        @GetMapping(value = "/getannotations", produces = MediaType.APPLICATION_JSON_VALUE)
        public ResponseEntity<JsonNode> getAnnotations(
                        @Parameter(description = "Document Index", required = true, example = "1646") @RequestParam String documentIndex,
                        @Parameter(description = "Session ID from login", required = true) @RequestHeader("sessionId") long sessionId) {

                if (documentIndex == null || documentIndex.isEmpty()) {
                        return ResponseEntity.badRequest().body(
                                        createError("Missing documentIndex parameter"));
                }

                JsonNode result = noteSheetService.getAnnotations(documentIndex, sessionId);

                return ResponseEntity.ok(result);
        }

        @Operation(summary = "Set Document Annotations", description = "Applies annotations to a specific document using NGOAddAnnotation. "
                        +
                        "Input should be the 'annotations' object from the getAnnotations response.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Request processed successfully.", content = @Content(mediaType = "application/json", examples = {
                                        @ExampleObject(name = "Success", value = "{\"success\":true,\"processedCount\":1,\"results\":[{\"groupName\":\"padmin\",\"status\":\"Success\"}]}")
                        })),
                        @ApiResponse(responseCode = "400", description = "Invalid parameters")
        })
        @PostMapping(value = "/setannotations", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
        public ResponseEntity<JsonNode> setAnnotations(
                        @Parameter(description = "Target Document Index", required = true, example = "1591") @RequestParam String documentIndex,
                        @Parameter(description = "Annotations JSON", required = true) @RequestBody JsonNode annotations,
                        @Parameter(description = "Session ID from login", required = true) @RequestHeader("sessionId") long sessionId) {

                if (documentIndex == null || documentIndex.isEmpty()) {
                        return ResponseEntity.badRequest().body(
                                        createError("Missing documentIndex parameter"));
                }

                // If body contains "annotations" wrapper (from getAnnotations output), unwrap
                // it
                if (annotations.has("annotations")) {
                        annotations = annotations.path("annotations");
                }

                JsonNode result = noteSheetService.setAnnotations(documentIndex, annotations, sessionId);

                return ResponseEntity.ok(result);
        }

        @Operation(summary = "Debug Work Item", description = "Dumps work item details for debugging.")
        @GetMapping(value = "/debug/workitem", produces = MediaType.APPLICATION_JSON_VALUE)
        public ResponseEntity<JsonNode> debugWorkItem(
                        @RequestParam String workitemId,
                        @RequestParam String processInstanceId,
                        @RequestHeader("sessionId") long sessionId) {

                String filePath = noteSheetService.dumpWorkItemDetails(processInstanceId, workitemId, sessionId);

                ObjectNode result = mapper.createObjectNode();
                result.put("success", true);
                result.put("dumpFile", filePath);

                return ResponseEntity.ok(result);
        }

        @Operation(summary = "Get Work Item Comments", description = "Retrieves comments history for a work item.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Request processed successfully.", content = @Content(mediaType = "application/json", examples = {
                                        @ExampleObject(name = "Comments Found", value = "{\"success\":true,\"count\":2,\"comments\":[{\"userName\":\"keerthi\",\"dateTime\":\"2025-12-12\",\"comments\":\"updated\",\"stage\":\"Initiator\"}]}")
                        })),
                        @ApiResponse(responseCode = "400", description = "Invalid parameters")
        })
        @GetMapping(value = "/getcomments", produces = MediaType.APPLICATION_JSON_VALUE)
        public ResponseEntity<JsonNode> getComments(
                        @RequestParam String workitemId,
                        @RequestParam String processInstanceId,
                        @RequestHeader("sessionId") long sessionId) {

                if (workitemId == null || workitemId.isEmpty() || processInstanceId == null
                                || processInstanceId.isEmpty()) {
                        return ResponseEntity.badRequest().body(createError("Missing parameters"));
                }

                JsonNode result = noteSheetService.getComments(processInstanceId, workitemId, sessionId);
                return ResponseEntity.ok(result);
        }

        @Operation(summary = "Get Notesheet Document ID", description = "Retrieves the notesheet document ID from work item attributes. "
                        + "Parses the 'notesheet' attribute (format: folder#docid) and returns the document index.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Request processed successfully.", content = @Content(mediaType = "application/json", examples = {
                                        @ExampleObject(name = "Found", value = "{\"success\":true,\"found\":true,\"documentIndex\":\"1664\",\"folderIndex\":\"2325\"}"),
                                        @ExampleObject(name = "Not Found", value = "{\"success\":true,\"found\":false,\"message\":\"No 'notesheet' attribute found in work item\"}")
                        })),
                        @ApiResponse(responseCode = "400", description = "Invalid parameters")
        })
        @GetMapping(value = "/getnotesheet", produces = MediaType.APPLICATION_JSON_VALUE)
        public ResponseEntity<JsonNode> getNotesheet(
                        @Parameter(description = "Work Item ID", required = true) @RequestParam String workitemId,
                        @Parameter(description = "Process Instance ID", required = true) @RequestParam String processInstanceId,
                        @Parameter(description = "Session ID from login", required = true) @RequestHeader("sessionId") long sessionId) {

                if (workitemId == null || workitemId.isEmpty() || processInstanceId == null
                                || processInstanceId.isEmpty()) {
                        return ResponseEntity.badRequest().body(createError("Missing required parameters"));
                }

                JsonNode result = noteSheetService.getNotesheet(processInstanceId, workitemId, sessionId);
                return ResponseEntity.ok(result);
        }

        @Operation(summary = "Create PDF Note", description = "Creates a PDF from the original notesheet with appended comments and updates the notesheet document. "
                        + "Flow: 1) Get original notesheet, 2) Get comments, 3) Get notesheet document ID, 4) Generate PDF with comments, 5) Update notesheet preserving annotations. "
                        + "Uses service account credentials from configuration for automatic authentication.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "PDF note created successfully", content = @Content(mediaType = "application/json", examples = {
                                        @ExampleObject(name = "Success", value = "{\n"
                                                        + "  \"success\": true,\n"
                                                        + "  \"originalDocIndex\": \"1669\",\n"
                                                        + "  \"notedocumentIndex\": \"1668\",\n"
                                                        + "  \"newVersion\": \"1.5\",\n"
                                                        + "  \"pdfPath\": \"/tmp/newNoteContent-uuid.pdf\",\n"
                                                        + "  \"commentsPath\": \"/tmp/comments-uuid.json\",\n"
                                                        + "  \"annotationsPreserved\": true\n"
                                                        + "}")
                        })),
                        @ApiResponse(responseCode = "400", description = "Invalid parameters"),
                        @ApiResponse(responseCode = "401", description = "Authentication failed")
        })
        @PostMapping(value = "/createpdfnote", produces = MediaType.APPLICATION_JSON_VALUE)
        public ResponseEntity<JsonNode> createPdfNote(
                        @Parameter(description = "Work Item ID", required = true, example = "1") @RequestParam String workitemId,
                        @Parameter(description = "Process Instance ID", required = true, example = "e-Notes-000000000008-process") @RequestParam String processInstanceId) {

                if (workitemId == null || workitemId.isEmpty() || processInstanceId == null
                                || processInstanceId.isEmpty()) {
                        return ResponseEntity.badRequest().body(
                                        createError("Missing required parameters: workitemId, processInstanceId"));
                }

                // Get session using service account credentials from config
                Long sessionId = sessionManager.getServiceSession();
                if (sessionId == null) {
                        return ResponseEntity.status(401)
                                        .body(createError("Failed to establish service session"));
                }

                JsonNode result = noteSheetService.createPdfNote(processInstanceId, workitemId, sessionId);
                return ResponseEntity.ok(result);
        }

        @Operation(summary = "Download Document with Annotations", description = "Downloads a document with all annotations burned into the PDF. "
                        + "This renders OmniDocs annotations (hyperlinks, lines, stamps, etc.) directly onto the PDF for offline viewing.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "PDF with annotations", content = @Content(mediaType = "application/pdf")),
                        @ApiResponse(responseCode = "400", description = "Invalid parameters"),
                        @ApiResponse(responseCode = "404", description = "Document not found or failed to process")
        })
        @GetMapping(value = "/downloadwithannotations", produces = MediaType.APPLICATION_PDF_VALUE)
        public ResponseEntity<byte[]> downloadWithAnnotations(
                        @Parameter(description = "Document Index", required = true, example = "1664") @RequestParam String documentIndex,
                        @Parameter(description = "Session ID from login", required = true) @RequestHeader("sessionId") long sessionId) {

                if (documentIndex == null || documentIndex.isEmpty()) {
                        return ResponseEntity.badRequest().build();
                }

                byte[] pdfWithAnnotations = noteSheetService.downloadDocumentWithAnnotations(documentIndex, sessionId);

                if (pdfWithAnnotations == null || pdfWithAnnotations.length == 0) {
                        return ResponseEntity.notFound().build();
                }

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_PDF);
                headers.setContentDispositionFormData("attachment", "document_" + documentIndex + "_annotated.pdf");

                return ResponseEntity.ok()
                                .headers(headers)
                                .body(pdfWithAnnotations);
        }

        private JsonNode createError(String message) {
                ObjectNode error = mapper.createObjectNode();
                error.put("success", false);
                error.put("error", message);
                return error;
        }
}
