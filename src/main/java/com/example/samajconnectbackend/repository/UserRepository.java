package com.example.samajconnectbackend.repository;

import com.example.samajconnectbackend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    /**
     * Search users from the same samaj excluding the current user
     */
    @Query("SELECT u FROM User u WHERE u.samaj.id = :samajId AND u.id != :currentUserId " +
            "AND (LOWER(u.name) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<User> findSamajMembersByQuery(@Param("samajId") Long samajId,
                                       @Param("currentUserId") Long currentUserId,
                                       @Param("query") String query,
                                       Pageable pageable);

    /**
     * Get all users from the same samaj excluding the current user
     */
    @Query("SELECT u FROM User u WHERE u.samaj.id = :samajId AND u.id != :currentUserId")
    Page<User> findAllSamajMembers(@Param("samajId") Long samajId,
                                   @Param("currentUserId") Long currentUserId,
                                   Pageable pageable);

    /**
     * Check if two users have any relationship
     */
    @Query("SELECT COUNT(ur) > 0 FROM UserRelationship ur WHERE " +
            "(ur.userId = :userId1 AND ur.relatedUserId = :userId2) OR " +
            "(ur.userId = :userId2 AND ur.relatedUserId = :userId1) AND ur.isActive = true")
    boolean hasRelationship(@Param("userId1") Long userId1, @Param("userId2") Long userId2);

    /**
     * Check if there's a pending relationship request between users
     */
    @Query("SELECT COUNT(rr) > 0 FROM RelationshipRequest rr WHERE " +
            "((rr.requesterUserId = :userId1 AND rr.targetUserId = :userId2) OR " +
            "(rr.requesterUserId = :userId2 AND rr.targetUserId = :userId1)) AND " +
            "rr.status = 'PENDING'")
    boolean hasPendingRequest(@Param("userId1") Long userId1, @Param("userId2") Long userId2);

    /**
     * Check if current user has sent a request to target user
     */
    @Query("SELECT COUNT(rr) > 0 FROM RelationshipRequest rr WHERE " +
            "rr.requesterUserId = :currentUserId AND rr.targetUserId = :targetUserId AND " +
            "rr.status = 'PENDING'")
    boolean hasSentRequestTo(@Param("currentUserId") Long currentUserId, @Param("targetUserId") Long targetUserId);

    /**
     * Check if target user has sent a request to current user
     */
    @Query("SELECT COUNT(rr) > 0 FROM RelationshipRequest rr WHERE " +
            "rr.requesterUserId = :targetUserId AND rr.targetUserId = :currentUserId AND " +
            "rr.status = 'PENDING'")
    boolean hasReceivedRequestFrom(@Param("currentUserId") Long currentUserId, @Param("targetUserId") Long targetUserId);
}
