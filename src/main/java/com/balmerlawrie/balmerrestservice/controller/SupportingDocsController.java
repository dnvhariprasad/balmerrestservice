package com.balmerlawrie.balmerrestservice.controller;

import com.balmerlawrie.balmerrestservice.service.SupportingDocsService;
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
 * Controller for supporting documents operations.
 * Provides endpoints to list documents attached to work items.
 */
@RestController
@RequestMapping("/supportingdocs")
@Tag(name = "Supporting Documents", description = "Operations for managing supporting documents attached to work items")
public class SupportingDocsController {

    @Autowired
    private SupportingDocsService supportingDocsService;

    private final ObjectMapper mapper = new ObjectMapper();

    @Operation(
        summary = "List Supporting Documents",
        description = "Retrieves all supporting documents attached to a work item. "
            + "This endpoint fetches the attachment folder associated with the work item "
            + "and returns a list of all documents in that folder with their metadata."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Request processed successfully. Check 'success' and 'count' fields for status.",
            content = @Content(
                mediaType = "application/json",
                examples = {
                    @ExampleObject(
                        name = "Documents Found",
                        value = "{\n"
                            + "  \"success\": true,\n"
                            + "  \"folderId\": \"1234\",\n"
                            + "  \"processInstanceId\": \"e-Notes-000000000008-process\",\n"
                            + "  \"workitemId\": \"1\",\n"
                            + "  \"count\": 3,\n"
                            + "  \"documents\": [\n"
                            + "    {\n"
                            + "      \"documentIndex\": \"1670\",\n"
                            + "      \"documentName\": \"Invoice.pdf\",\n"
                            + "      \"documentType\": \"pdf\",\n"
                            + "      \"documentSize\": \"245678\",\n"
                            + "      \"createdDateTime\": \"2025-01-15 10:30:00\",\n"
                            + "      \"modifiedDateTime\": \"2025-01-15 10:30:00\",\n"
                            + "      \"versionNo\": \"1.0\",\n"
                            + "      \"owner\": \"keerthi\",\n"
                            + "      \"comment\": \"Supporting invoice\"\n"
                            + "    },\n"
                            + "    {\n"
                            + "      \"documentIndex\": \"1671\",\n"
                            + "      \"documentName\": \"Contract.docx\",\n"
                            + "      \"documentType\": \"docx\",\n"
                            + "      \"documentSize\": \"156789\",\n"
                            + "      \"createdDateTime\": \"2025-01-14 09:15:00\",\n"
                            + "      \"modifiedDateTime\": \"2025-01-14 09:15:00\",\n"
                            + "      \"versionNo\": \"1.0\",\n"
                            + "      \"owner\": \"admin\",\n"
                            + "      \"comment\": \"\"\n"
                            + "    }\n"
                            + "  ]\n"
                            + "}"
                    ),
                    @ExampleObject(
                        name = "No Documents",
                        value = "{\n"
                            + "  \"success\": true,\n"
                            + "  \"folderId\": \"1234\",\n"
                            + "  \"processInstanceId\": \"e-Notes-000000000008-process\",\n"
                            + "  \"workitemId\": \"1\",\n"
                            + "  \"count\": 0,\n"
                            + "  \"documents\": []\n"
                            + "}"
                    ),
                    @ExampleObject(
                        name = "Folder Not Found",
                        value = "{\n"
                            + "  \"success\": true,\n"
                            + "  \"found\": false,\n"
                            + "  \"message\": \"No AttachmentFolderId found in work item attributes\",\n"
                            + "  \"count\": 0,\n"
                            + "  \"documents\": []\n"
                            + "}"
                    )
                }
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid or missing parameters",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    value = "{\n"
                        + "  \"success\": false,\n"
                        + "  \"error\": \"Missing required parameter: workitemId\"\n"
                        + "}"
                )
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    value = "{\n"
                        + "  \"success\": false,\n"
                        + "  \"error\": \"Error retrieving supporting documents\",\n"
                        + "  \"details\": \"Connection timeout\"\n"
                        + "}"
                )
            )
        )
    })
    @GetMapping(value = "/list", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> listSupportingDocuments(
            @Parameter(
                description = "Work Item ID",
                required = true,
                example = "1"
            )
            @RequestParam String workitemId,

            @Parameter(
                description = "Process Instance ID",
                required = true,
                example = "e-Notes-000000000008-process"
            )
            @RequestParam String processInstanceId,

            @Parameter(
                description = "Session ID from login",
                required = true
            )
            @RequestHeader("sessionId") long sessionId) {

        // Validate required parameters
        if (workitemId == null || workitemId.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(
                    createError("Missing required parameter: workitemId"));
        }

        if (processInstanceId == null || processInstanceId.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(
                    createError("Missing required parameter: processInstanceId"));
        }

        // Call service to get supporting documents
        JsonNode result = supportingDocsService.getSupportingDocuments(
                processInstanceId, workitemId, sessionId);

        // Always return 200 OK - check 'success' field for status
        return ResponseEntity.ok(result);
    }

    /**
     * Creates an error response JSON object.
     */
    private JsonNode createError(String message) {
        ObjectNode error = mapper.createObjectNode();
        error.put("success", false);
        error.put("error", message);
        return error;
    }
}
