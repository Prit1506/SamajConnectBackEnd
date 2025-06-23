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
public class AddRelationshipRequest {
    @NotNull(message = "Related user ID is required")
    private Long relatedUserId;

    @NotNull(message = "Relationship type is required")
    private RelationshipType relationshipType;

    private RelationshipSide relationshipSide;

    private Integer generationLevel;

    private String requestMessage;

    @NotNull(message = "Requesting user ID is required")
    private Long requestingUserId;

    private boolean sendRequest; // If true, send request instead of direct add
}