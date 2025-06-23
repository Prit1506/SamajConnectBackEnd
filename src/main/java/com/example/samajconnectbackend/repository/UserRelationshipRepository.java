package com.example.samajconnectbackend.repository;

import com.example.samajconnectbackend.entity.RelationshipSide;
import com.example.samajconnectbackend.entity.RelationshipType;
import com.example.samajconnectbackend.entity.UserRelationship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRelationshipRepository extends JpaRepository<UserRelationship, Long> {

    // Find all active relationships for a user
    List<UserRelationship> findByUserIdAndIsActiveTrue(Long userId);

    // Find relationships by side
    List<UserRelationship> findByUserIdAndRelationshipSideAndIsActiveTrue(Long userId, RelationshipSide relationshipSide);

    // Find relationships by generation level
    List<UserRelationship> findByUserIdAndGenerationLevelAndIsActiveTrue(Long userId, Integer generationLevel);

    // Find specific relationship
    Optional<UserRelationship> findByUserIdAndRelatedUserIdAndRelationshipTypeAndIsActiveTrue(
            Long userId, Long relatedUserId, RelationshipType relationshipType);

    // Check if relationship exists (bidirectional)
    @Query("SELECT ur FROM UserRelationship ur WHERE " +
            "((ur.userId = :userId AND ur.relatedUserId = :relatedUserId) OR " +
            "(ur.userId = :relatedUserId AND ur.relatedUserId = :userId)) AND " +
            "ur.isActive = true")
    List<UserRelationship> findExistingRelationship(@Param("userId") Long userId, @Param("relatedUserId") Long relatedUserId);

    // Get complete family tree using native query for better performance
    @Query(nativeQuery = true, value = """
        SELECT ur.*, u.name as related_user_name, u.email as related_user_email, 
               u.profile_img as related_user_profile_img, s.name as samaj_name
        FROM user_relationships ur
        JOIN users u ON ur.related_user_id = u.id
        LEFT JOIN samajs s ON u.samaj_id = s.id
        WHERE ur.user_id = :userId AND ur.is_active = true
        ORDER BY ur.generation_level DESC, ur.relationship_side, ur.relationship_type
        """)
    List<Object[]> getFamilyTreeData(@Param("userId") Long userId);

    // Get family members by generation level and side
    @Query("SELECT ur FROM UserRelationship ur " +
            "JOIN ur.relatedUser u " +
            "WHERE ur.userId = :userId AND ur.generationLevel = :generationLevel " +
            "AND ur.relationshipSide = :side AND ur.isActive = true " +
            "ORDER BY ur.relationshipType")
    List<UserRelationship> findFamilyMembersByGenerationAndSide(
            @Param("userId") Long userId,
            @Param("generationLevel") Integer generationLevel,
            @Param("side") RelationshipSide side);

    // Find parents
    @Query("SELECT ur FROM UserRelationship ur WHERE ur.userId = :userId AND " +
            "ur.relationshipType IN ('FATHER', 'MOTHER') AND ur.isActive = true")
    List<UserRelationship> findParents(@Param("userId") Long userId);

    // Find children
    @Query("SELECT ur FROM UserRelationship ur WHERE ur.userId = :userId AND " +
            "ur.relationshipType IN ('SON', 'DAUGHTER') AND ur.isActive = true")
    List<UserRelationship> findChildren(@Param("userId") Long userId);

    // Find siblings
    @Query("SELECT ur FROM UserRelationship ur WHERE ur.userId = :userId AND " +
            "ur.relationshipType IN ('BROTHER', 'SISTER') AND ur.isActive = true")
    List<UserRelationship> findSiblings(@Param("userId") Long userId);

    // Find spouse
    @Query("SELECT ur FROM UserRelationship ur WHERE ur.userId = :userId AND " +
            "ur.relationshipType IN ('HUSBAND', 'WIFE') AND ur.isActive = true")
    Optional<UserRelationship> findSpouse(@Param("userId") Long userId);

    // Get relationship count by side
    @Query("SELECT ur.relationshipSide, COUNT(ur) FROM UserRelationship ur " +
            "WHERE ur.userId = :userId AND ur.isActive = true " +
            "GROUP BY ur.relationshipSide")
    List<Object[]> getRelationshipCountBySide(@Param("userId") Long userId);

    // Find mutual relationships (common relatives)
    @Query("SELECT ur1.relatedUserId FROM UserRelationship ur1 " +
            "JOIN UserRelationship ur2 ON ur1.relatedUserId = ur2.relatedUserId " +
            "WHERE ur1.userId = :userId1 AND ur2.userId = :userId2 AND " +
            "ur1.isActive = true AND ur2.isActive = true")
    List<Long> findMutualRelatives(@Param("userId1") Long userId1, @Param("userId2") Long userId2);

    // Delete relationship and its reverse
    @Query("UPDATE UserRelationship ur SET ur.isActive = false, ur.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE ((ur.userId = :userId AND ur.relatedUserId = :relatedUserId) OR " +
            "(ur.userId = :relatedUserId AND ur.relatedUserId = :userId)) AND ur.isActive = true")
    void softDeleteRelationship(@Param("userId") Long userId, @Param("relatedUserId") Long relatedUserId);
}