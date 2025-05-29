package com.example.samajconnectbackend.dto;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

// Response for getting all samajs
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SamajListResponse {
    private boolean success;
    private String message;
    private List<SamajDto> samajs;
}