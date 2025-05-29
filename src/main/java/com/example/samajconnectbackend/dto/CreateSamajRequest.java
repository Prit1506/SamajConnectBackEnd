package com.example.samajconnectbackend.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

// DTO for creating a new samaj
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateSamajRequest {
    private String name;
    private String description;
    private String rules;
    private LocalDate establishedDate;

    // Admin user details
    private String adminName;
    private String adminEmail;
    private String adminPassword;
}