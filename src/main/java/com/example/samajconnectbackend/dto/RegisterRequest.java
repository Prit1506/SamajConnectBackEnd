package com.example.samajconnectbackend.dto;

import lombok.Data;

@Data
public class RegisterRequest {
    private String name;
    private String email;
    private String password;
    private Boolean isAdmin;

    // For individual users joining existing samaj
    private Long samajId;

    // For admin users creating new samaj
    private String samajName;
    private String samajDescription;
    private String samajRules;
    private String samajEstablishedDate; // Will be parsed to LocalDate
}