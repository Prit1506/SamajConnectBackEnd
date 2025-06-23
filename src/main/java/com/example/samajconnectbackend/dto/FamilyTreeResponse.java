package com.example.samajconnectbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FamilyTreeResponse {
    private UserNodeDto rootUser;
    private Map<String, FamilySideDto> familySides;
    private List<GenerationDto> generations;
    private RelationshipStatsDto stats;
    private Long totalMembers;
    private boolean hasSpouse;
    private boolean hasChildren;
    private boolean hasParents;
}