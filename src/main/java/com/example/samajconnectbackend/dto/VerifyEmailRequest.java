package com.example.samajconnectbackend.dto;

import lombok.Data;

@Data
public class VerifyEmailRequest {
    private String email;
    private String otp;
}