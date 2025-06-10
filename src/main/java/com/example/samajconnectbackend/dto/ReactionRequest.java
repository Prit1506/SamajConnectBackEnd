package com.example.samajconnectbackend.dto;

import com.example.samajconnectbackend.entity.ReactionType;

public class ReactionRequest {

    private ReactionType reactionType;
    private Long userId;

    // ✅ Required for JSON deserialization
    public ReactionRequest() {}

    public ReactionRequest(Long userId) {
        this.userId = userId;
    }

    public ReactionRequest(ReactionType reactionType, Long userId) {
        this.reactionType = reactionType;
        this.userId = userId;
    }

    public ReactionType getReactionType() {
        return reactionType;
    }

    public void setReactionType(ReactionType reactionType) {
        this.reactionType = reactionType;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
