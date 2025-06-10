package com.example.samajconnectbackend.repository;

import com.example.samajconnectbackend.entity.EventReaction;
import com.example.samajconnectbackend.entity.ReactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventReactionRepository extends JpaRepository<EventReaction, Long> {

    // Find reaction by user and event
    Optional<EventReaction> findByUserIdAndEventId(Long userId, Long eventId);

    // Count reactions by event and type
    long countByEventIdAndReactionType(Long eventId, ReactionType reactionType);

    // Find all reactions for a specific event
    List<EventReaction> findByEventId(Long eventId);

    // Find all reactions by a specific user
    List<EventReaction> findByUserId(Long userId);

    // Check if user has reacted to an event
    boolean existsByUserIdAndEventId(Long userId, Long eventId);

    // Get user's reaction type for a specific event
    @Query("SELECT er.reactionType FROM EventReaction er WHERE er.userId = :userId AND er.eventId = :eventId")
    Optional<ReactionType> findReactionTypeByUserIdAndEventId(@Param("userId") Long userId, @Param("eventId") Long eventId);

    // Delete all reactions for a specific event
    void deleteByEventId(Long eventId);

    // Delete all reactions by a specific user
    void deleteByUserId(Long userId);

    // Get events that a user has liked
    @Query("SELECT er.eventId FROM EventReaction er WHERE er.userId = :userId AND er.reactionType = 'LIKE'")
    List<Long> findLikedEventIdsByUserId(@Param("userId") Long userId);

    // Get events that a user has disliked
    @Query("SELECT er.eventId FROM EventReaction er WHERE er.userId = :userId AND er.reactionType = 'DISLIKE'")
    List<Long> findDislikedEventIdsByUserId(@Param("userId") Long userId);

    // Get top liked events
    @Query("SELECT er.eventId, COUNT(er.id) as likeCount " +
            "FROM EventReaction er " +
            "WHERE er.reactionType = 'LIKE' " +
            "GROUP BY er.eventId " +
            "ORDER BY likeCount DESC")
    List<Object[]> findTopLikedEvents();
}