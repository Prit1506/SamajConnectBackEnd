package com.example.samajconnectbackend.dto;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateSamajResponse {
    private boolean success;
    private String message;
    private SamajDto samaj;
}
