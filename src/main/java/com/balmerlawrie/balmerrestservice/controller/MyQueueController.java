package com.balmerlawrie.balmerrestservice.controller;

import com.balmerlawrie.balmerrestservice.service.MyQueueService;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for My Queue and Common Queue work items.
 * 
 * My Queue = Work items assigned directly to the user
 * Common Queue = Work items in shared/group queues the user has access to
 */
@RestController
@RequestMapping("/queue")
@Tag(name = "Queue Management", description = "Fetch work items from My Queue and Common Queue")
public class MyQueueController {

    @Autowired
    private MyQueueService myQueueService;

    @Operation(summary = "Get My Queue Work Items", description = "Returns all work items assigned directly to the user across all processes. "
            +
            "Uses WMFetchWorkList with MyQueueFlag=Y.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Work items retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid session ID")
    })
    @GetMapping(value = "/myqueue", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> getMyQueue(
            @Parameter(description = "Session ID from login", required = true) @RequestHeader("sessionId") long sessionId) {

        JsonNode result = myQueueService.getMyQueueItems(sessionId);

        if (result.path("success").asBoolean(false)) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }

    @Operation(summary = "Get Common Queue Work Items", description = "Returns all work items from shared/group queues the user has access to.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Work items retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid session ID")
    })
    @GetMapping(value = "/commonqueue", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> getCommonQueue(
            @Parameter(description = "Session ID from login", required = true) @RequestHeader("sessionId") long sessionId) {

        JsonNode result = myQueueService.getCommonQueueItems(sessionId);

        if (result.path("success").asBoolean(false)) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }

    @Operation(summary = "Get All Work Items", description = "Returns ALL work items (My Queue + Common Queue combined). "
            +
            "This is the main endpoint to display all work assigned to or available for the user.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Work items retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid session ID")
    })
    @GetMapping(value = "/allworkitems", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> getAllWorkItems(
            @Parameter(description = "Session ID from login", required = true) @RequestHeader("sessionId") long sessionId) {

        JsonNode result = myQueueService.getAllWorkItems(sessionId);

        if (result.path("success").asBoolean(false)) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }

    @Operation(summary = "Get User's Accessible Queues", description = "Returns a list of all queues the user has access to.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Queue list retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid session ID")
    })
    @GetMapping(value = "/list", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> getUserQueues(
            @Parameter(description = "Session ID from login", required = true) @RequestHeader("sessionId") long sessionId) {

        JsonNode result = myQueueService.getUserQueues(sessionId);

        if (result.path("success").asBoolean(false)) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }

    @Operation(summary = "Get Work Items from Specific Queue", description = "Returns work items from a specific queue by ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Work items retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid session ID or queue ID")
    })
    @GetMapping(value = "/{queueId}/items", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> getQueueWorkItems(
            @Parameter(description = "Queue ID", required = true) @PathVariable int queueId,
            @Parameter(description = "Queue name for display", required = false) @RequestParam(defaultValue = "Queue") String queueName,
            @Parameter(description = "Session ID from login", required = true) @RequestHeader("sessionId") long sessionId) {

        JsonNode result = myQueueService.getQueueWorkItems(queueId, queueName, sessionId);

        if (result.path("success").asBoolean(false)) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }
}
