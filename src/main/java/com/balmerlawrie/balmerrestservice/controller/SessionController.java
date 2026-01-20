package com.balmerlawrie.balmerrestservice.controller;

import com.balmerlawrie.balmerrestservice.dto.LoginRequest;
import com.balmerlawrie.balmerrestservice.model.CachedSession;
import com.balmerlawrie.balmerrestservice.service.SessionManager;
import com.balmerlawrie.balmerrestservice.service.WMConnectService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for session management with caching support.
 * 
 * Sessions are cached to avoid calling WMConnect for every request.
 * Cached sessions are reused until they expire (default: 25 minutes).
 */
@RestController
@Tag(name = "Session Management", description = "Cached session management for iBPS")
public class SessionController {

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private WMConnectService wmConnectService;

    private final ObjectMapper mapper = new ObjectMapper();

    @Operation(summary = "Get or Create Session", description = "Returns a cached session if valid, or creates a new one. "
            +
            "Sessions are cached for 25 minutes to avoid repeated WMConnect calls.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Session obtained successfully"),
            @ApiResponse(responseCode = "401", description = "Authentication failed")
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "User credentials for session", required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = LoginRequest.class), examples = @ExampleObject(name = "Session Request", summary = "Get or create session", value = "{\"userName\": \"admin\", \"password\": \"password123\"}")))
    @PostMapping(value = "/session", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> getSession(@RequestBody LoginRequest credentials) {
        String userName = credentials.getUserName();
        String password = credentials.getPassword();

        if (userName == null || password == null) {
            return ResponseEntity.badRequest().body(
                    createErrorResponse("Missing userName or password"));
        }

        Long sessionId = sessionManager.getSession(userName, password);

        if (sessionId == null) {
            return ResponseEntity.status(401).body(
                    createErrorResponse("Authentication failed"));
        }

        // Get cached session details
        CachedSession cached = sessionManager.getCachedSession(userName);

        ObjectNode response = mapper.createObjectNode();
        response.put("success", true);
        response.put("sessionId", sessionId);
        response.put("userName", userName);
        response.put("remainingMinutes", cached != null ? cached.getRemainingMinutes() : 0);
        response.put("cached", true);

        // Also get user details
        Map<String, Object> userDetails = wmConnectService.getUserDetails(userName);
        response.set("userDetails", mapper.valueToTree(userDetails));

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Invalidate Session", description = "Invalidates a cached session, forcing a new WMConnect call on next request.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Session invalidated"),
            @ApiResponse(responseCode = "400", description = "Missing userName")
    })
    @DeleteMapping(value = "/session", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> invalidateSession(
            @Parameter(description = "Username whose session to invalidate") @RequestParam String userName) {

        if (userName == null || userName.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    createErrorResponse("Missing userName"));
        }

        sessionManager.invalidateSession(userName);

        ObjectNode response = mapper.createObjectNode();
        response.put("success", true);
        response.put("message", "Session invalidated for user: " + userName);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get Session Info", description = "Gets information about a cached session without creating a new one.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Session info returned"),
            @ApiResponse(responseCode = "404", description = "No cached session found")
    })
    @GetMapping(value = "/session/info", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> getSessionInfo(
            @Parameter(description = "Username to check") @RequestParam String userName) {

        CachedSession cached = sessionManager.getCachedSession(userName);

        ObjectNode response = mapper.createObjectNode();

        if (cached == null) {
            response.put("exists", false);
            response.put("message", "No valid cached session for user: " + userName);
            return ResponseEntity.status(404).body(response);
        }

        response.put("exists", true);
        response.put("sessionId", cached.getSessionId());
        response.put("userName", cached.getUserName());
        response.put("createdAt", cached.getCreatedAt().toString());
        response.put("remainingMinutes", cached.getRemainingMinutes());
        response.put("isValid", cached.isValid());
        response.put("timeoutMinutes", sessionManager.getSessionTimeoutMinutes());

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get Session Cache Stats", description = "Gets statistics about the session cache.")
    @GetMapping(value = "/session/stats", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> getCacheStats() {
        ObjectNode response = mapper.createObjectNode();
        response.put("cacheSize", sessionManager.getCacheSize());
        response.put("timeoutMinutes", sessionManager.getSessionTimeoutMinutes());
        return ResponseEntity.ok(response);
    }

    private JsonNode createErrorResponse(String message) {
        ObjectNode error = mapper.createObjectNode();
        error.put("success", false);
        error.put("error", message);
        return error;
    }
}
