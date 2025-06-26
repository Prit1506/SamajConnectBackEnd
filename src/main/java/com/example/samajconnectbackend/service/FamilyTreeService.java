package com.example.samajconnectbackend.service;

import com.example.samajconnectbackend.dto.*;
import com.example.samajconnectbackend.entity.*;
import com.example.samajconnectbackend.repository.RelationshipRequestRepository;
import com.example.samajconnectbackend.repository.UserRelationshipRepository;
import com.example.samajconnectbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class FamilyTreeService {

    private final UserRelationshipRepository relationshipRepository;
    private final UserRepository userRepository;
    private final RelationshipRequestRepository requestRepository;
    private final RelationshipValidationService validationService;

    // ==================== CONTROLLER METHOD ORDER ====================

    /**
     * 1. Get complete family tree for a user
     * Corresponds to: GET /user/{userId}
     */
    public FamilyTreeResponse getFamilyTree(Long userId) {
        log.info("Fetching family tree for user: {}", userId);

        // Get all relationships for the user
        List<UserRelationship> relationships = relationshipRepository.findByUserIdAndIsActiveTrue(userId);

        // Get user details
        User rootUser = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Build family tree response
        FamilyTreeResponse response = new FamilyTreeResponse();

        // Set root user
        response.setRootUser(buildUserNodeDto(rootUser, null, true));

        // Build family sides
        response.setFamilySides(buildFamilySides(relationships));

        // Build generations
        response.setGenerations(buildGenerations(relationships));

        // Build stats
        response.setStats(buildRelationshipStats(userId, relationships));

        // Set additional flags
        response.setTotalMembers((long) relationships.size());
        response.setHasSpouse(hasSpouse(relationships));
        response.setHasChildren(hasChildren(relationships));
        response.setHasParents(hasParents(relationships));

        return response;
    }

    /**
     * 2. Add a new relationship
     * Corresponds to: POST /relationship
     */
    public ApiResponse<String> addRelationship(AddRelationshipRequest request) {
        try {
            log.info("Adding relationship: {} -> {} as {}",
                    request.getRequestingUserId(), request.getRelatedUserId(), request.getRelationshipType());

            // Validate users exist
            User requestingUser = userRepository.findById(request.getRequestingUserId())
                    .orElseThrow(() -> new RuntimeException("Requesting user not found"));
            User relatedUser = userRepository.findById(request.getRelatedUserId())
                    .orElseThrow(() -> new RuntimeException("Related user not found"));

            log.info("USERS_DEBUG: Requesting User ID={}, Name={}, Gender={}",
                    requestingUser.getId(), requestingUser.getName(), requestingUser.getGender());
            log.info("USERS_DEBUG: Related User ID={}, Name={}, Gender={}",
                    relatedUser.getId(), relatedUser.getName(), relatedUser.getGender());

            // Validate that the target user's gender matches the relationship role
            ApiResponse<String> targetGenderValidation = validateTargetUserGenderCompatibility(relatedUser, request.getRelationshipType());
            if (!targetGenderValidation.isSuccess()) {
                return targetGenderValidation;
            }

            // Validate relationship using validation service
            RelationshipValidationResponse validation = validationService.validateRelationship(
                    request.getRequestingUserId(), request.getRelatedUserId(), request.getRelationshipType());

            if (!validation.isValid()) {
                return ApiResponse.error("Validation failed: " + String.join(", ", validation.getValidationErrors()));
            }

            if (request.isSendRequest()) {
                return createRelationshipRequest(request);
            } else {
                return addDirectRelationship(request, requestingUser, relatedUser);
            }

        } catch (Exception e) {
            log.error("Error adding relationship", e);
            return ApiResponse.error("Failed to add relationship: " + e.getMessage());
        }
    }

    /**
     * 3. Update an existing relationship
     * Corresponds to: PUT /relationship
     */
    public ApiResponse<String> updateRelationship(UpdateRelationshipRequest request) {
        try {
            UserRelationship relationship = relationshipRepository.findById(request.getRelationshipId())
                    .orElseThrow(() -> new RuntimeException("Relationship not found"));

            if (request.getRelationshipType() != null) {
                relationship.setRelationshipType(request.getRelationshipType());
            }
            if (request.getRelationshipSide() != null) {
                relationship.setRelationshipSide(request.getRelationshipSide());
            }
            if (request.getGenerationLevel() != null) {
                relationship.setGenerationLevel(request.getGenerationLevel());
            }

            relationship.setUpdatedAt(LocalDateTime.now());
            relationshipRepository.save(relationship);

            return ApiResponse.success("Relationship updated successfully");

        } catch (Exception e) {
            log.error("Error updating relationship", e);
            return ApiResponse.error("Failed to update relationship: " + e.getMessage());
        }
    }

    /**
     * 4. Remove a relationship
     * Corresponds to: DELETE /relationship/{userId}/{relatedUserId}
     */
    public ApiResponse<String> removeRelationship(Long userId, Long relatedUserId) {
        try {
            relationshipRepository.softDeleteRelationship(userId, relatedUserId);
            relationshipRepository.softDeleteRelationship(relatedUserId, userId);
            return ApiResponse.success("Relationship removed successfully");
        } catch (Exception e) {
            log.error("Error removing relationship", e);
            return ApiResponse.error("Failed to remove relationship: " + e.getMessage());
        }
    }

    /**
     * 5. Get pending relationship requests for a user
     * Corresponds to: GET /requests/pending/{userId}
     */
    public List<RelationshipRequestDto> getPendingRequests(Long userId) {
        List<RelationshipRequest> requests = requestRepository.findByTargetUserIdAndStatus(userId, RequestStatus.PENDING);
        return requests.stream().map(this::buildRelationshipRequestDto).collect(Collectors.toList());
    }

    /**
     * 6. Get sent relationship requests by a user
     * Corresponds to: GET /requests/sent/{userId}
     */
    public List<RelationshipRequestDto> getSentRequests(Long userId) {
        List<RelationshipRequest> requests = requestRepository.findByRequesterUserIdAndStatus(userId, RequestStatus.PENDING);
        return requests.stream().map(this::buildRelationshipRequestDto).collect(Collectors.toList());
    }

    /**
     * 7. Respond to a relationship request
     * Corresponds to: POST /requests/respond
     */
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<String> respondToRequest(RespondToRequestDto response) {
        try {
            log.info("Attempting to respond to request with ID: {}", response.getRequestId());

            // Find the request
            RelationshipRequest request = requestRepository.findById(response.getRequestId())
                    .orElseThrow(() -> new IllegalArgumentException("Request not found with ID: " + response.getRequestId()));

            log.info("Found request: ID={}, Status={}, TargetUserId={}, RequesterUserId={}, RelationshipType={}",
                    request.getId(), request.getStatus(), request.getTargetUserId(),
                    request.getRequesterUserId(), request.getRelationshipType());

            // Validate the responding user
            if (!request.getTargetUserId().equals(response.getRespondingUserId())) {
                log.warn("Unauthorized response attempt. Request TargetUserId={}, RespondingUserId={}",
                        request.getTargetUserId(), response.getRespondingUserId());
                return ApiResponse.error("Unauthorized to respond to this request");
            }

            // Check if request is still pending
            if (request.getStatus() != RequestStatus.PENDING) {
                log.warn("Request {} has already been responded to. Current status: {}",
                        request.getId(), request.getStatus());
                return ApiResponse.error("Request has already been responded to");
            }

            // Validate the response status
            if (response.getStatus() != RequestStatus.APPROVED && response.getStatus() != RequestStatus.REJECTED) {
                log.error("Invalid response status: {}", response.getStatus());
                return ApiResponse.error("Invalid response status");
            }

            // If approved, check for existing relationships before updating request status
            if (response.getStatus() == RequestStatus.APPROVED) {
                // Check if relationships already exist
                if (checkRelationshipExists(request)) {
                    log.warn("Relationship already exists between users {} and {} with type {}",
                            request.getRequesterUserId(), request.getTargetUserId(), request.getRelationshipType());
                    return ApiResponse.error("Relationship already exists between these users");
                }
            }

            // Update the request
            request.setStatus(response.getStatus());
            request.setRespondedAt(LocalDateTime.now());
            request.setUpdatedAt(LocalDateTime.now());

            RelationshipRequest savedRequest = requestRepository.save(request);
            log.info("Request {} updated successfully with status: {}", savedRequest.getId(), savedRequest.getStatus());

            // If approved, create the actual relationship
            if (response.getStatus() == RequestStatus.APPROVED) {
                createRelationshipsIfNotExists(request, response.getRespondingUserId());
            }

            String message = response.getStatus() == RequestStatus.APPROVED ?
                    "Request approved and relationship created" : "Request rejected";

            log.info("Successfully processed request response: {}", message);
            return ApiResponse.success(message);

        } catch (DataIntegrityViolationException e) {
            log.error("Data integrity violation - relationship may already exist", e);
            return ApiResponse.error("Relationship already exists between these users");
        } catch (Exception e) {
            log.error("Error responding to request with ID: {}", response.getRequestId(), e);
            return ApiResponse.error("Failed to respond to request: " + e.getMessage());
        }
    }

    /**
     * 8. Search family members
     * Corresponds to: POST /search/{userId}
     */
    public FamilyMemberSearchResponse searchFamilyMembers(Long userId, FamilyMemberSearchDto searchDto) {
        try {
            List<UserRelationship> allRelationships = relationshipRepository.findByUserIdAndIsActiveTrue(userId);

            // Filter relationships based on search criteria
            List<UserRelationship> filteredRelationships = allRelationships.stream()
                    .filter(rel -> matchesSearchCriteria(rel, searchDto))
                    .collect(Collectors.toList());

            // Apply pagination
            int start = searchDto.getPage() * searchDto.getSize();
            int end = Math.min(start + searchDto.getSize(), filteredRelationships.size());

            List<UserRelationship> pagedRelationships = filteredRelationships.subList(start, end);

            List<UserNodeDto> members = pagedRelationships.stream()
                    .map(this::buildUserNodeDto)
                    .collect(Collectors.toList());

            FamilyMemberSearchResponse response = new FamilyMemberSearchResponse();
            response.setMembers(members);
            response.setTotalResults((long) filteredRelationships.size());
            response.setCurrentPage(searchDto.getPage());
            response.setTotalPages((int) Math.ceil((double) filteredRelationships.size() / searchDto.getSize()));
            response.setHasNext(end < filteredRelationships.size());
            response.setHasPrevious(searchDto.getPage() > 0);

            return response;

        } catch (Exception e) {
            log.error("Error searching family members", e);
            return new FamilyMemberSearchResponse();
        }
    }

    /**
     * 9. Get family members by generation level
     * Corresponds to: GET /generation/{userId}/{generationLevel}
     */
    public List<UserNodeDto> getFamilyMembersByGeneration(Long userId, Integer generationLevel) {
        List<UserRelationship> relationships = relationshipRepository
                .findByUserIdAndGenerationLevelAndIsActiveTrue(userId, generationLevel);

        return relationships.stream()
                .map(this::buildUserNodeDto)
                .collect(Collectors.toList());
    }

    /**
     * 10. Get family members by relationship side
     * Corresponds to: GET /side/{userId}/{relationshipSide}
     */
    public List<UserNodeDto> getFamilyMembersBySide(Long userId, RelationshipSide side) {
        List<UserRelationship> relationships = relationshipRepository
                .findByUserIdAndRelationshipSideAndIsActiveTrue(userId, side);

        return relationships.stream()
                .map(this::buildUserNodeDto)
                .collect(Collectors.toList());
    }

    /**
     * 11. Get mutual relatives between two users
     * Corresponds to: GET /mutual/{userId1}/{userId2}
     */
    public List<UserNodeDto> getMutualRelatives(Long userId1, Long userId2) {
        List<Long> mutualRelativeIds = relationshipRepository.findMutualRelatives(userId1, userId2);
        return mutualRelativeIds.stream()
                .map(id -> userRepository.findById(id))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(user -> buildUserNodeDto(user, null, false))
                .collect(Collectors.toList());
    }

    // ==================== PRIVATE HELPER METHODS ====================

    /**
     * Create a relationship request
     */
    private ApiResponse<String> createRelationshipRequest(AddRelationshipRequest request) {
        Optional<RelationshipRequest> existingRequest = requestRepository
                .findByRequesterUserIdAndTargetUserIdAndRelationshipTypeAndStatus(
                        request.getRequestingUserId(), request.getRelatedUserId(),
                        request.getRelationshipType(), RequestStatus.PENDING);

        if (existingRequest.isPresent()) {
            return ApiResponse.error("Relationship request already exists");
        }

        RelationshipRequest relationshipRequest = new RelationshipRequest();
        relationshipRequest.setRequesterUserId(request.getRequestingUserId());
        relationshipRequest.setTargetUserId(request.getRelatedUserId());
        relationshipRequest.setRelationshipType(request.getRelationshipType());

        // Use enum's built-in methods for defaults
        relationshipRequest.setRelationshipSide(request.getRelationshipSide() != null ?
                request.getRelationshipSide() : request.getRelationshipType().getDefaultRelationshipSide());
        relationshipRequest.setGenerationLevel(request.getGenerationLevel() != null ?
                request.getGenerationLevel() : request.getRelationshipType().getDefaultGenerationLevel());

        relationshipRequest.setRequestMessage(request.getRequestMessage());
        relationshipRequest.setStatus(RequestStatus.PENDING);
        relationshipRequest.setCreatedAt(LocalDateTime.now());
        relationshipRequest.setUpdatedAt(LocalDateTime.now());

        log.info("RELATIONSHIP_REQUEST_CREATED: RequesterUserId={}, TargetUserId={}, RelationshipType={}, RelationshipSide={}, GenerationLevel={}",
                relationshipRequest.getRequesterUserId(), relationshipRequest.getTargetUserId(),
                relationshipRequest.getRelationshipType(), relationshipRequest.getRelationshipSide(),
                relationshipRequest.getGenerationLevel());

        requestRepository.save(relationshipRequest);

        return ApiResponse.success("Relationship request sent successfully");
    }

    /**
     * Add direct relationship without request - CORRECTED VERSION
     */
    private ApiResponse<String> addDirectRelationship(AddRelationshipRequest request, User requestingUser, User relatedUser) {
        try {
            log.info("DIRECT_RELATIONSHIP_START: RequestingUser ID={}, Gender={}, RelatedUser ID={}, Gender={}, RelationshipType={}",
                    requestingUser.getId(), requestingUser.getGender(), relatedUser.getId(), relatedUser.getGender(), request.getRelationshipType());

            // Create forward relationship (what the requesting user claims about the related user)
            UserRelationship relationship = new UserRelationship();
            relationship.setUserId(request.getRequestingUserId());
            relationship.setRelatedUserId(request.getRelatedUserId());
            relationship.setRelationshipType(request.getRelationshipType());

            // Use enum's built-in methods for defaults
            relationship.setRelationshipSide(request.getRelationshipSide() != null ?
                    request.getRelationshipSide() : request.getRelationshipType().getDefaultRelationshipSide());
            relationship.setGenerationLevel(request.getGenerationLevel() != null ?
                    request.getGenerationLevel() : request.getRelationshipType().getDefaultGenerationLevel());

            relationship.setCreatedBy(request.getRequestingUserId());
            relationship.setIsActive(true);
            relationship.setCreatedAt(LocalDateTime.now());
            relationship.setUpdatedAt(LocalDateTime.now());

            log.info("FORWARD_RELATIONSHIP_SAVING: UserId={}, RelatedUserId={}, RelationshipType={}, RelationshipSide={}, GenerationLevel={}",
                    relationship.getUserId(), relationship.getRelatedUserId(), relationship.getRelationshipType(),
                    relationship.getRelationshipSide(), relationship.getGenerationLevel());

            relationshipRepository.save(relationship);
            log.info("FORWARD_RELATIONSHIP_SAVED: Relationship ID={}", relationship.getId());

            // Create reverse relationship - CORRECTED: Pass both users for proper calculation
            RelationshipType reverseType = getReverseRelationshipType(request.getRelationshipType(), requestingUser, relatedUser);

            UserRelationship reverseRelationship = new UserRelationship();
            reverseRelationship.setUserId(request.getRelatedUserId());
            reverseRelationship.setRelatedUserId(request.getRequestingUserId());
            reverseRelationship.setRelationshipType(reverseType);
            reverseRelationship.setRelationshipSide(reverseType.getDefaultRelationshipSide());
            reverseRelationship.setGenerationLevel(-relationship.getGenerationLevel());
            reverseRelationship.setCreatedBy(request.getRequestingUserId());
            reverseRelationship.setIsActive(true);
            reverseRelationship.setCreatedAt(LocalDateTime.now());
            reverseRelationship.setUpdatedAt(LocalDateTime.now());

            log.info("REVERSE_RELATIONSHIP_SAVING: UserId={}, RelatedUserId={}, RelationshipType={}, RelationshipSide={}, GenerationLevel={}",
                    reverseRelationship.getUserId(), reverseRelationship.getRelatedUserId(), reverseRelationship.getRelationshipType(),
                    reverseRelationship.getRelationshipSide(), reverseRelationship.getGenerationLevel());

            relationshipRepository.save(reverseRelationship);
            log.info("REVERSE_RELATIONSHIP_SAVED: Relationship ID={}", reverseRelationship.getId());

            return ApiResponse.success("Relationship added successfully");

        } catch (DataIntegrityViolationException e) {
            log.error("Data integrity violation while adding direct relationship", e);
            return ApiResponse.error("Relationship already exists or data constraint violation");
        }
    }

    /**
     * Check if relationship already exists
     */
    private boolean checkRelationshipExists(RelationshipRequest request) {
        // Check if any relationship exists between these two users (bidirectional)
        boolean relationshipExists = relationshipRepository.existsByUserIdAndRelatedUserIdAndIsActive(
                request.getRequesterUserId(),
                request.getTargetUserId(),
                true) ||
                relationshipRepository.existsByUserIdAndRelatedUserIdAndIsActive(
                        request.getTargetUserId(),
                        request.getRequesterUserId(),
                        true);

        log.info("RELATIONSHIP_EXISTS_CHECK: RequesterUserId={}, TargetUserId={}, Exists={}",
                request.getRequesterUserId(), request.getTargetUserId(), relationshipExists);

        return relationshipExists;
    }

    /**
     * Create relationships with additional safety checks and proper gender handling - CORRECTED VERSION
     */
    private void createRelationshipsIfNotExists(RelationshipRequest request, Long respondingUserId) {
        log.info("CREATING_RELATIONSHIPS_START: RequestId={}, RequesterUserId={}, TargetUserId={}, RelationshipType={}",
                request.getId(), request.getRequesterUserId(), request.getTargetUserId(), request.getRelationshipType());

        try {
            // Get user details to determine gender for reverse relationship
            User requesterUser = userRepository.findById(request.getRequesterUserId())
                    .orElseThrow(() -> new RuntimeException("Requester user not found"));
            User targetUser = userRepository.findById(request.getTargetUserId())
                    .orElseThrow(() -> new RuntimeException("Target user not found"));

            log.info("USER_DETAILS: RequesterUser ID={}, Name={}, Gender={}",
                    requesterUser.getId(), requesterUser.getName(), requesterUser.getGender());
            log.info("USER_DETAILS: TargetUser ID={}, Name={}, Gender={}",
                    targetUser.getId(), targetUser.getName(), targetUser.getGender());

            // Create or update the requester's relationship (original request)
            createOrUpdateRelationship(
                    request.getRequesterUserId(),
                    request.getTargetUserId(),
                    request.getRelationshipType(),
                    request.getRelationshipSide(),
                    request.getGenerationLevel(),
                    respondingUserId,
                    "REQUESTER"
            );

            // Create or update the target user's relationship (reverse with proper gender consideration)
            // CORRECTED: Pass both users for proper reverse relationship calculation
            log.info("CALCULATING_REVERSE_TYPE: OriginalType={}, RequesterGender={}, TargetGender={}",
                    request.getRelationshipType(), requesterUser.getGender(), targetUser.getGender());

            RelationshipType reverseType = getReverseRelationshipType(request.getRelationshipType(), requesterUser, targetUser);

            log.info("CALCULATED_REVERSE_TYPE: {}", reverseType);

            createOrUpdateRelationship(
                    request.getTargetUserId(),
                    request.getRequesterUserId(),
                    reverseType,
                    reverseType.getDefaultRelationshipSide(),
                    -request.getGenerationLevel(),
                    respondingUserId,
                    "TARGET"
            );

        } catch (DataIntegrityViolationException e) {
            log.warn("Relationship creation failed due to constraint violation - relationship may have been created by another process", e);
            // Don't throw the exception as the request was already approved
        }
    }

    /**
     * Create or update a relationship - handles both new creation and updating existing relationships
     */
    private void createOrUpdateRelationship(Long userId, Long relatedUserId, RelationshipType relationshipType,
                                            RelationshipSide relationshipSide, Integer generationLevel,
                                            Long createdBy, String relationshipDirection) {

        // Check if relationship already exists
        Optional<UserRelationship> existingRelationship = relationshipRepository
                .findByUserIdAndRelatedUserIdAndIsActiveTrue(userId, relatedUserId);

        if (existingRelationship.isPresent()) {
            // Update existing relationship
            UserRelationship relationship = existingRelationship.get();

            log.info("{}_RELATIONSHIP_UPDATING: Existing ID={}, Old Type={}, New Type={}",
                    relationshipDirection, relationship.getId(), relationship.getRelationshipType(), relationshipType);

            relationship.setRelationshipType(relationshipType);
            relationship.setRelationshipSide(relationshipSide);
            relationship.setGenerationLevel(generationLevel);
            relationship.setUpdatedAt(LocalDateTime.now());

            relationshipRepository.save(relationship);
            log.info("{}_RELATIONSHIP_UPDATED: ID={}, New Type={}",
                    relationshipDirection, relationship.getId(), relationshipType);

        } else {
            // Create new relationship
            UserRelationship relationship = new UserRelationship();
            relationship.setUserId(userId);
            relationship.setRelatedUserId(relatedUserId);
            relationship.setRelationshipType(relationshipType);
            relationship.setRelationshipSide(relationshipSide);
            relationship.setGenerationLevel(generationLevel);
            relationship.setCreatedBy(createdBy);
            relationship.setIsActive(true);
            relationship.setCreatedAt(LocalDateTime.now());
            relationship.setUpdatedAt(LocalDateTime.now());

            log.info("{}_RELATIONSHIP_CREATING: UserId={}, RelatedUserId={}, RelationshipType={}, RelationshipSide={}, GenerationLevel={}",
                    relationshipDirection, relationship.getUserId(), relationship.getRelatedUserId(),
                    relationship.getRelationshipType(), relationship.getRelationshipSide(),
                    relationship.getGenerationLevel());

            relationshipRepository.save(relationship);
            log.info("{}_RELATIONSHIP_CREATED: ID={}, Type={}",
                    relationshipDirection, relationship.getId(), relationshipType);
        }
    }

    /**
     * CORRECTED: Get reverse relationship type - handles ALL relationship types correctly
     * This method determines what relationship the target user should have with the requester
     *
     * @param originalType The relationship type being claimed
     * @param requesterUser The user making the claim
     * @param targetUser The user being assigned the role
     * @return The reverse relationship type
     */
    private RelationshipType getReverseRelationshipType(RelationshipType originalType, User requesterUser, User targetUser) {
        log.info("REVERSE_TYPE_CALCULATION_START: OriginalType={}, RequesterGender={}, TargetGender={}",
                originalType, requesterUser.getGender(), targetUser.getGender());

        boolean isRequesterMale = "MALE".equalsIgnoreCase(requesterUser.getGender()) || "M".equalsIgnoreCase(requesterUser.getGender());
        boolean isRequesterFemale = "FEMALE".equalsIgnoreCase(requesterUser.getGender()) || "F".equalsIgnoreCase(requesterUser.getGender());

        log.info("REVERSE_TYPE_CALCULATION: isRequesterMale={}, isRequesterFemale={}", isRequesterMale, isRequesterFemale);

        RelationshipType reverseType = switch (originalType) {
            // Parent-Child relationships: reverse depends on requester's gender
            case FATHER -> {
                RelationshipType result = isRequesterMale ? RelationshipType.SON : RelationshipType.DAUGHTER;
                log.info("REVERSE_TYPE_CALCULATION: FATHER -> {} (based on requester gender)", result);
                yield result;
            }
            case MOTHER -> {
                RelationshipType result = isRequesterMale ? RelationshipType.SON : RelationshipType.DAUGHTER;
                log.info("REVERSE_TYPE_CALCULATION: MOTHER -> {} (based on requester gender)", result);
                yield result;
            }
            case SON -> {
                log.info("REVERSE_TYPE_CALCULATION: SON -> FATHER || MOTHER");
                yield isRequesterMale ? RelationshipType.FATHER : RelationshipType.MOTHER;
            }
            case DAUGHTER -> {
                log.info("REVERSE_TYPE_CALCULATION: DAUGHTER -> MOTHER || FATHER");
                yield isRequesterMale ? RelationshipType.FATHER : RelationshipType.MOTHER;
            }

            // Spouse relationships: fixed reverse
            case HUSBAND -> {
                log.info("REVERSE_TYPE_CALCULATION: HUSBAND -> WIFE");
                yield RelationshipType.WIFE;
            }
            case WIFE -> {
                log.info("REVERSE_TYPE_CALCULATION: WIFE -> HUSBAND");
                yield RelationshipType.HUSBAND;
            }

            // Sibling relationships: reverse depends on requester's gender
            case BROTHER -> {
                RelationshipType result = isRequesterMale ? RelationshipType.BROTHER : RelationshipType.SISTER;
                log.info("REVERSE_TYPE_CALCULATION: BROTHER -> {} (based on requester gender)", result);
                yield result;
            }
            case SISTER -> {
                RelationshipType result = isRequesterMale ? RelationshipType.BROTHER : RelationshipType.SISTER;
                log.info("REVERSE_TYPE_CALCULATION: SISTER -> {} (based on requester gender)", result);
                yield result;
            }

            // Grandparent-Grandchild relationships: reverse depends on requester's gender
            case PATERNAL_GRANDFATHER -> {
                RelationshipType result = isRequesterMale ? RelationshipType.GRANDSON : RelationshipType.GRANDDAUGHTER;
                log.info("REVERSE_TYPE_CALCULATION: PATERNAL_GRANDFATHER -> {} (based on requester gender)", result);
                yield result;
            }
            case PATERNAL_GRANDMOTHER -> {
                RelationshipType result = isRequesterMale ? RelationshipType.GRANDSON : RelationshipType.GRANDDAUGHTER;
                log.info("REVERSE_TYPE_CALCULATION: PATERNAL_GRANDMOTHER -> {} (based on requester gender)", result);
                yield result;
            }
            case MATERNAL_GRANDFATHER -> {
                RelationshipType result = isRequesterMale ? RelationshipType.GRANDSON : RelationshipType.GRANDDAUGHTER;
                log.info("REVERSE_TYPE_CALCULATION: MATERNAL_GRANDFATHER -> {} (based on requester gender)", result);
                yield result;
            }
            case MATERNAL_GRANDMOTHER -> {
                RelationshipType result = isRequesterMale ? RelationshipType.GRANDSON : RelationshipType.GRANDDAUGHTER;
                log.info("REVERSE_TYPE_CALCULATION: MATERNAL_GRANDMOTHER -> {} (based on requester gender)", result);
                yield result;
            }
            case GRANDSON -> {
                // Default to paternal grandfather - could be enhanced with more context
                log.info("REVERSE_TYPE_CALCULATION: GRANDSON -> PATERNAL_GRANDFATHER");
                yield RelationshipType.PATERNAL_GRANDFATHER;
            }
            case GRANDDAUGHTER -> {
                // Default to paternal grandmother - could be enhanced with more context
                log.info("REVERSE_TYPE_CALCULATION: GRANDDAUGHTER -> PATERNAL_GRANDMOTHER");
                yield RelationshipType.PATERNAL_GRANDMOTHER;
            }

            // Uncle/Aunt - Nephew/Niece relationships: reverse depends on requester's gender
            case PATERNAL_UNCLE -> {
                RelationshipType result = isRequesterMale ? RelationshipType.NEPHEW : RelationshipType.NIECE;
                log.info("REVERSE_TYPE_CALCULATION: PATERNAL_UNCLE -> {} (based on requester gender)", result);
                yield result;
            }
            case PATERNAL_AUNT -> {
                RelationshipType result = isRequesterMale ? RelationshipType.NEPHEW : RelationshipType.NIECE;
                log.info("REVERSE_TYPE_CALCULATION: PATERNAL_AUNT -> {} (based on requester gender)", result);
                yield result;
            }
            case MATERNAL_UNCLE -> {
                RelationshipType result = isRequesterMale ? RelationshipType.NEPHEW : RelationshipType.NIECE;
                log.info("REVERSE_TYPE_CALCULATION: MATERNAL_UNCLE -> {} (based on requester gender)", result);
                yield result;
            }
            case MATERNAL_AUNT -> {
                RelationshipType result = isRequesterMale ? RelationshipType.NEPHEW : RelationshipType.NIECE;
                log.info("REVERSE_TYPE_CALCULATION: MATERNAL_AUNT -> {} (based on requester gender)", result);
                yield result;
            }
            case NEPHEW -> {
                // Default to paternal uncle - could be enhanced with more context
                log.info("REVERSE_TYPE_CALCULATION: NEPHEW -> PATERNAL_UNCLE");
                yield RelationshipType.PATERNAL_UNCLE;
            }
            case NIECE -> {
                // Default to paternal aunt - could be enhanced with more context
                log.info("REVERSE_TYPE_CALCULATION: NIECE -> PATERNAL_AUNT");
                yield RelationshipType.PATERNAL_AUNT;
            }

            // Cousin relationships: reverse depends on requester's gender
            case PATERNAL_COUSIN_BROTHER -> {
                RelationshipType result = isRequesterMale ? RelationshipType.PATERNAL_COUSIN_BROTHER : RelationshipType.PATERNAL_COUSIN_SISTER;
                log.info("REVERSE_TYPE_CALCULATION: PATERNAL_COUSIN_BROTHER -> {} (based on requester gender)", result);
                yield result;
            }
            case PATERNAL_COUSIN_SISTER -> {
                RelationshipType result = isRequesterMale ? RelationshipType.PATERNAL_COUSIN_BROTHER : RelationshipType.PATERNAL_COUSIN_SISTER;
                log.info("REVERSE_TYPE_CALCULATION: PATERNAL_COUSIN_SISTER -> {} (based on requester gender)", result);
                yield result;
            }
            case MATERNAL_COUSIN_BROTHER -> {
                RelationshipType result = isRequesterMale ? RelationshipType.MATERNAL_COUSIN_BROTHER : RelationshipType.MATERNAL_COUSIN_SISTER;
                log.info("REVERSE_TYPE_CALCULATION: MATERNAL_COUSIN_BROTHER -> {} (based on requester gender)", result);
                yield result;
            }
            case MATERNAL_COUSIN_SISTER -> {
                RelationshipType result = isRequesterMale ? RelationshipType.MATERNAL_COUSIN_BROTHER : RelationshipType.MATERNAL_COUSIN_SISTER;
                log.info("REVERSE_TYPE_CALCULATION: MATERNAL_COUSIN_SISTER -> {} (based on requester gender)", result);
                yield result;
            }

            // In-Law relationships: fixed reverse
            case FATHER_IN_LAW -> {
                log.info("REVERSE_TYPE_CALCULATION: FATHER_IN_LAW -> SON_IN_LAW");
                yield RelationshipType.SON_IN_LAW;
            }
            case MOTHER_IN_LAW -> {
                log.info("REVERSE_TYPE_CALCULATION: MOTHER_IN_LAW -> DAUGHTER_IN_LAW");
                yield RelationshipType.DAUGHTER_IN_LAW;
            }
            case BROTHER_IN_LAW -> {
                log.info("REVERSE_TYPE_CALCULATION: BROTHER_IN_LAW -> SISTER_IN_LAW");
                yield RelationshipType.SISTER_IN_LAW;
            }
            case SISTER_IN_LAW -> {
                log.info("REVERSE_TYPE_CALCULATION: SISTER_IN_LAW -> BROTHER_IN_LAW");
                yield RelationshipType.BROTHER_IN_LAW;
            }
            case SON_IN_LAW -> {
                log.info("REVERSE_TYPE_CALCULATION: SON_IN_LAW -> FATHER_IN_LAW");
                yield RelationshipType.FATHER_IN_LAW;
            }
            case DAUGHTER_IN_LAW -> {
                log.info("REVERSE_TYPE_CALCULATION: DAUGHTER_IN_LAW -> MOTHER_IN_LAW");
                yield RelationshipType.MOTHER_IN_LAW;
            }

            // Great-grandparent relationships: reverse depends on requester's gender
            case GREAT_GRANDFATHER -> {
                RelationshipType result = isRequesterMale ? RelationshipType.GREAT_GRANDSON : RelationshipType.GREAT_GRANDDAUGHTER;
                log.info("REVERSE_TYPE_CALCULATION: GREAT_GRANDFATHER -> {} (based on requester gender)", result);
                yield result;
            }
            case GREAT_GRANDMOTHER -> {
                RelationshipType result = isRequesterMale ? RelationshipType.GREAT_GRANDSON : RelationshipType.GREAT_GRANDDAUGHTER;
                log.info("REVERSE_TYPE_CALCULATION: GREAT_GRANDMOTHER -> {} (based on requester gender)", result);
                yield result;
            }
            case GREAT_GRANDSON -> {
                log.info("REVERSE_TYPE_CALCULATION: GREAT_GRANDSON -> GREAT_GRANDFATHER");
                yield RelationshipType.GREAT_GRANDFATHER;
            }
            case GREAT_GRANDDAUGHTER -> {
                log.info("REVERSE_TYPE_CALCULATION: GREAT_GRANDDAUGHTER -> GREAT_GRANDMOTHER");
                yield RelationshipType.GREAT_GRANDMOTHER;
            }

            // Step family relationships: reverse depends on requester's gender
            case STEP_FATHER -> {
                RelationshipType result = isRequesterMale ? RelationshipType.STEP_SON : RelationshipType.STEP_DAUGHTER;
                log.info("REVERSE_TYPE_CALCULATION: STEP_FATHER -> {} (based on requester gender)", result);
                yield result;
            }
            case STEP_MOTHER -> {
                RelationshipType result = isRequesterMale ? RelationshipType.STEP_SON : RelationshipType.STEP_DAUGHTER;
                log.info("REVERSE_TYPE_CALCULATION: STEP_MOTHER -> {} (based on requester gender)", result);
                yield result;
            }
            case STEP_BROTHER -> {
                RelationshipType result = isRequesterMale ? RelationshipType.STEP_BROTHER : RelationshipType.STEP_SISTER;
                log.info("REVERSE_TYPE_CALCULATION: STEP_BROTHER -> {} (based on requester gender)", result);
                yield result;
            }
            case STEP_SISTER -> {
                RelationshipType result = isRequesterMale ? RelationshipType.STEP_BROTHER : RelationshipType.STEP_SISTER;
                log.info("REVERSE_TYPE_CALCULATION: STEP_SISTER -> {} (based on requester gender)", result);
                yield result;
            }
            case STEP_SON -> {
                log.info("REVERSE_TYPE_CALCULATION: STEP_SON -> STEP_FATHER");
                yield RelationshipType.STEP_FATHER;
            }
            case STEP_DAUGHTER -> {
                log.info("REVERSE_TYPE_CALCULATION: STEP_DAUGHTER -> STEP_MOTHER");
                yield RelationshipType.STEP_MOTHER;
            }

            default -> {
                log.info("REVERSE_TYPE_CALCULATION: DEFAULT -> {}", originalType);
                yield originalType; // For any unhandled relationships, return same
            }
        };

        log.info("REVERSE_TYPE_CALCULATION_FINAL: OriginalType={}, RequesterGender={}, ReverseType={}",
                originalType, requesterUser.getGender(), reverseType);

        return reverseType;
    }

    /**
     * Validate that the target user's gender matches the relationship role they're being assigned
     * This validates that the TARGET user can fulfill the relationship role being claimed
     */
    private ApiResponse<String> validateTargetUserGenderCompatibility(User targetUser, RelationshipType relationshipType) {
        String targetGender = targetUser.getGender();
        log.info("TARGET_GENDER_VALIDATION: Target User ID={}, Gender={}, RelationshipType={}",
                targetUser.getId(), targetGender, relationshipType);

        if (targetGender == null) {
            return ApiResponse.error("Target user gender is not specified");
        }

        boolean isTargetMale = "MALE".equalsIgnoreCase(targetGender) || "M".equalsIgnoreCase(targetGender);
        boolean isTargetFemale = "FEMALE".equalsIgnoreCase(targetGender) || "F".equalsIgnoreCase(targetGender);

        log.info("TARGET_GENDER_VALIDATION: isTargetMale={}, isTargetFemale={}", isTargetMale, isTargetFemale);

        // Check if the TARGET user's gender matches the relationship role they're being assigned
        switch (relationshipType) {
            // Male-only roles (target must be male to be assigned these roles)
            case FATHER, HUSBAND, SON, BROTHER,
                 PATERNAL_GRANDFATHER, MATERNAL_GRANDFATHER,
                 PATERNAL_UNCLE, MATERNAL_UNCLE,
                 PATERNAL_COUSIN_BROTHER, MATERNAL_COUSIN_BROTHER,
                 FATHER_IN_LAW, BROTHER_IN_LAW, SON_IN_LAW,
                 NEPHEW, GRANDSON, GREAT_GRANDFATHER, GREAT_GRANDSON,
                 STEP_FATHER, STEP_BROTHER, STEP_SON:
                if (!isTargetMale) {
                    log.warn("TARGET_GENDER_VALIDATION: FAILED - Target user must be male to be assigned {} role", relationshipType.getDisplayName());
                    return ApiResponse.error("Cannot assign " + relationshipType.getDisplayName() + " role to a female user");
                }
                break;

            // Female-only roles (target must be female to be assigned these roles)
            case MOTHER, WIFE, DAUGHTER, SISTER,
                 PATERNAL_GRANDMOTHER, MATERNAL_GRANDMOTHER,
                 PATERNAL_AUNT, MATERNAL_AUNT,
                 PATERNAL_COUSIN_SISTER, MATERNAL_COUSIN_SISTER,
                 MOTHER_IN_LAW, SISTER_IN_LAW, DAUGHTER_IN_LAW,
                 NIECE, GRANDDAUGHTER, GREAT_GRANDMOTHER, GREAT_GRANDDAUGHTER,
                 STEP_MOTHER, STEP_SISTER, STEP_DAUGHTER:
                if (!isTargetFemale) {
                    log.warn("TARGET_GENDER_VALIDATION: FAILED - Target user must be female to be assigned {} role", relationshipType.getDisplayName());
                    return ApiResponse.error("Cannot assign " + relationshipType.getDisplayName() + " role to a male user");
                }
                break;

            default:
                // For other relationship types, no gender restriction
                break;
        }

        log.info("TARGET_GENDER_VALIDATION: PASSED for Target User ID={}, RelationshipType={}", targetUser.getId(), relationshipType);
        return ApiResponse.success("Target gender validation passed");
    }

    // Helper methods remain the same...
    private boolean matchesSearchCriteria(UserRelationship relationship, FamilyMemberSearchDto searchDto) {
        // Name search
        if (searchDto.getQuery() != null && !searchDto.getQuery().isEmpty()) {
            String query = searchDto.getQuery().toLowerCase();
            if (!relationship.getRelatedUser().getName().toLowerCase().contains(query) &&
                    !relationship.getRelatedUser().getEmail().toLowerCase().contains(query)) {
                return false;
            }
        }

        // Relationship type filter
        if (searchDto.getRelationshipTypes() != null && !searchDto.getRelationshipTypes().isEmpty()) {
            if (!searchDto.getRelationshipTypes().contains(relationship.getRelationshipType().name())) {
                return false;
            }
        }

        // Relationship side filter
        if (searchDto.getRelationshipSides() != null && !searchDto.getRelationshipSides().isEmpty()) {
            if (!searchDto.getRelationshipSides().contains(relationship.getRelationshipSide().name())) {
                return false;
            }
        }

        // Generation level filter
        if (searchDto.getGenerationLevels() != null && !searchDto.getGenerationLevels().isEmpty()) {
            if (!searchDto.getGenerationLevels().contains(relationship.getGenerationLevel())) {
                return false;
            }
        }

        // Samaj filter
        if (searchDto.getSamajId() != null) {
            if (relationship.getRelatedUser().getSamaj() == null ||
                    !relationship.getRelatedUser().getSamaj().getId().equals(searchDto.getSamajId())) {
                return false;
            }
        }

        return true;
    }

    private Map<String, FamilySideDto> buildFamilySides(List<UserRelationship> relationships) {
        Map<String, FamilySideDto> familySides = new HashMap<>();

        Map<RelationshipSide, List<UserRelationship>> relationshipsBySide =
                relationships.stream().collect(Collectors.groupingBy(UserRelationship::getRelationshipSide));

        for (RelationshipSide side : RelationshipSide.values()) {
            List<UserRelationship> sideRelationships = relationshipsBySide.getOrDefault(side, new ArrayList<>());

            FamilySideDto sideDto = new FamilySideDto();
            sideDto.setSideName(side.name());
            sideDto.setSideDisplayName(side.getDisplayName());
            sideDto.setSideDescription(side.getDescription());
            sideDto.setMemberCount((long) sideRelationships.size());

            Map<Integer, List<UserNodeDto>> generationMembers = sideRelationships.stream()
                    .collect(Collectors.groupingBy(
                            UserRelationship::getGenerationLevel,
                            Collectors.mapping(this::buildUserNodeDto, Collectors.toList())
                    ));

            sideDto.setGenerationMembers(generationMembers);
            sideDto.setGenerations(buildGenerationsForSide(sideRelationships));

            familySides.put(side.name(), sideDto);
        }

        return familySides;
    }

    private List<GenerationDto> buildGenerations(List<UserRelationship> relationships) {
        Map<Integer, List<UserRelationship>> relationshipsByGeneration =
                relationships.stream().collect(Collectors.groupingBy(UserRelationship::getGenerationLevel));

        return relationshipsByGeneration.entrySet().stream()
                .map(entry -> {
                    Integer level = entry.getKey();
                    List<UserRelationship> genRelationships = entry.getValue();

                    GenerationDto genDto = new GenerationDto();
                    genDto.setLevel(level);
                    genDto.setLevelName(getGenerationName(level));
                    genDto.setLevelDescription(getGenerationDescription(level));
                    genDto.setMemberCount((long) genRelationships.size());

                    Map<String, List<UserNodeDto>> sideMembers = genRelationships.stream()
                            .collect(Collectors.groupingBy(
                                    rel -> rel.getRelationshipSide().name(),
                                    Collectors.mapping(this::buildUserNodeDto, Collectors.toList())
                            ));

                    genDto.setSideMembers(sideMembers);
                    genDto.setAllMembers(genRelationships.stream()
                            .map(this::buildUserNodeDto)
                            .collect(Collectors.toList()));

                    return genDto;
                })
                .sorted(Comparator.comparingInt(GenerationDto::getLevel).reversed())
                .collect(Collectors.toList());
    }

    private List<GenerationDto> buildGenerationsForSide(List<UserRelationship> relationships) {
        Map<Integer, List<UserRelationship>> relationshipsByGeneration =
                relationships.stream().collect(Collectors.groupingBy(UserRelationship::getGenerationLevel));

        return relationshipsByGeneration.entrySet().stream()
                .map(entry -> {
                    Integer level = entry.getKey();
                    List<UserRelationship> genRelationships = entry.getValue();

                    GenerationDto genDto = new GenerationDto();
                    genDto.setLevel(level);
                    genDto.setLevelName(getGenerationName(level));
                    genDto.setMemberCount((long) genRelationships.size());
                    genDto.setAllMembers(genRelationships.stream()
                            .map(this::buildUserNodeDto)
                            .collect(Collectors.toList()));

                    return genDto;
                })
                .sorted(Comparator.comparingInt(GenerationDto::getLevel).reversed())
                .collect(Collectors.toList());
    }

    private RelationshipStatsDto buildRelationshipStats(Long userId, List<UserRelationship> relationships) {
        RelationshipStatsDto stats = new RelationshipStatsDto();

        stats.setTotalRelationships((long) relationships.size());

        Map<String, Long> relationshipsBySide = relationships.stream()
                .collect(Collectors.groupingBy(
                        rel -> rel.getRelationshipSide().name(),
                        Collectors.counting()
                ));
        stats.setRelationshipsBySide(relationshipsBySide);

        Map<Integer, Long> relationshipsByGeneration = relationships.stream()
                .collect(Collectors.groupingBy(
                        UserRelationship::getGenerationLevel,
                        Collectors.counting()
                ));
        stats.setRelationshipsByGeneration(relationshipsByGeneration);

        stats.setPendingRequests((long) requestRepository.findByTargetUserIdAndStatus(userId, RequestStatus.PENDING).size());

        long directFamilyCount = relationships.stream()
                .filter(rel -> rel.getRelationshipSide() == RelationshipSide.DIRECT)
                .count();
        stats.setDirectFamilyCount(directFamilyCount);
        stats.setExtendedFamilyCount(stats.getTotalRelationships() - directFamilyCount);

        return stats;
    }

    private UserNodeDto buildUserNodeDto(UserRelationship relationship) {
        return buildUserNodeDto(relationship.getRelatedUser(), relationship, false);
    }

    private UserNodeDto buildUserNodeDto(User user, UserRelationship relationship, boolean isCurrentUser) {
        UserNodeDto dto = new UserNodeDto();
        dto.setUserId(user.getId());
        dto.setName(user.getName());
        dto.setEmail(user.getEmail());
        dto.setSamajName(user.getSamaj() != null ? user.getSamaj().getName() : null);
        dto.setHasProfileImage(user.getProfileImg() != null);

        if (user.getProfileImg() != null) {
            dto.setProfileImageBase64(Base64.getEncoder().encodeToString(user.getProfileImg()));
        }

        if (relationship != null) {
            dto.setRelationshipType(relationship.getRelationshipType());
            dto.setRelationshipDisplayName(relationship.getRelationshipType().getDisplayName());
            dto.setRelationshipSide(relationship.getRelationshipSide());
            dto.setRelationshipSideDisplayName(relationship.getRelationshipSide().getDisplayName());
            dto.setGenerationLevel(relationship.getGenerationLevel());
            dto.setGenerationName(getGenerationName(relationship.getGenerationLevel()));
            dto.setRelationshipId(relationship.getId());
        }

        return dto;
    }

    private RelationshipRequestDto buildRelationshipRequestDto(RelationshipRequest request) {
        RelationshipRequestDto dto = new RelationshipRequestDto();
        dto.setId(request.getId());
        dto.setRequesterUserId(request.getRequesterUserId());
        dto.setTargetUserId(request.getTargetUserId());
        dto.setRelationshipType(request.getRelationshipType());
        dto.setRelationshipDisplayName(request.getRelationshipType().getDisplayName());
        dto.setRelationshipSide(request.getRelationshipSide());
        dto.setRelationshipSideDisplayName(request.getRelationshipSide().getDisplayName());
        dto.setGenerationLevel(request.getGenerationLevel());
        dto.setRequestMessage(request.getRequestMessage());
        dto.setStatus(request.getStatus());
        dto.setStatusDisplayName(request.getStatus().getDisplayName());
        dto.setCreatedAt(request.getCreatedAt());
        dto.setRespondedAt(request.getRespondedAt());

        if (request.getRequesterUser() != null) {
            dto.setRequesterName(request.getRequesterUser().getName());
            dto.setRequesterEmail(request.getRequesterUser().getEmail());
        }

        if (request.getTargetUser() != null) {
            dto.setTargetName(request.getTargetUser().getName());
            dto.setTargetEmail(request.getTargetUser().getEmail());
        }

        return dto;
    }

    private String getGenerationName(Integer level) {
        return switch (level) {
            case -3 -> "Great Grandparents";
            case -2 -> "Grandparents";
            case -1 -> "Parents";
            case 0 -> "Same Generation";
            case 1 -> "Children";
            case 2 -> "Grandchildren";
            case 3 -> "Great Grandchildren";
            default -> level > 0 ? "Younger Generation +" + level : "Elder Generation " + Math.abs(level);
        };
    }

    private String getGenerationDescription(Integer level) {
        return switch (level) {
            case -3 -> "Great grandparents and their siblings";
            case -2 -> "Grandparents, grand uncles, and grand aunts";
            case -1 -> "Parents, uncles, and aunts";
            case 0 -> "You, siblings, cousins, and spouse";
            case 1 -> "Children, nephews, and nieces";
            case 2 -> "Grandchildren";
            case 3 -> "Great grandchildren";
            default -> level > 0 ? "Descendants" : "Ancestors";
        };
    }

    private boolean hasSpouse(List<UserRelationship> relationships) {
        return relationships.stream()
                .anyMatch(rel -> rel.getRelationshipType() == RelationshipType.HUSBAND ||
                        rel.getRelationshipType() == RelationshipType.WIFE);
    }

    private boolean hasChildren(List<UserRelationship> relationships) {
        return relationships.stream()
                .anyMatch(rel -> rel.getRelationshipType() == RelationshipType.SON ||
                        rel.getRelationshipType() == RelationshipType.DAUGHTER);
    }

    private boolean hasParents(List<UserRelationship> relationships) {
        return relationships.stream()
                .anyMatch(rel -> rel.getRelationshipType() == RelationshipType.FATHER ||
                        rel.getRelationshipType() == RelationshipType.MOTHER);
    }
}
