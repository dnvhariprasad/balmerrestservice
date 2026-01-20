package com.balmerlawrie.balmerrestservice.controller;

import com.balmerlawrie.balmerrestservice.dto.LoginRequest;
import com.balmerlawrie.balmerrestservice.service.WMConnectService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Component
@RestController
@Tag(name = "Authentication", description = "User authentication and session management")
public class WMConnectController {

    @Autowired
    private WMConnectService wmConnectService;

    @Operation(summary = "User Login", description = "Authenticates user with username and password. Returns session ID and user details.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login successful", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "400", description = "Missing username or password"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "User credentials for login", required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = LoginRequest.class), examples = @ExampleObject(name = "Login Example", summary = "Sample login request", value = "{\"userName\": \"admin\", \"password\": \"password123\"}")))
    @PostMapping(value = "/login-wmConnectCabinet", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> callApi(@RequestBody LoginRequest credentials) {
        String userName = credentials.getUserName();
        String password = credentials.getPassword();

        if (userName == null || password == null) {
            return ResponseEntity.badRequest().body(null);
        }

        JsonNode wmResponse = wmConnectService.callWMConnectApi(userName, password);
        Map<String, Object> userDetails = wmConnectService.getUserDetails(userName);

        // Combine both responses
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode combined = mapper.createObjectNode();
        combined.set("wmConnectResponse", wmResponse);
        combined.set("userDetails", mapper.valueToTree(userDetails));

        return ResponseEntity.ok(combined);
    }
}
