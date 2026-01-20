package com.balmerlawrie.balmerrestservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Login request DTO with Swagger documentation.
 */
@Schema(description = "User login credentials")
public class LoginRequest {

    @Schema(description = "Username for authentication", example = "admin")
    private String userName;

    @Schema(description = "Password for authentication", example = "password123")
    private String password;

    public LoginRequest() {
    }

    public LoginRequest(String userName, String password) {
        this.userName = userName;
        this.password = password;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
