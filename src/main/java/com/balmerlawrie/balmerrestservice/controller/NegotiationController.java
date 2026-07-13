package com.balmerlawrie.balmerrestservice.controller;

import com.balmerlawrie.balmerrestservice.service.NoteSheetService;
import com.balmerlawrie.balmerrestservice.service.SessionManager;
import com.balmerlawrie.balmerrestservice.service.WorkItemAttributesService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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

@RestController
@RequestMapping("/proc/negotiation")
@Tag(name = "Proc Negotiation", description = "Negotiation notesheet document operations")
public class NegotiationController {

    @Autowired
    private NoteSheetService noteSheetService;

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private WorkItemAttributesService workItemAttributesService;

    private static final String NEGOTIATION_COMMENTS_ATTRIBUTE = "Q_negotiation_comments";

    private final ObjectMapper mapper = new ObjectMapper();

    @Operation(summary = "Get Original Notesheet", description = "Retrieves the original notesheet document from the work item's attachment folder.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Request processed successfully. Check 'found' field to see if document was located.", content = @Content(mediaType = "application/json", examples = {
                    @ExampleObject(name = "Document Found", value = "{\"success\":true,\"found\":true,\"filePath\":\"/tmp/notesheets/abc123.pdf\",\"documentName\":\"notesheet.pdf\"}"),
                    @ExampleObject(name = "Document Not Found", value = "{\"success\":true,\"found\":false,\"message\":\"Notesheet Original document not found\"}")
            })),
            @ApiResponse(responseCode = "400", description = "Invalid parameters")
    })
    @GetMapping(value = "/getoriginal", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> getOriginalNotesheet(
            @Parameter(description = "Work Item ID", required = true) @RequestParam String workitemId,
            @Parameter(description = "Process Instance ID", required = true) @RequestParam String processInstanceId,
            @Parameter(description = "Session ID from login", required = true) @RequestHeader("sessionId") long sessionId) {

        if (workitemId == null || workitemId.isEmpty()) {
            return ResponseEntity.badRequest().body(createError("Missing workitemId parameter"));
        }
        if (processInstanceId == null || processInstanceId.isEmpty()) {
            return ResponseEntity.badRequest().body(createError("Missing processInstanceId parameter"));
        }

        JsonNode result = noteSheetService.getOriginalNotesheet(processInstanceId, workitemId, sessionId);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Get Document Annotations", description = "Retrieves annotations for a specific document from OmniDocs.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Request processed successfully.", content = @Content(mediaType = "application/json", examples = {
                    @ExampleObject(name = "Annotations Found", value = "{\"success\":true,\"found\":true,\"documentIndex\":\"1623\"}"),
                    @ExampleObject(name = "No Annotations", value = "{\"success\":true,\"found\":true,\"documentIndex\":\"1623\",\"annotations\":{}}")
            })),
            @ApiResponse(responseCode = "400", description = "Invalid parameters")
    })
    @GetMapping(value = "/getannotations", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> getAnnotations(
            @Parameter(description = "Document Index", required = true) @RequestParam String documentIndex,
            @Parameter(description = "Session ID from login", required = true) @RequestHeader("sessionId") long sessionId) {

        if (documentIndex == null || documentIndex.isEmpty()) {
            return ResponseEntity.badRequest().body(createError("Missing documentIndex parameter"));
        }

        JsonNode result = noteSheetService.getAnnotations(documentIndex, sessionId);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Set Document Annotations", description = "Applies annotations to a specific document using NGOAddAnnotation.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Request processed successfully.", content = @Content(mediaType = "application/json", examples = {
                    @ExampleObject(name = "Success", value = "{\"success\":true,\"processedCount\":1,\"results\":[{\"groupName\":\"padmin\",\"status\":\"Success\"}]}")
            })),
            @ApiResponse(responseCode = "400", description = "Invalid parameters")
    })
    @PostMapping(value = "/setannotations", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> setAnnotations(
            @Parameter(description = "Target Document Index", required = true) @RequestParam String documentIndex,
            @Parameter(description = "Annotations JSON", required = true) @RequestBody JsonNode annotations,
            @Parameter(description = "Session ID from login", required = true) @RequestHeader("sessionId") long sessionId) {

        if (documentIndex == null || documentIndex.isEmpty()) {
            return ResponseEntity.badRequest().body(createError("Missing documentIndex parameter"));
        }

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

    @Operation(summary = "Get Work Item Comments", description = "Retrieves Negotiation comments history (from the '" + NEGOTIATION_COMMENTS_ATTRIBUTE + "' work item attribute) for a work item.")
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

        if (workitemId == null || workitemId.isEmpty() || processInstanceId == null || processInstanceId.isEmpty()) {
            return ResponseEntity.badRequest().body(createError("Missing parameters"));
        }

        JsonNode attributesResponse = workItemAttributesService.fetchWorkItemAttributes(processInstanceId, workitemId, sessionId);
        JsonNode result = extractNegotiationComments(attributesResponse);
        return ResponseEntity.ok(result);
    }

    private JsonNode extractNegotiationComments(JsonNode attributesResponse) {
        JsonNode output = attributesResponse.path("WMFetchWorkItemAttributes_Output");
        if (output.isMissingNode()) {
            output = attributesResponse; // Handle unwrapped
        }

        JsonNode attributes = output.path("Attributes");

        ObjectNode result = mapper.createObjectNode();
        ArrayNode commentsList = result.putArray("comments");

        if (attributes.isMissingNode()) {
            result.put("success", false);
            result.put("error", "Failed to fetch work item attributes");
            result.put("count", 0);
            return result;
        }

        JsonNode negotiationCommentsNode = findFieldIgnoreCase(attributes, NEGOTIATION_COMMENTS_ATTRIBUTE);
        if (negotiationCommentsNode != null) {
            appendCommentsFromNode(negotiationCommentsNode, commentsList);
        }

        result.put("success", true);
        result.put("count", commentsList.size());
        return result;
    }

    private void appendCommentsFromNode(JsonNode node, ArrayNode commentsList) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }

        if (node.isArray()) {
            for (JsonNode item : node) {
                appendCommentsFromNode(item, commentsList);
            }
            return;
        }

        if (node.isTextual()) {
            String text = node.asText("").trim();
            if ((text.startsWith("{") && text.endsWith("}")) || (text.startsWith("[") && text.endsWith("]"))) {
                try {
                    JsonNode parsed = mapper.readTree(text);
                    appendCommentsFromNode(parsed, commentsList);
                } catch (Exception ignored) {
                    // Non-JSON text; ignore
                }
            }
            return;
        }

        if (!node.isObject()) {
            return;
        }

        String userName = getValue(node, "username");
        String userId = getValue(node, "userid");
        String dateTime = getValue(node, "datetime");
        String comments = getValue(node, "comments");
        String stage = getValue(node, "stagename");
        String status = getValue(node, "email");
        String actionTaken = getValue(node, "actiontaken");
        if (actionTaken == null || actionTaken.isEmpty()) {
            actionTaken = status;
        }

        boolean looksLikeComment = !(isBlank(userName) && isBlank(dateTime) && isBlank(comments) && isBlank(stage)
                && isBlank(actionTaken));
        if (looksLikeComment) {
            ObjectNode comment = mapper.createObjectNode();
            comment.put("userName", userName);
            comment.put("userId", userId);
            comment.put("dateTime", dateTime);
            comment.put("comments", comments);
            comment.put("stage", stage);
            comment.put("status", status);
            comment.put("actionTaken", actionTaken);
            commentsList.add(comment);
        }

        java.util.Iterator<java.util.Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            java.util.Map.Entry<String, JsonNode> field = fields.next();
            String name = field.getKey();
            JsonNode value = field.getValue();
            if (name == null || value == null || !value.isContainerNode()) {
                continue;
            }
            appendCommentsFromNode(value, commentsList);
        }
    }

    private String getValue(JsonNode parent, String key) {
        if (parent == null || !parent.isObject()) {
            return "";
        }

        JsonNode node = findFieldIgnoreCase(parent, key);
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }

        JsonNode emptyKeyValue = node.path("");
        if (!emptyKeyValue.isMissingNode() && !emptyKeyValue.isNull()) {
            return emptyKeyValue.asText("");
        }

        JsonNode contentValue = node.path("content");
        if (!contentValue.isMissingNode() && !contentValue.isNull()) {
            return contentValue.asText("");
        }

        JsonNode textValue = node.path("#text");
        if (!textValue.isMissingNode() && !textValue.isNull()) {
            return textValue.asText("");
        }

        if (node.isTextual() || node.isNumber() || node.isBoolean()) {
            return node.asText("");
        }

        return "";
    }

    private JsonNode findFieldIgnoreCase(JsonNode node, String targetKey) {
        if (node == null || !node.isObject() || targetKey == null) {
            return null;
        }
        java.util.Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (targetKey.equalsIgnoreCase(name)) {
                return node.path(name);
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    @Operation(summary = "Get Notesheet Document ID", description = "Retrieves the notesheet document ID from work item attributes.")
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

        if (workitemId == null || workitemId.isEmpty() || processInstanceId == null || processInstanceId.isEmpty()) {
            return ResponseEntity.badRequest().body(createError("Missing required parameters"));
        }

        JsonNode result = noteSheetService.getNotesheet(processInstanceId, workitemId, sessionId);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Create PDF Note", description = "Creates a PDF from the original notesheet with appended comments and updates the notesheet document.")
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
            @Parameter(description = "Work Item ID", required = true) @RequestParam String workitemId,
            @Parameter(description = "Process Instance ID", required = true) @RequestParam String processInstanceId,
            @Parameter(description = "Optional Session ID from login. If not provided, uses service account.") @RequestHeader(value = "sessionId", required = false) Long providedSessionId) {

        if (workitemId == null || workitemId.isEmpty() || processInstanceId == null || processInstanceId.isEmpty()) {
            return ResponseEntity.badRequest().body(createError("Missing required parameters: workitemId, processInstanceId"));
        }

        Long sessionId = sessionManager.getServiceSession();
        if (sessionId == null) {
            return ResponseEntity.status(401).body(createError("Failed to establish service session"));
        }

        JsonNode result = noteSheetService.createPdfNote(processInstanceId, workitemId, sessionId);

        if (!result.path("success").asBoolean(true)) {
            String error = result.path("error").asText("").toLowerCase();
            String details = result.path("details").asText("").toLowerCase();
            if (error.contains("invalid session") || error.contains("401") ||
                details.contains("invalid session") || details.contains("401")) {
                sessionManager.invalidateSessionById(sessionId);
                sessionId = sessionManager.getServiceSession();
                if (sessionId != null) {
                    result = noteSheetService.createPdfNote(processInstanceId, workitemId, sessionId);
                }
            }
        }

        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Download Document with Annotations", description = "Downloads a document with all annotations burned into the PDF.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "PDF with annotations", content = @Content(mediaType = "application/pdf")),
            @ApiResponse(responseCode = "400", description = "Invalid parameters"),
            @ApiResponse(responseCode = "404", description = "Document not found or failed to process")
    })
    @GetMapping(value = "/downloadwithannotations", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadWithAnnotations(
            @Parameter(description = "Document Index", required = true) @RequestParam String documentIndex,
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

        return ResponseEntity.ok().headers(headers).body(pdfWithAnnotations);
    }

    private JsonNode createError(String message) {
        ObjectNode error = mapper.createObjectNode();
        error.put("success", false);
        error.put("error", message);
        return error;
    }
}
