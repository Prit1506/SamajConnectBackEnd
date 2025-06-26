package com.example.samajconnectbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SamajMemberSearchDto {
    private String query;
    private int page = 0;
    private int size = 20;
}
