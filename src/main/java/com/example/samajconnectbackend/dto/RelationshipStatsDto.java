package com.example.samajconnectbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelationshipStatsDto {
    private Long totalRelationships;
    private Map<String, Long> relationshipsBySide;
    private Map<Integer, Long> relationshipsByGeneration;
    private Long pendingRequests;
    private Long directFamilyCount;
    private Long extendedFamilyCount;
}