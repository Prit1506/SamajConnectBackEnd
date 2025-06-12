package com.example.samajconnectbackend.service;
import com.example.samajconnectbackend.dto.ReactionStats;
import com.example.samajconnectbackend.entity.Event;
import com.example.samajconnectbackend.entity.EventReaction;
import com.example.samajconnectbackend.entity.ReactionType;
import com.example.samajconnectbackend.repository.EventReactionRepository;
import com.example.samajconnectbackend.repository.EventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class EventReactionService {

    @Autowired
    private EventReactionRepository eventReactionRepository;

    @Autowired
    private EventRepository eventRepository;

    @Transactional
    public EventReaction addOrUpdateReaction(Long userId, Long eventId, String reactionTypeStr) {
        ReactionType reactionType = ReactionType.valueOf(reactionTypeStr.toUpperCase());

        Optional<EventReaction> existingReaction = eventReactionRepository
                .findByUserIdAndEventId(userId, eventId);

        if (existingReaction.isPresent()) {
            EventReaction reaction = existingReaction.get();

            if (reaction.getReactionType() == reactionType) {
                // Same reaction - remove it (toggle off)
                eventReactionRepository.delete(reaction);
                return null; // Indicates reaction was removed
            } else {
                // Different reaction - update it
                reaction.setReactionType(reactionType);
                return eventReactionRepository.save(reaction);
            }
        } else {
            // No existing reaction - create new one
            EventReaction newReaction = new EventReaction(userId, eventId, reactionType);
            return eventReactionRepository.save(newReaction);
        }
    }

    @Transactional
    public void removeReaction(Long userId, Long eventId) {
        Optional<EventReaction> reaction =
                eventReactionRepository.findByUserIdAndEventId(userId, eventId);

        if (reaction.isPresent()) {
            ReactionType oldType = reaction.get().getReactionType();
            eventReactionRepository.delete(reaction.get());
            updateEventCounts(eventId, null, oldType);
        }
    }

    private void updateEventCounts(Long eventId, ReactionType newType, ReactionType oldType) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        // Decrease old count
        if (oldType == ReactionType.LIKE) {
            event.setLikeCount(Math.max(0, event.getLikeCount() - 1));
        } else if (oldType == ReactionType.DISLIKE) {
            event.setDislikeCount(Math.max(0, event.getDislikeCount() - 1));
        }

        // Increase new count
        if (newType == ReactionType.LIKE) {
            event.setLikeCount(event.getLikeCount() + 1);
        } else if (newType == ReactionType.DISLIKE) {
            event.setDislikeCount(event.getDislikeCount() + 1);
        }

        eventRepository.save(event);
    }

    public ReactionStats getReactionStats(Long eventId) {
        long likeCount = eventReactionRepository.countByEventIdAndReactionType(eventId, ReactionType.LIKE);
        long dislikeCount = eventReactionRepository.countByEventIdAndReactionType(eventId, ReactionType.DISLIKE);

        return new ReactionStats(likeCount, dislikeCount);
    }

    public ReactionStats getReactionStatsWithUserReaction(Long eventId, Long userId) {
        // Get total counts
        long likeCount = eventReactionRepository.countByEventIdAndReactionType(eventId, ReactionType.LIKE);
        long dislikeCount = eventReactionRepository.countByEventIdAndReactionType(eventId, ReactionType.DISLIKE);

        // Get user's specific reaction (if any)
        EventReaction userReaction = null;
        if (userId != null) {
            Optional<EventReaction> userReactionOpt = eventReactionRepository
                    .findByUserIdAndEventId(userId, eventId);
            if (userReactionOpt.isPresent()) {
                userReaction = userReactionOpt.get();
            }
        }

        return new ReactionStats(likeCount, dislikeCount, userReaction);
    }

    public Optional<ReactionType> getUserReaction(Long userId, Long eventId) {
        return eventReactionRepository.findReactionTypeByUserIdAndEventId(userId, eventId);
    }

    public boolean hasUserReacted(Long userId, Long eventId) {
        return eventReactionRepository.existsByUserIdAndEventId(userId, eventId);
    }
}