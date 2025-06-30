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
     * 2. Add a new relationship - CORRECTED: ALWAYS SENDS REQUEST FIRST
     * Corresponds to: POST /relationship
     */
    public ApiResponse<String> addRelationship(AddRelationshipRequest request) {
        try {
            log.info("Adding relationship: {} -> {} as {} with lineage context: {}",
                    request.getRequestingUserId(), request.getRelatedUserId(),
                    request.getRelationshipType(), request.getLineageContext());

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

            // CORRECTED: Always create a relationship request - no exceptions
            log.info("CREATING_RELATIONSHIP_REQUEST: All relationships require mutual approval");
            return createRelationshipRequest(request);

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
     * 7. Respond to a relationship request - CORRECTED: This is where relationships are actually created
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

            // CORRECTED: Only create relationships when request is APPROVED
            if (response.getStatus() == RequestStatus.APPROVED) {
                log.info("REQUEST_APPROVED: Creating bidirectional relationships");
                createRelationshipsAfterApproval(request, response.getRespondingUserId());
            } else {
                log.info("REQUEST_REJECTED: No relationships will be created");
            }

            String message = response.getStatus() == RequestStatus.APPROVED ?
                    "Request approved and relationship created successfully" : "Request rejected";

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
     * CORRECTED: Create a relationship request (this is the main flow now)
     */
    private ApiResponse<String> createRelationshipRequest(AddRelationshipRequest request) {
        try {
            log.info("CREATING_RELATIONSHIP_REQUEST: {} -> {} as {}",
                    request.getRequestingUserId(), request.getRelatedUserId(), request.getRelationshipType());

            // Check if a similar request already exists
            Optional<RelationshipRequest> existingRequest = requestRepository
                    .findByRequesterUserIdAndTargetUserIdAndRelationshipTypeAndStatus(
                            request.getRequestingUserId(), request.getRelatedUserId(),
                            request.getRelationshipType(), RequestStatus.PENDING);

            if (existingRequest.isPresent()) {
                log.warn("DUPLICATE_REQUEST: Request already exists with ID: {}", existingRequest.get().getId());
                return ApiResponse.error("A similar relationship request is already pending");
            }

            // Check if relationship already exists
            boolean relationshipExists = relationshipRepository.existsByUserIdAndRelatedUserIdAndIsActive(
                    request.getRequestingUserId(), request.getRelatedUserId(), true) ||
                    relationshipRepository.existsByUserIdAndRelatedUserIdAndIsActive(
                            request.getRelatedUserId(), request.getRequestingUserId(), true);

            if (relationshipExists) {
                log.warn("RELATIONSHIP_EXISTS: Relationship already exists between users {} and {}",
                        request.getRequestingUserId(), request.getRelatedUserId());
                return ApiResponse.error("Relationship already exists between these users");
            }

            // Create the relationship request
            RelationshipRequest relationshipRequest = new RelationshipRequest();
            relationshipRequest.setRequesterUserId(request.getRequestingUserId());
            relationshipRequest.setTargetUserId(request.getRelatedUserId());
            relationshipRequest.setRelationshipType(request.getRelationshipType());

            // Determine relationship side with context
            RelationshipSide determinedSide = determineRelationshipSide(request);
            relationshipRequest.setRelationshipSide(determinedSide);

            relationshipRequest.setGenerationLevel(request.getGenerationLevel() != null ?
                    request.getGenerationLevel() : request.getRelationshipType().getDefaultGenerationLevel());

            relationshipRequest.setRequestMessage(request.getRequestMessage());
            relationshipRequest.setStatus(RequestStatus.PENDING);
            relationshipRequest.setCreatedAt(LocalDateTime.now());
            relationshipRequest.setUpdatedAt(LocalDateTime.now());

            RelationshipRequest savedRequest = requestRepository.save(relationshipRequest);

            log.info("RELATIONSHIP_REQUEST_CREATED: ID={}, RequesterUserId={}, TargetUserId={}, RelationshipType={}, RelationshipSide={}, GenerationLevel={}",
                    savedRequest.getId(), savedRequest.getRequesterUserId(), savedRequest.getTargetUserId(),
                    savedRequest.getRelationshipType(), savedRequest.getRelationshipSide(),
                    savedRequest.getGenerationLevel());

            return ApiResponse.success("Relationship request sent successfully. Waiting for approval from the other user.");

        } catch (Exception e) {
            log.error("Error creating relationship request", e);
            return ApiResponse.error("Failed to send relationship request: " + e.getMessage());
        }
    }

    /**
     * CORRECTED: Only for admin override - direct relationship addition
     */


    /**
     * CORRECTED: Create relationships after request approval
     */
    private void createRelationshipsAfterApproval(RelationshipRequest request, Long respondingUserId) {
        log.info("CREATING_RELATIONSHIPS_AFTER_APPROVAL: RequestId={}, RequesterUserId={}, TargetUserId={}, RelationshipType={}",
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

            // Build relationship context from request
            RelationshipContext context = buildRelationshipContextFromRequest(request, requesterUser, targetUser);

            // Create the requester's relationship (original request)
            UserRelationship requesterRelationship = createSingleRelationship(
                    request.getRequesterUserId(),
                    request.getTargetUserId(),
                    request.getRelationshipType(),
                    request.getRelationshipSide(),
                    request.getGenerationLevel(),
                    respondingUserId
            );

            log.info("REQUESTER_RELATIONSHIP_CREATED: ID={}, Type={}",
                    requesterRelationship.getId(), requesterRelationship.getRelationshipType());

            // Calculate and create the target user's relationship (reverse with enhanced context)
            log.info("CALCULATING_REVERSE_TYPE: OriginalType={}, RequesterGender={}, TargetGender={}, Context={}",
                    request.getRelationshipType(), requesterUser.getGender(), targetUser.getGender(), context);

            RelationshipType reverseType = getReverseRelationshipTypeEnhanced(
                    request.getRelationshipType(), requesterUser, targetUser, context);

            log.info("CALCULATED_REVERSE_TYPE: {}", reverseType);

            UserRelationship targetRelationship = createSingleRelationship(
                    request.getTargetUserId(),
                    request.getRequesterUserId(),
                    reverseType,
                    reverseType.getDefaultRelationshipSide(),
                    -request.getGenerationLevel(),
                    respondingUserId
            );

            log.info("TARGET_RELATIONSHIP_CREATED: ID={}, Type={}",
                    targetRelationship.getId(), targetRelationship.getRelationshipType());

            log.info("BIDIRECTIONAL_RELATIONSHIPS_CREATED_SUCCESSFULLY: Forward ID={}, Reverse ID={}",
                    requesterRelationship.getId(), targetRelationship.getId());

        } catch (DataIntegrityViolationException e) {
            log.warn("Relationship creation failed due to constraint violation - relationship may have been created by another process", e);
            throw new RuntimeException("Relationship already exists between these users");
        } catch (Exception e) {
            log.error("Error creating relationships after approval", e);
            throw new RuntimeException("Failed to create relationships: " + e.getMessage());
        }
    }

    /**
     * NEW: Create a single relationship record
     */
    private UserRelationship createSingleRelationship(Long userId, Long relatedUserId, RelationshipType relationshipType,
                                                      RelationshipSide relationshipSide, Integer generationLevel,
                                                      Long createdBy) {

        log.info("CREATING_SINGLE_RELATIONSHIP: UserId={}, RelatedUserId={}, RelationshipType={}, RelationshipSide={}, GenerationLevel={}",
                userId, relatedUserId, relationshipType, relationshipSide, generationLevel);

        // Check if relationship already exists
        Optional<UserRelationship> existingRelationship = relationshipRepository
                .findByUserIdAndRelatedUserIdAndIsActiveTrue(userId, relatedUserId);

        if (existingRelationship.isPresent()) {
            log.warn("RELATIONSHIP_ALREADY_EXISTS: Updating existing relationship ID={}", existingRelationship.get().getId());

            // Update existing relationship
            UserRelationship relationship = existingRelationship.get();
            relationship.setRelationshipType(relationshipType);
            relationship.setRelationshipSide(relationshipSide);
            relationship.setGenerationLevel(generationLevel);
            relationship.setUpdatedAt(LocalDateTime.now());

            UserRelationship savedRelationship = relationshipRepository.save(relationship);
            log.info("EXISTING_RELATIONSHIP_UPDATED: ID={}, Type={}", savedRelationship.getId(), savedRelationship.getRelationshipType());
            return savedRelationship;
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

            UserRelationship savedRelationship = relationshipRepository.save(relationship);
            log.info("NEW_RELATIONSHIP_CREATED: ID={}, Type={}", savedRelationship.getId(), savedRelationship.getRelationshipType());
            return savedRelationship;
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
     * ENHANCED: Get reverse relationship type with context - SOLVES THE MAIN ISSUE
     */
    private RelationshipType getReverseRelationshipTypeEnhanced(RelationshipType originalType,
                                                                User requesterUser, User targetUser,
                                                                RelationshipContext context) {
        log.info("REVERSE_TYPE_CALCULATION_START: OriginalType={}, RequesterGender={}, TargetGender={}, Context={}",
                originalType, requesterUser.getGender(), targetUser.getGender(), context);

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

            // ENHANCED: GRANDSON/GRANDDAUGHTER with context-based lineage determination
            case GRANDSON -> {
                RelationshipType result = determineGrandparentLineage(context, isRequesterMale, true);
                log.info("REVERSE_TYPE_CALCULATION: GRANDSON -> {} (based on context)", result);
                yield result;
            }
            case GRANDDAUGHTER -> {
                RelationshipType result = determineGrandparentLineage(context, isRequesterMale, false);
                log.info("REVERSE_TYPE_CALCULATION: GRANDDAUGHTER -> {} (based on context)", result);
                yield result;
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

            // ENHANCED: NEPHEW/NIECE with context-based lineage determination
            case NEPHEW -> {
                RelationshipType result = determineUncleAuntLineage(context, true);
                log.info("REVERSE_TYPE_CALCULATION: NEPHEW -> {} (based on context)", result);
                yield result;
            }
            case NIECE -> {
                RelationshipType result = determineUncleAuntLineage(context, false);
                log.info("REVERSE_TYPE_CALCULATION: NIECE -> {} (based on context)", result);
                yield result;
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

        log.info("REVERSE_TYPE_CALCULATION_FINAL: OriginalType={}, RequesterGender={}, ReverseType={}, Context={}",
                originalType, requesterUser.getGender(), reverseType, context);

        return reverseType;
    }

    /**
     * NEW: Determine grandparent lineage based on context
     */
    private RelationshipType determineGrandparentLineage(RelationshipContext context, boolean isRequesterMale, boolean isGrandson) {
        log.info("GRANDPARENT_LINEAGE_DETERMINATION: Context={}, isRequesterMale={}, isGrandson={}", context, isRequesterMale, isGrandson);

        if (context != null && context.getLineage() != null) {
            // Direct lineage specification
            if ("PATERNAL".equalsIgnoreCase(context.getLineage())) {
                RelationshipType result = isRequesterMale ? RelationshipType.PATERNAL_GRANDFATHER : RelationshipType.PATERNAL_GRANDMOTHER;
                log.info("GRANDPARENT_LINEAGE_DETERMINATION: PATERNAL context -> {}", result);
                return result;
            } else if ("MATERNAL".equalsIgnoreCase(context.getLineage())) {
                RelationshipType result = isRequesterMale ? RelationshipType.MATERNAL_GRANDFATHER : RelationshipType.MATERNAL_GRANDMOTHER;
                log.info("GRANDPARENT_LINEAGE_DETERMINATION: MATERNAL context -> {}", result);
                return result;
            }
        }

        if (context != null && context.getIntermediateRelative() != null) {
            // Determine lineage based on intermediate relative (the parent)
            User parentUser = context.getIntermediateRelative();
            boolean isParentMale = "MALE".equalsIgnoreCase(parentUser.getGender()) || "M".equalsIgnoreCase(parentUser.getGender());

            log.info("GRANDPARENT_LINEAGE_DETERMINATION: IntermediateRelative={}, isParentMale={}", parentUser.getName(), isParentMale);

            if (isParentMale) {
                // Father's side = Paternal
                RelationshipType result = isRequesterMale ? RelationshipType.PATERNAL_GRANDFATHER : RelationshipType.PATERNAL_GRANDMOTHER;
                log.info("GRANDPARENT_LINEAGE_DETERMINATION: Parent is male -> {}", result);
                return result;
            } else {
                // Mother's side = Maternal
                RelationshipType result = isRequesterMale ? RelationshipType.MATERNAL_GRANDFATHER : RelationshipType.MATERNAL_GRANDMOTHER;
                log.info("GRANDPARENT_LINEAGE_DETERMINATION: Parent is female -> {}", result);
                return result;
            }
        }

        // Default fallback
        RelationshipType result = isRequesterMale ? RelationshipType.PATERNAL_GRANDFATHER : RelationshipType.PATERNAL_GRANDMOTHER;
        log.info("GRANDPARENT_LINEAGE_DETERMINATION: Default fallback -> {}", result);
        return result;
    }

    /**
     * NEW: Determine uncle/aunt lineage based on context
     */
    private RelationshipType determineUncleAuntLineage(RelationshipContext context, boolean isNephew) {
        log.info("UNCLE_AUNT_LINEAGE_DETERMINATION: Context={}, isNephew={}", context, isNephew);

        if (context != null && context.getLineage() != null) {
            if ("PATERNAL".equalsIgnoreCase(context.getLineage())) {
                RelationshipType result = isNephew ? RelationshipType.PATERNAL_UNCLE : RelationshipType.PATERNAL_AUNT;
                log.info("UNCLE_AUNT_LINEAGE_DETERMINATION: PATERNAL context -> {}", result);
                return result;
            } else if ("MATERNAL".equalsIgnoreCase(context.getLineage())) {
                RelationshipType result = isNephew ? RelationshipType.MATERNAL_UNCLE : RelationshipType.MATERNAL_AUNT;
                log.info("UNCLE_AUNT_LINEAGE_DETERMINATION: MATERNAL context -> {}", result);
                return result;
            }
        }

        if (context != null && context.getIntermediateRelative() != null) {
            User parentUser = context.getIntermediateRelative();
            boolean isParentMale = "MALE".equalsIgnoreCase(parentUser.getGender()) || "M".equalsIgnoreCase(parentUser.getGender());

            log.info("UNCLE_AUNT_LINEAGE_DETERMINATION: IntermediateRelative={}, isParentMale={}", parentUser.getName(), isParentMale);

            if (isParentMale) {
                RelationshipType result = isNephew ? RelationshipType.PATERNAL_UNCLE : RelationshipType.PATERNAL_AUNT;
                log.info("UNCLE_AUNT_LINEAGE_DETERMINATION: Parent is male -> {}", result);
                return result;
            } else {
                RelationshipType result = isNephew ? RelationshipType.MATERNAL_UNCLE : RelationshipType.MATERNAL_AUNT;
                log.info("UNCLE_AUNT_LINEAGE_DETERMINATION: Parent is female -> {}", result);
                return result;
            }
        }

        // Default fallback
        RelationshipType result = isNephew ? RelationshipType.PATERNAL_UNCLE : RelationshipType.PATERNAL_AUNT;
        log.info("UNCLE_AUNT_LINEAGE_DETERMINATION: Default fallback -> {}", result);
        return result;
    }

    /**
     * NEW: Build relationship context from request
     */
    private RelationshipContext buildRelationshipContext(AddRelationshipRequest request, User requestingUser, User relatedUser) {
        RelationshipContext context = new RelationshipContext();

        // Set lineage from request if provided
        if (request.getLineageContext() != null) {
            context.setLineage(request.getLineageContext());
            log.info("CONTEXT_BUILDING: Set lineage from request: {}", request.getLineageContext());
        }

        // Set intermediate relative if provided
        if (request.getIntermediateRelativeId() != null) {
            Optional<User> intermediateUser = userRepository.findById(request.getIntermediateRelativeId());
            if (intermediateUser.isPresent()) {
                context.setIntermediateRelative(intermediateUser.get());
                log.info("CONTEXT_BUILDING: Set intermediate relative: {}", intermediateUser.get().getName());
            }
        }

        // Set relationship path if provided
        if (request.getRelationshipPath() != null) {
            context.setOriginalRelationshipPath(request.getRelationshipPath());
            log.info("CONTEXT_BUILDING: Set relationship path: {}", request.getRelationshipPath());
        }

        return context;
    }

    /**
     * NEW: Build relationship context from relationship request
     */
    private RelationshipContext buildRelationshipContextFromRequest(RelationshipRequest request, User requesterUser, User targetUser) {
        RelationshipContext context = new RelationshipContext();

        // Try to determine lineage from relationship side
        if (request.getRelationshipSide() != null) {
            switch (request.getRelationshipSide()) {
                case PATERNAL -> {
                    context.setLineage("PATERNAL");
                    log.info("CONTEXT_FROM_REQUEST: Set PATERNAL lineage from relationship side");
                }
                case MATERNAL -> {
                    context.setLineage("MATERNAL");
                    log.info("CONTEXT_FROM_REQUEST: Set MATERNAL lineage from relationship side");
                }
                default -> {
                    log.info("CONTEXT_FROM_REQUEST: No specific lineage from relationship side: {}", request.getRelationshipSide());
                }
            }
        }

        return context;
    }

    /**
     * NEW: Determine relationship side with context
     */
    private RelationshipSide determineRelationshipSide(AddRelationshipRequest request) {
        // If explicitly provided, use it
        if (request.getRelationshipSide() != null) {
            log.info("RELATIONSHIP_SIDE_DETERMINATION: Using provided side: {}", request.getRelationshipSide());
            return request.getRelationshipSide();
        }

        // If lineage context is provided, use it for applicable relationships
        if (request.getLineageContext() != null) {
            switch (request.getRelationshipType()) {
                case PATERNAL_GRANDFATHER, PATERNAL_GRANDMOTHER, PATERNAL_UNCLE, PATERNAL_AUNT,
                     PATERNAL_COUSIN_BROTHER, PATERNAL_COUSIN_SISTER -> {
                    if ("PATERNAL".equalsIgnoreCase(request.getLineageContext())) {
                        log.info("RELATIONSHIP_SIDE_DETERMINATION: Using PATERNAL from lineage context");
                        return RelationshipSide.PATERNAL;
                    }
                }
                case MATERNAL_GRANDFATHER, MATERNAL_GRANDMOTHER, MATERNAL_UNCLE, MATERNAL_AUNT,
                     MATERNAL_COUSIN_BROTHER, MATERNAL_COUSIN_SISTER -> {
                    if ("MATERNAL".equalsIgnoreCase(request.getLineageContext())) {
                        log.info("RELATIONSHIP_SIDE_DETERMINATION: Using MATERNAL from lineage context");
                        return RelationshipSide.MATERNAL;
                    }
                }
                case GRANDSON, GRANDDAUGHTER -> {
                    if ("PATERNAL".equalsIgnoreCase(request.getLineageContext())) {
                        log.info("RELATIONSHIP_SIDE_DETERMINATION: Using PATERNAL for GRANDSON/GRANDDAUGHTER from lineage context");
                        return RelationshipSide.PATERNAL;
                    } else if ("MATERNAL".equalsIgnoreCase(request.getLineageContext())) {
                        log.info("RELATIONSHIP_SIDE_DETERMINATION: Using MATERNAL for GRANDSON/GRANDDAUGHTER from lineage context");
                        return RelationshipSide.MATERNAL;
                    }
                }
                case NEPHEW, NIECE -> {
                    if ("PATERNAL".equalsIgnoreCase(request.getLineageContext())) {
                        log.info("RELATIONSHIP_SIDE_DETERMINATION: Using PATERNAL for NEPHEW/NIECE from lineage context");
                        return RelationshipSide.PATERNAL;
                    } else if ("MATERNAL".equalsIgnoreCase(request.getLineageContext())) {
                        log.info("RELATIONSHIP_SIDE_DETERMINATION: Using MATERNAL for NEPHEW/NIECE from lineage context");
                        return RelationshipSide.MATERNAL;
                    }
                }
            }
        }

        // Default to enum's default
        RelationshipSide defaultSide = request.getRelationshipType().getDefaultRelationshipSide();
        log.info("RELATIONSHIP_SIDE_DETERMINATION: Using default side: {}", defaultSide);
        return defaultSide;
    }

    /**
     * Validate that the target user's gender matches the relationship role they're being assigned
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
                    return ApiResponse.error("Cannot assign " + relationshipType.getDisplayName() + " role to a female user");
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
