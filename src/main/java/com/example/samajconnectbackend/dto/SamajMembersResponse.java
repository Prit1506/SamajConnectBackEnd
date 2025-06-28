package com.example.samajconnectbackend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SamajMembersResponse {
    private List<DetailedUserDto> members;
    private long totalMembers;
    private int currentPage;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;
    private String message;
    private Long samajId;
    private String samajName;
}