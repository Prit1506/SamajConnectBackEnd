package com.example.samajconnectbackend.service;

import com.example.samajconnectbackend.dto.RelationshipValidationResponse;
import com.example.samajconnectbackend.dto.UserNodeDto;
import com.example.samajconnectbackend.entity.RelationshipType;
import com.example.samajconnectbackend.entity.User;
import com.example.samajconnectbackend.entity.UserRelationship;
import com.example.samajconnectbackend.repository.UserRelationshipRepository;
import com.example.samajconnectbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RelationshipValidationService {

    private final UserRelationshipRepository relationshipRepository;
    private final UserRepository userRepository;

    public RelationshipValidationResponse validateRelationship(Long userId, Long relatedUserId, RelationshipType relationshipType) {
        RelationshipValidationResponse response = new RelationshipValidationResponse();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<UserNodeDto> conflictingRelationships = new ArrayList<>();

        try {
            log.info("VALIDATION_START: UserId={}, RelatedUserId={}, RelationshipType={}",
                    userId, relatedUserId, relationshipType);

            // Basic validations
            if (userId.equals(relatedUserId)) {
                errors.add("Cannot create relationship with yourself");
            }

            // Check if users exist
            Optional<User> user = userRepository.findById(userId);
            Optional<User> relatedUser = userRepository.findById(relatedUserId);

            if (user.isEmpty()) {
                errors.add("Primary user not found");
            }
            if (relatedUser.isEmpty()) {
                errors.add("Related user not found");
            }

            if (!errors.isEmpty()) {
                response.setValid(false);
                response.setValidationErrors(errors);
                return response;
            }

            // Check for existing relationships
            List<UserRelationship> existingRelationships = relationshipRepository
                    .findExistingRelationship(userId, relatedUserId);

            if (!existingRelationships.isEmpty()) {
                conflictingRelationships = existingRelationships.stream()
                        .map(this::buildUserNodeDto)
                        .collect(Collectors.toList());
                warnings.add("A relationship already exists between these users");
            }

            // Validate relationship logic
            validateRelationshipLogic(userId, relatedUserId, relationshipType, errors, warnings);

            // FIXED: More intelligent circular relationship check
            if (wouldCreateProblematicCycle(userId, relatedUserId, relationshipType)) {
                errors.add("This relationship would create an invalid family structure");
            }

            // Validate spouse relationships
            validateSpouseRelationships(userId, relatedUserId, relationshipType, errors, warnings);

            // Validate parent-child relationships
            validateParentChildRelationships(userId, relatedUserId, relationshipType, errors, warnings);

            response.setValid(errors.isEmpty());
            response.setValidationErrors(errors);
            response.setWarnings(warnings);
            response.setConflictingRelationships(conflictingRelationships);

            if (!errors.isEmpty()) {
                response.setSuggestion("Please resolve the validation errors before creating this relationship");
            } else if (!warnings.isEmpty()) {
                response.setSuggestion("This relationship can be created but please review the warnings");
            }

            log.info("VALIDATION_RESULT: Valid={}, Errors={}, Warnings={}",
                    response.isValid(), errors.size(), warnings.size());

        } catch (Exception e) {
            log.error("Error validating relationship", e);
            errors.add("Validation failed due to system error");
            response.setValid(false);
            response.setValidationErrors(errors);
        }

        return response;
    }

    private void validateRelationshipLogic(Long userId, Long relatedUserId, RelationshipType relationshipType,
                                           List<String> errors, List<String> warnings) {

        List<UserRelationship> userRelationships = relationshipRepository.findByUserIdAndIsActiveTrue(userId);
        List<UserRelationship> relatedUserRelationships = relationshipRepository.findByUserIdAndIsActiveTrue(relatedUserId);

        switch (relationshipType) {
            case FATHER, MOTHER -> validateParentLogic(userId, relatedUserId, relationshipType, userRelationships, errors, warnings);
            case SON, DAUGHTER -> validateChildLogic(userId, relatedUserId, relationshipType, relatedUserRelationships, errors, warnings);
            case HUSBAND, WIFE -> validateSpouseLogic(userId, relatedUserId, relationshipType, userRelationships, errors, warnings);
            case BROTHER, SISTER -> validateSiblingLogic(userId, relatedUserId, relationshipType, userRelationships, relatedUserRelationships, errors, warnings);
            default -> validateExtendedFamilyLogic(userId, relatedUserId, relationshipType, userRelationships, errors, warnings);
        }
    }

    private void validateParentLogic(Long userId, Long relatedUserId, RelationshipType relationshipType,
                                     List<UserRelationship> userRelationships, List<String> errors, List<String> warnings) {

        // Check if user already has this type of parent
        boolean hasParentOfType = userRelationships.stream()
                .anyMatch(rel -> rel.getRelationshipType() == relationshipType);

        if (hasParentOfType) {
            errors.add("User already has a " + relationshipType.getDisplayName().toLowerCase());
        }

        // Check if user already has both parents
        long parentCount = userRelationships.stream()
                .filter(rel -> rel.getRelationshipType() == RelationshipType.FATHER ||
                        rel.getRelationshipType() == RelationshipType.MOTHER)
                .count();

        if (parentCount >= 2) {
            warnings.add("User already has both parents defined");
        }
    }

    private void validateChildLogic(Long userId, Long relatedUserId, RelationshipType relationshipType,
                                    List<UserRelationship> relatedUserRelationships, List<String> errors, List<String> warnings) {

        // Check if the related user (child) already has parents
        long parentCount = relatedUserRelationships.stream()
                .filter(rel -> rel.getRelationshipType() == RelationshipType.FATHER ||
                        rel.getRelationshipType() == RelationshipType.MOTHER)
                .count();

        if (parentCount >= 2) {
            warnings.add("The child already has both parents defined");
        }
    }

    private void validateSpouseLogic(Long userId, Long relatedUserId, RelationshipType relationshipType,
                                     List<UserRelationship> userRelationships, List<String> errors, List<String> warnings) {

        // Check if user already has a spouse
        boolean hasSpouse = userRelationships.stream()
                .anyMatch(rel -> rel.getRelationshipType() == RelationshipType.HUSBAND ||
                        rel.getRelationshipType() == RelationshipType.WIFE);

        if (hasSpouse) {
            errors.add("User already has a spouse");
        }
    }

    private void validateSiblingLogic(Long userId, Long relatedUserId, RelationshipType relationshipType,
                                      List<UserRelationship> userRelationships, List<UserRelationship> relatedUserRelationships,
                                      List<String> errors, List<String> warnings) {

        // Check if both users have the same parents (if parents are defined)
        Set<Long> userParents = userRelationships.stream()
                .filter(rel -> rel.getRelationshipType() == RelationshipType.FATHER ||
                        rel.getRelationshipType() == RelationshipType.MOTHER)
                .map(UserRelationship::getRelatedUserId)
                .collect(Collectors.toSet());

        Set<Long> relatedUserParents = relatedUserRelationships.stream()
                .filter(rel -> rel.getRelationshipType() == RelationshipType.FATHER ||
                        rel.getRelationshipType() == RelationshipType.MOTHER)
                .map(UserRelationship::getRelatedUserId)
                .collect(Collectors.toSet());

        if (!userParents.isEmpty() && !relatedUserParents.isEmpty()) {
            Set<Long> commonParents = new HashSet<>(userParents);
            commonParents.retainAll(relatedUserParents);

            if (commonParents.isEmpty()) {
                warnings.add("Siblings should typically have at least one common parent");
            }
        }
    }

    private void validateExtendedFamilyLogic(Long userId, Long relatedUserId, RelationshipType relationshipType,
                                             List<UserRelationship> userRelationships, List<String> errors, List<String> warnings) {
        // Additional logic for extended family relationships can be added here
        log.debug("Validating extended family relationship: {}", relationshipType);
    }

    /**
     * FIXED: More intelligent cycle detection that understands family tree semantics
     * Only flags truly problematic cycles, not natural family hierarchies
     */
    private boolean wouldCreateProblematicCycle(Long userId, Long relatedUserId, RelationshipType relationshipType) {
        try {
            log.info("CYCLE_CHECK_START: UserId={}, RelatedUserId={}, RelationshipType={}",
                    userId, relatedUserId, relationshipType);

            // Check for direct contradictory relationships only
            List<UserRelationship> existingRelationships = relationshipRepository
                    .findExistingRelationship(userId, relatedUserId);

            for (UserRelationship existing : existingRelationships) {
                if (isContradictoryRelationship(existing.getRelationshipType(), relationshipType)) {
                    log.warn("CONTRADICTORY_RELATIONSHIP: Existing={}, New={}",
                            existing.getRelationshipType(), relationshipType);
                    return true;
                }
            }

            // Check for impossible generational conflicts
            if (hasGenerationalConflict(userId, relatedUserId, relationshipType)) {
                log.warn("GENERATIONAL_CONFLICT detected");
                return true;
            }

            // Check for impossible parent-child cycles (A parent of B, B parent of A)
            if (hasDirectParentChildCycle(userId, relatedUserId, relationshipType)) {
                log.warn("DIRECT_PARENT_CHILD_CYCLE detected");
                return true;
            }

            log.info("CYCLE_CHECK_PASSED: No problematic cycles detected");
            return false;

        } catch (Exception e) {
            log.error("Error checking for problematic cycles", e);
            return false; // Assume no cycle if check fails
        }
    }

    /**
     * Check if two relationship types are contradictory
     */
    private boolean isContradictoryRelationship(RelationshipType existing, RelationshipType newType) {
        // Same relationship type is not contradictory (might be updating)
        if (existing == newType) {
            return false;
        }

        // Check for direct contradictions
        Map<RelationshipType, Set<RelationshipType>> contradictions = Map.of(
                RelationshipType.FATHER, Set.of(RelationshipType.MOTHER, RelationshipType.SON, RelationshipType.DAUGHTER),
                RelationshipType.MOTHER, Set.of(RelationshipType.FATHER, RelationshipType.SON, RelationshipType.DAUGHTER),
                RelationshipType.SON, Set.of(RelationshipType.FATHER, RelationshipType.MOTHER, RelationshipType.DAUGHTER),
                RelationshipType.DAUGHTER, Set.of(RelationshipType.FATHER, RelationshipType.MOTHER, RelationshipType.SON),
                RelationshipType.HUSBAND, Set.of(RelationshipType.WIFE, RelationshipType.FATHER, RelationshipType.MOTHER),
                RelationshipType.WIFE, Set.of(RelationshipType.HUSBAND, RelationshipType.FATHER, RelationshipType.MOTHER)
        );

        return contradictions.getOrDefault(existing, Collections.emptySet()).contains(newType);
    }

    /**
     * Check for impossible generational conflicts
     */
    private boolean hasGenerationalConflict(Long userId, Long relatedUserId, RelationshipType relationshipType) {
        try {
            // Get existing relationships to check generation levels
            List<UserRelationship> userRelationships = relationshipRepository.findByUserIdAndIsActiveTrue(userId);
            List<UserRelationship> relatedUserRelationships = relationshipRepository.findByUserIdAndIsActiveTrue(relatedUserId);

            int newGenerationLevel = relationshipType.getDefaultGenerationLevel();

            // Check if this would create impossible generation conflicts
            for (UserRelationship rel : userRelationships) {
                if (rel.getRelatedUserId().equals(relatedUserId)) {
                    continue; // Skip existing relationship with same user
                }

                // Check for generation level conflicts through common relatives
                for (UserRelationship relatedRel : relatedUserRelationships) {
                    if (relatedRel.getRelatedUserId().equals(rel.getRelatedUserId())) {
                        // Common relative found - check generation consistency
                        int existingGenLevel = rel.getGenerationLevel();
                        int relatedGenLevel = relatedRel.getGenerationLevel();

                        // Calculate expected generation level
                        int expectedLevel = existingGenLevel - relatedGenLevel;

                        // Allow some tolerance for complex family structures
                        if (Math.abs(expectedLevel - newGenerationLevel) > 2) {
                            log.warn("GENERATION_CONFLICT: Expected={}, Actual={}", expectedLevel, newGenerationLevel);
                            return true;
                        }
                    }
                }
            }

            return false;
        } catch (Exception e) {
            log.error("Error checking generational conflicts", e);
            return false;
        }
    }

    /**
     * Check for direct parent-child cycles (A parent of B AND B parent of A)
     */
    private boolean hasDirectParentChildCycle(Long userId, Long relatedUserId, RelationshipType relationshipType) {
        // Check if we're trying to create a parent relationship
        if (!isParentRelationship(relationshipType)) {
            return false;
        }

        // Check if the related user is already a parent of the user
        List<UserRelationship> relatedUserRelationships = relationshipRepository.findByUserIdAndIsActiveTrue(relatedUserId);

        return relatedUserRelationships.stream()
                .anyMatch(rel -> rel.getRelatedUserId().equals(userId) && isParentRelationship(rel.getRelationshipType()));
    }

    /**
     * Check if a relationship type is a parent relationship
     */
    private boolean isParentRelationship(RelationshipType type) {
        return type == RelationshipType.FATHER || type == RelationshipType.MOTHER;
    }

    private void validateSpouseRelationships(Long userId, Long relatedUserId, RelationshipType relationshipType,
                                             List<String> errors, List<String> warnings) {
        if (relationshipType != RelationshipType.HUSBAND && relationshipType != RelationshipType.WIFE) {
            return;
        }

        // Check if either user already has a spouse
        List<UserRelationship> userSpouseRels = relationshipRepository.findByUserIdAndIsActiveTrue(userId).stream()
                .filter(rel -> rel.getRelationshipType() == RelationshipType.HUSBAND ||
                        rel.getRelationshipType() == RelationshipType.WIFE)
                .collect(Collectors.toList());

        List<UserRelationship> relatedUserSpouseRels = relationshipRepository.findByUserIdAndIsActiveTrue(relatedUserId).stream()
                .filter(rel -> rel.getRelationshipType() == RelationshipType.HUSBAND ||
                        rel.getRelationshipType() == RelationshipType.WIFE)
                .collect(Collectors.toList());

        if (!userSpouseRels.isEmpty()) {
            errors.add("User already has a spouse");
        }

        if (!relatedUserSpouseRels.isEmpty()) {
            errors.add("Related user already has a spouse");
        }
    }

    private void validateParentChildRelationships(Long userId, Long relatedUserId, RelationshipType relationshipType,
                                                  List<String> errors, List<String> warnings) {

        // Check for conflicting parent-child relationships
        if (relationshipType == RelationshipType.FATHER || relationshipType == RelationshipType.MOTHER) {
            // Check if the child already has this type of parent
            List<UserRelationship> existingParents = relationshipRepository.findByUserIdAndIsActiveTrue(relatedUserId).stream()
                    .filter(rel -> rel.getRelationshipType() == relationshipType)
                    .collect(Collectors.toList());

            if (!existingParents.isEmpty()) {
                errors.add("Child already has a " + relationshipType.getDisplayName().toLowerCase());
            }
        }

        if (relationshipType == RelationshipType.SON || relationshipType == RelationshipType.DAUGHTER) {
            // Check if the parent already has too many children (optional warning)
            List<UserRelationship> existingChildren = relationshipRepository.findByUserIdAndIsActiveTrue(userId).stream()
                    .filter(rel -> rel.getRelationshipType() == RelationshipType.SON ||
                            rel.getRelationshipType() == RelationshipType.DAUGHTER)
                    .collect(Collectors.toList());

            if (existingChildren.size() > 10) {
                warnings.add("User has many children already defined");
            }
        }
    }

    private UserNodeDto buildUserNodeDto(UserRelationship relationship) {
        UserNodeDto dto = new UserNodeDto();

        if (relationship.getRelatedUser() != null) {
            dto.setUserId(relationship.getRelatedUser().getId());
            dto.setName(relationship.getRelatedUser().getName());
            dto.setEmail(relationship.getRelatedUser().getEmail());
        }

        dto.setRelationshipType(relationship.getRelationshipType());
        dto.setRelationshipDisplayName(relationship.getRelationshipType().getDisplayName());
        dto.setRelationshipSide(relationship.getRelationshipSide());
        dto.setRelationshipSideDisplayName(relationship.getRelationshipSide().getDisplayName());
        dto.setGenerationLevel(relationship.getGenerationLevel());
        dto.setRelationshipId(relationship.getId());

        return dto;
    }
}
