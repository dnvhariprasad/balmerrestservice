package com.balmerlawrie.balmerrestservice.controller;

import com.balmerlawrie.balmerrestservice.service.DocumentOpsService;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for document operations.
 */
@RestController
@RequestMapping("/docs")
@Tag(name = "Document Operations", description = "Document checkout, checkin, and annotation operations")
public class DocumentOpsController {

    @Autowired
    private DocumentOpsService documentOpsService;

    private final ObjectMapper mapper = new ObjectMapper();

    @Operation(summary = "Checkout, Checkin with Annotations", description = "Performs a checkout/checkin cycle while preserving annotations. "
            +
            "1. Backs up existing annotations to /tmp folder. " +
            "2. Checks out the document (undoes if already checked out). " +
            "3. Checks in with new content from the provided path. " +
            "4. Restores the backed up annotations.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Operation completed", content = @Content(mediaType = "application/json", examples = {
                    @ExampleObject(name = "Success", value = "{\n" +
                            "  \"success\": true,\n" +
                            "  \"documentIndex\": \"1668\",\n" +
                            "  \"newVersion\": \"2.0\",\n" +
                            "  \"annotationsBackedUp\": true,\n" +
                            "  \"annotationsRestored\": true,\n" +
                            "  \"annotationBackupPath\": \"/tmp/annotations/uuid_annotations_1668.json\"\n" +
                            "}"),
                    @ExampleObject(name = "Error", value = "{\n" +
                            "  \"success\": false,\n" +
                            "  \"error\": \"Content file not found: /path/to/file.pdf\"\n" +
                            "}")
            })),
            @ApiResponse(responseCode = "400", description = "Invalid parameters")
    })
    @PostMapping(value = "/checkoutcheckinwithanno", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> checkoutCheckinWithAnnotations(
            @Parameter(description = "Document Index in OmniDocs", required = true, example = "1668") @RequestParam String documentIndex,

            @Parameter(description = "Absolute path to the new content file", required = true, example = "/Users/hariprasad/Workspace/balmerrestservice/tmp/notesheets/sample.pdf") @RequestParam String contentPath,

            @Parameter(description = "Session ID from login", required = true) @RequestHeader("sessionId") long sessionId) {

        if (documentIndex == null || documentIndex.isEmpty()) {
            return ResponseEntity.badRequest().body(createError("Missing documentIndex parameter"));
        }

        if (contentPath == null || contentPath.isEmpty()) {
            return ResponseEntity.badRequest().body(createError("Missing contentPath parameter"));
        }

        JsonNode result = documentOpsService.checkoutCheckinWithAnnotations(documentIndex, contentPath, sessionId);
        return ResponseEntity.ok(result);
    }

    private JsonNode createError(String message) {
        ObjectNode error = mapper.createObjectNode();
        error.put("success", false);
        error.put("error", message);
        return error;
    }
}
