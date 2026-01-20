package com.balmerlawrie.balmerrestservice.controller;

import com.balmerlawrie.balmerrestservice.service.WMDisconnectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Authentication", description = "User authentication and session management")
public class DisconnectController {

    @Autowired
    private WMDisconnectService disconnectService;

    @Operation(summary = "User Logout", description = "Disconnects the user session and logs out the user.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Logout successful"),
            @ApiResponse(responseCode = "400", description = "Invalid session or parameters")
    })
    @PostMapping("/disconnect")
    public String disconnectUser(
            @Parameter(description = "Username", required = true) @RequestParam String name,
            @Parameter(description = "Session ID from login", required = true) @RequestHeader("sessionId") long sessionId) {
        return disconnectService.callWMDisconnect(name, sessionId);
    }
}
