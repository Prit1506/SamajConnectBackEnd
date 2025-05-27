package com.example.samajconnectbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class VerifyEmailResponse {
    private boolean success;
    private String message;
}