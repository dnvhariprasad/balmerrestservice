package com.balmerlawrie.balmerrestservice.controller;

import com.balmerlawrie.balmerrestservice.service.WorkItemAttributesService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for work item attributes.
 */
@RestController
@RequestMapping("/wi")
@Tag(name = "Work Items", description = "Work item attribute operations")
public class WorkItemAttributesController {

    @Autowired
    private WorkItemAttributesService workItemAttributesService;

    private final ObjectMapper mapper = new ObjectMapper();

    @Operation(
            summary = "Fetch Work Item Attributes",
            description = "Retrieves raw WMFetchWorkItemAttributes response for a work item."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Request processed successfully.",
                    content = @Content(mediaType = "application/json", examples = {
                            @ExampleObject(name = "Success", value = "{ \"WMFetchWorkItemAttributes_Output\": { \"Status\": 0 } }"),
                            @ExampleObject(name = "Error", value = "{ \"success\": false, \"error\": \"iBPS error: ...\" }")
                    })
            ),
            @ApiResponse(responseCode = "400", description = "Invalid parameters")
    })
    @GetMapping(value = "/attributefetch", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> fetchAttributes(
            @Parameter(description = "Work Item ID", required = true, example = "1")
            @RequestParam String workitemId,
            @Parameter(description = "Process Instance ID", required = true, example = "e-Notes-000000000008-process")
            @RequestParam String processInstanceId,
            @Parameter(description = "Session ID from login", required = true)
            @RequestHeader("sessionId") long sessionId) {

        if (workitemId == null || workitemId.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(createError("Missing required parameter: workitemId"));
        }

        if (processInstanceId == null || processInstanceId.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(createError("Missing required parameter: processInstanceId"));
        }

        JsonNode result = workItemAttributesService.fetchWorkItemAttributes(processInstanceId, workitemId, sessionId);
        return ResponseEntity.ok(result);
    }

    private JsonNode createError(String message) {
        ObjectNode error = mapper.createObjectNode();
        error.put("success", false);
        error.put("error", message);
        return error;
    }
}
