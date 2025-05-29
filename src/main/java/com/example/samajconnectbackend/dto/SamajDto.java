package com.example.samajconnectbackend.dto;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
// DTO for samaj response
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SamajDto {
    private Long id;
    private String name;
    private String description;
    private String rules;
    private LocalDate establishedDate;
    private int memberCount;
}