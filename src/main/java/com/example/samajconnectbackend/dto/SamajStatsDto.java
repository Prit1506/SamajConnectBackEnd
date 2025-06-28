package com.example.samajconnectbackend.dto;

import lombok.Data;

@Data
public class SamajStatsDto {
    private Long samajId;
    private String samajName;
    private String description;
    private long totalMembers;
}