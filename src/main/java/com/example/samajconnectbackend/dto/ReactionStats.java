package com.example.samajconnectbackend.dto;

import com.example.samajconnectbackend.entity.EventReaction;

public class ReactionStats {
    private long likeCount;
    private long dislikeCount;
    private long totalReactions;
    private EventReaction userReaction; // The user's specific reaction
    private double likePercentage;
    private double dislikePercentage;

    // Constructors
    public ReactionStats() {}

    public ReactionStats(long likeCount, long dislikeCount) {
        this.likeCount = likeCount;
        this.dislikeCount = dislikeCount;
        this.totalReactions = likeCount + dislikeCount;
        this.userReaction = null;
        calculatePercentages();
    }

    public ReactionStats(long likeCount, long dislikeCount, EventReaction userReaction) {
        this.likeCount = likeCount;
        this.dislikeCount = dislikeCount;
        this.totalReactions = likeCount + dislikeCount;
        this.userReaction = userReaction;
        calculatePercentages();
    }

    // Getters and Setters
    public long getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(long likeCount) {
        this.likeCount = likeCount;
        recalculate();
    }

    public long getDislikeCount() {
        return dislikeCount;
    }

    public void setDislikeCount(long dislikeCount) {
        this.dislikeCount = dislikeCount;
        recalculate();
    }

    public long getTotalReactions() {
        return totalReactions;
    }

    public EventReaction getUserReaction() {
        return userReaction;
    }

    public void setUserReaction(EventReaction userReaction) {
        this.userReaction = userReaction;
    }

    public double getLikePercentage() {
        return likePercentage;
    }

    public double getDislikePercentage() {
        return dislikePercentage;
    }

    private void recalculate() {
        this.totalReactions = this.likeCount + this.dislikeCount;
        calculatePercentages();
    }

    private void calculatePercentages() {
        if (totalReactions > 0) {
            likePercentage = (double) likeCount / totalReactions * 100;
            dislikePercentage = (double) dislikeCount / totalReactions * 100;
        } else {
            likePercentage = 0;
            dislikePercentage = 0;
        }
    }

    // Utility methods
    public boolean hasUserReacted() {
        return userReaction != null;
    }

    public boolean hasUserLiked() {
        return userReaction != null && userReaction.getReactionType().name().equals("LIKE");
    }

    public boolean hasUserDisliked() {
        return userReaction != null && userReaction.getReactionType().name().equals("DISLIKE");
    }

    public String getReactionSummary() {
        if (totalReactions == 0) {
            return "No reactions yet";
        } else if (totalReactions == 1) {
            return "1 reaction";
        } else {
            return totalReactions + " reactions";
        }
    }

    @Override
    public String toString() {
        return "ReactionStats{" +
                "likeCount=" + likeCount +
                ", dislikeCount=" + dislikeCount +
                ", totalReactions=" + totalReactions +
                ", userReaction=" + (userReaction != null ? userReaction.getReactionType() : "null") +
                ", likePercentage=" + likePercentage +
                ", dislikePercentage=" + dislikePercentage +
                '}';
    }
}