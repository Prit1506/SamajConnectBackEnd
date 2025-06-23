package com.example.samajconnectbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelationshipValidationResponse {
    private boolean isValid;
    private List<String> validationErrors;
    private List<String> warnings;
    private List<UserNodeDto> conflictingRelationships;
    private String suggestion;
}