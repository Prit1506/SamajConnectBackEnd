package com.example.samajconnectbackend.entity;

public enum RequestStatus {
    PENDING("Pending", "Waiting for response"),
    APPROVED("Approved", "Request accepted"),
    REJECTED("Rejected", "Request declined"),
    CANCELLED("Cancelled", "Request cancelled by requester");

    private final String displayName;
    private final String description;

    RequestStatus(String displayName, String description) {
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
