package com.example.samajconnectbackend.dto;

import com.example.samajconnectbackend.entity.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelationshipContext {
    private String lineage; // "PATERNAL" or "MATERNAL"
    private User intermediateRelative; // The connecting relative (parent, etc.)
    private String originalRelationshipPath; // Full path of relationship
    private Map<String, Object> additionalInfo;

    public RelationshipContext(String lineage) {
        this.lineage = lineage;
    }

    public RelationshipContext(User intermediateRelative) {
        this.intermediateRelative = intermediateRelative;
    }

    public boolean isPaternal() {
        return "PATERNAL".equalsIgnoreCase(lineage);
    }

    public boolean isMaternal() {
        return "MATERNAL".equalsIgnoreCase(lineage);
    }
}
