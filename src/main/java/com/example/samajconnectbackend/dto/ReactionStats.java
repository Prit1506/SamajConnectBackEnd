package com.example.samajconnectbackend.dto;

import com.example.samajconnectbackend.entity.ReactionType;

public class ReactionStats {
    private Long eventId;
    private long likeCount;
    private long dislikeCount;
    private long totalCount;
    private ReactionType currentUserReaction;

    public ReactionStats() {}

    public ReactionStats(long likeCount, long dislikeCount) {
        this.likeCount = likeCount;
        this.dislikeCount = dislikeCount;
        this.totalCount = likeCount + dislikeCount;
    }

    public ReactionStats(Long eventId, long likeCount, long dislikeCount, ReactionType currentUserReaction) {
        this.eventId = eventId;
        this.likeCount = likeCount;
        this.dislikeCount = dislikeCount;
        this.totalCount = likeCount + dislikeCount;
        this.currentUserReaction = currentUserReaction;
    }

    // Getters and setters
    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public long getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(long likeCount) {
        this.likeCount = likeCount;
    }

    public long getDislikeCount() {
        return dislikeCount;
    }

    public void setDislikeCount(long dislikeCount) {
        this.dislikeCount = dislikeCount;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(long totalCount) {
        this.totalCount = totalCount;
    }

    public ReactionType getCurrentUserReaction() {
        return currentUserReaction;
    }

    public void setCurrentUserReaction(ReactionType currentUserReaction) {
        this.currentUserReaction = currentUserReaction;
    }

    public double getLikePercentage() {
        if (totalCount == 0) return 0.0;
        return (double) likeCount / totalCount * 100;
    }

    public double getDislikePercentage() {
        if (totalCount == 0) return 0.0;
        return (double) dislikeCount / totalCount * 100;
    }

    public boolean isPositivelyReceived() {
        return likeCount > dislikeCount;
    }
}