package com.example.samajconnectbackend.dto;

import com.example.samajconnectbackend.entity.RelationshipSide;
import com.example.samajconnectbackend.entity.RelationshipType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserNodeDto {
    private Long userId;
    private String name;
    private String email;
    private String profileImageBase64;
    private RelationshipType relationshipType;
    private String relationshipDisplayName;
    private RelationshipSide relationshipSide;
    private String relationshipSideDisplayName;
    private Integer generationLevel;
    private String generationName;
    private String samajName;
    private boolean isCurrentUser;
    private boolean hasProfileImage;
    private Long relationshipId;
}