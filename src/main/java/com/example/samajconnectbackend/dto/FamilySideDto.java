package com.example.samajconnectbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FamilySideDto {
    private String sideName;
    private String sideDisplayName;
    private String sideDescription;
    private Long memberCount;
    private Map<Integer, List<UserNodeDto>> generationMembers;
    private List<GenerationDto> generations;
}
