package com.example.samajconnectbackend.dto;

import com.example.samajconnectbackend.entity.RelationshipSide;
import com.example.samajconnectbackend.entity.RelationshipType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotNull;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRelationshipRequest {
    @NotNull(message = "Relationship ID is required")
    private Long relationshipId;

    private RelationshipType relationshipType;
    private RelationshipSide relationshipSide;
    private Integer generationLevel;

    @NotNull(message = "Updated by user ID is required")
    private Long updatedBy;
}