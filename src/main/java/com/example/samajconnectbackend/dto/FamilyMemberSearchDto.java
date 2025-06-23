package com.example.samajconnectbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FamilyMemberSearchDto {
    private String query;
    private List<String> relationshipTypes;
    private List<String> relationshipSides;
    private List<Integer> generationLevels;
    private Long samajId;
    private int page = 0;
    private int size = 20;
}