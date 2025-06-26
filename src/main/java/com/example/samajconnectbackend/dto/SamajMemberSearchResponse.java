package com.example.samajconnectbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SamajMemberSearchResponse {
    private List<SamajMemberDto> members;
    private Long totalResults;
    private int currentPage;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;
    private String message;
}
