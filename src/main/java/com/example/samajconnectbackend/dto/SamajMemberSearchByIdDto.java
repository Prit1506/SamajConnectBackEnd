package com.example.samajconnectbackend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SamajMemberSearchByIdDto {
    @NotNull(message = "Samaj ID is required")
    private Long samajId;

    private String query; // Optional search query

    @Min(value = 0, message = "Page number cannot be negative")
    private int page = 0;

    @Min(value = 1, message = "Page size must be at least 1")
    @Max(value = 100, message = "Page size cannot exceed 100")
    private int size = 20;
}
