package com.example.samajconnectbackend.dto;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
// Response for checking samaj name availability
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CheckSamajResponse {
    private boolean exists;
    private String message;
}
