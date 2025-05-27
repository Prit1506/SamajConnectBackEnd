package com.example.samajconnectbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {
    private boolean success;
    private String message;
    private String token;
    private UserDto user;

    public LoginResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
}
