package com.example.samajconnectbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenerationDto {
    private Integer level;
    private String levelName;
    private String levelDescription;
    private Long memberCount;
    private Map<String, List<UserNodeDto>> sideMembers; // Map by RelationshipSide
    private List<UserNodeDto> allMembers;
}