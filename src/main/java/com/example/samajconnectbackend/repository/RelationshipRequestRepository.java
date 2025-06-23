package com.example.samajconnectbackend.repository;

import com.example.samajconnectbackend.entity.RelationshipRequest;
import com.example.samajconnectbackend.entity.RelationshipType;
import com.example.samajconnectbackend.entity.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface RelationshipRequestRepository extends JpaRepository<RelationshipRequest, Long> {

    // Find pending requests for a user (received)
    List<RelationshipRequest> findByTargetUserIdAndStatus(Long targetUserId, RequestStatus status);

    // Find sent requests by user
    List<RelationshipRequest> findByRequesterUserIdAndStatus(Long requesterUserId, RequestStatus status);

    // Find all requests for a user (sent and received)
    @Query("SELECT rr FROM RelationshipRequest rr WHERE " +
            "(rr.requesterUserId = :userId OR rr.targetUserId = :userId) " +
            "ORDER BY rr.createdAt DESC")
    List<RelationshipRequest> findAllRequestsForUser(@Param("userId") Long userId);

    // Check if request already exists
    Optional<RelationshipRequest> findByRequesterUserIdAndTargetUserIdAndRelationshipTypeAndStatus(
            Long requesterUserId, Long targetUserId, RelationshipType relationshipType, RequestStatus status);

    // Find pending requests between two users
    @Query("SELECT rr FROM RelationshipRequest rr WHERE " +
            "((rr.requesterUserId = :userId1 AND rr.targetUserId = :userId2) OR " +
            "(rr.requesterUserId = :userId2 AND rr.targetUserId = :userId1)) AND " +
            "rr.status = 'PENDING'")
    List<RelationshipRequest> findPendingRequestsBetweenUsers(@Param("userId1") Long userId1, @Param("userId2") Long userId2);

    // Count pending requests for user
    @Query("SELECT COUNT(rr) FROM RelationshipRequest rr WHERE rr.targetUserId = :userId AND rr.status = 'PENDING'")
    Long countPendingRequestsForUser(@Param("userId") Long userId);

    // Find requests by status and date range
    @Query("SELECT rr FROM RelationshipRequest rr WHERE rr.targetUserId = :userId AND " +
            "rr.status = :status AND rr.createdAt >= :fromDate " +
            "ORDER BY rr.createdAt DESC")
    List<RelationshipRequest> findRequestsByStatusAndDateRange(
            @Param("userId") Long userId,
            @Param("status") RequestStatus status,
            @Param("fromDate") java.time.LocalDateTime fromDate);
}