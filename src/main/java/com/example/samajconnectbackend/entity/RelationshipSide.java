package com.example.samajconnectbackend.entity;

public enum RelationshipSide {
    PATERNAL("Paternal Side", "Father's family"),
    MATERNAL("Maternal Side", "Mother's family"),
    SPOUSE_FAMILY("Spouse Family", "In-laws and spouse's relatives"),
    DIRECT("Direct Family", "Immediate family members"),
    STEP_FAMILY("Step Family", "Step family members");

    private final String displayName;
    private final String description;

    RelationshipSide(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}