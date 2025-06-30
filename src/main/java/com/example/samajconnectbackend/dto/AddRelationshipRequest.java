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

    // REMOVED: sendRequest field - all requests now go through approval process

    // NEW FIELDS FOR LINEAGE CONTEXT
    private String lineageContext; // "PATERNAL" or "MATERNAL" - helps determine correct reverse relationship
    private Long intermediateRelativeId; // ID of the connecting relative (e.g., parent for grandparent relationships)
    private String relationshipPath; // Description of relationship path for complex relationships

    // REMOVED: Admin override functionality - all relationships require approval
    // private boolean adminOverride = false;
}
