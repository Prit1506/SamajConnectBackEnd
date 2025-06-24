package com.example.samajconnectbackend.service;

import com.example.samajconnectbackend.dto.*;
import com.example.samajconnectbackend.entity.*;
import com.example.samajconnectbackend.repository.RelationshipRequestRepository;
import com.example.samajconnectbackend.repository.UserRelationshipRepository;
import com.example.samajconnectbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<String> respondToRequest(RespondToRequestDto response) {
        try {
            log.info("Attempting to respond to request with ID: {}", response.getRequestId());

            // Find the request
            RelationshipRequest request = requestRepository.findById(response.getRequestId())
                    .orElseThrow(() -> new IllegalArgumentException("Request not found with ID: " + response.getRequestId()));

            log.info("Found request: ID={}, Status={}, TargetUserId={}, RequesterUserId={}",
                    request.getId(), request.getStatus(), request.getTargetUserId(), request.getRequesterUserId());

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

    // Method to check if relationship already exists
    private boolean checkRelationshipExists(RelationshipRequest request) {
        // Check if requester -> target relationship exists
        boolean requesterToTargetExists = relationshipRepository.existsByUserIdAndRelatedUserIdAndRelationshipTypeAndIsActive(
                request.getRequesterUserId(),
                request.getTargetUserId(),
                request.getRelationshipType(),
                true);

        // Check if target -> requester relationship exists (reverse)
        RelationshipType reverseType = getReverse(request.getRelationshipType());
        boolean targetToRequesterExists = relationshipRepository.existsByUserIdAndRelatedUserIdAndRelationshipTypeAndIsActive(
                request.getTargetUserId(),
                request.getRequesterUserId(),
                reverseType,
                true);

        return requesterToTargetExists || targetToRequesterExists;
    }

    // Method to create relationships with additional safety checks
    private void createRelationshipsIfNotExists(RelationshipRequest request, Long respondingUserId) {
        log.info("Creating relationships for approved request: {}", request.getId());

        try {
            // Create the target user's relationship (only if it doesn't exist)
            RelationshipType reverseType = getReverse(request.getRelationshipType());

            boolean targetRelationshipExists = relationshipRepository.existsByUserIdAndRelatedUserIdAndRelationshipTypeAndIsActive(
                    request.getTargetUserId(),
                    request.getRequesterUserId(),
                    reverseType,
                    true);

            if (!targetRelationshipExists) {
                UserRelationship relationship = new UserRelationship();
                relationship.setUserId(request.getTargetUserId());
                relationship.setRelatedUserId(request.getRequesterUserId());
                relationship.setRelationshipType(reverseType);
                relationship.setRelationshipSide(request.getRelationshipSide());
                relationship.setGenerationLevel(-request.getGenerationLevel());
                relationship.setCreatedBy(respondingUserId);
                relationship.setIsActive(true);
                relationship.setCreatedAt(LocalDateTime.now());
                relationship.setUpdatedAt(LocalDateTime.now());

                UserRelationship savedRelationship = relationshipRepository.save(relationship);
                log.info("Created relationship for target user: {}", savedRelationship.getId());
            } else {
                log.info("Target user relationship already exists, skipping creation");
            }

            // Create the requester's relationship (only if it doesn't exist)
            boolean requesterRelationshipExists = relationshipRepository.existsByUserIdAndRelatedUserIdAndRelationshipTypeAndIsActive(
                    request.getRequesterUserId(),
                    request.getTargetUserId(),
                    request.getRelationshipType(),
                    true);

            if (!requesterRelationshipExists) {
                UserRelationship requesterRelationship = new UserRelationship();
                requesterRelationship.setUserId(request.getRequesterUserId());
                requesterRelationship.setRelatedUserId(request.getTargetUserId());
                requesterRelationship.setRelationshipType(request.getRelationshipType());
                requesterRelationship.setRelationshipSide(request.getRelationshipSide());
                requesterRelationship.setGenerationLevel(request.getGenerationLevel());
                requesterRelationship.setCreatedBy(respondingUserId);
                requesterRelationship.setIsActive(true);
                requesterRelationship.setCreatedAt(LocalDateTime.now());
                requesterRelationship.setUpdatedAt(LocalDateTime.now());

                UserRelationship savedRequesterRelationship = relationshipRepository.save(requesterRelationship);
                log.info("Created relationship for requester user: {}", savedRequesterRelationship.getId());
            } else {
                log.info("Requester relationship already exists, skipping creation");
            }

        } catch (DataIntegrityViolationException e) {
            log.warn("Relationship creation failed due to constraint violation - relationship may have been created by another process", e);
            // Don't throw the exception as the request was already approved
        }
    }

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

    public List<UserNodeDto> getMutualRelatives(Long userId1, Long userId2) {
        List<Long> mutualRelativeIds = relationshipRepository.findMutualRelatives(userId1, userId2);
        return mutualRelativeIds.stream()
                .map(id -> userRepository.findById(id))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(user -> buildUserNodeDto(user, null, false))
                .collect(Collectors.toList());
    }

    public List<UserNodeDto> getFamilyMembersByGeneration(Long userId, Integer generationLevel) {
        List<UserRelationship> relationships = relationshipRepository
                .findByUserIdAndGenerationLevelAndIsActiveTrue(userId, generationLevel);

        return relationships.stream()
                .map(this::buildUserNodeDto)
                .collect(Collectors.toList());
    }

    public List<UserNodeDto> getFamilyMembersBySide(Long userId, RelationshipSide side) {
        List<UserRelationship> relationships = relationshipRepository
                .findByUserIdAndRelationshipSideAndIsActiveTrue(userId, side);

        return relationships.stream()
                .map(this::buildUserNodeDto)
                .collect(Collectors.toList());
    }

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

    private RelationshipType getReverse(RelationshipType type) {
        return switch (type) {
            case FATHER -> RelationshipType.SON; // or DAUGHTER based on gender
            case MOTHER -> RelationshipType.DAUGHTER; // or SON based on gender
            case SON -> RelationshipType.FATHER;
            case DAUGHTER -> RelationshipType.MOTHER;
            case HUSBAND -> RelationshipType.WIFE;
            case WIFE -> RelationshipType.HUSBAND;
            case BROTHER -> RelationshipType.SISTER; // or BROTHER based on gender
            case SISTER -> RelationshipType.BROTHER; // or SISTER based on gender
            case PATERNAL_GRANDFATHER -> RelationshipType.GRANDSON;
            case PATERNAL_GRANDMOTHER -> RelationshipType.GRANDDAUGHTER;
            case MATERNAL_GRANDFATHER -> RelationshipType.GRANDSON;
            case MATERNAL_GRANDMOTHER -> RelationshipType.GRANDDAUGHTER;
            case GRANDSON -> RelationshipType.PATERNAL_GRANDFATHER;
            case GRANDDAUGHTER -> RelationshipType.PATERNAL_GRANDMOTHER;
            default -> type; // For complex relationships, return same
        };
    }

    // All the private helper methods from the previous incomplete code remain the same
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

    public ApiResponse<String> addRelationship(AddRelationshipRequest request) {
        try {
            log.info("Adding relationship: {} -> {} as {}",
                    request.getRequestingUserId(), request.getRelatedUserId(), request.getRelationshipType());

            RelationshipValidationResponse validation = validationService.validateRelationship(
                    request.getRequestingUserId(), request.getRelatedUserId(), request.getRelationshipType());

            if (!validation.isValid()) {
                return ApiResponse.error("Validation failed: " + String.join(", ", validation.getValidationErrors()));
            }

            if (request.isSendRequest()) {
                return createRelationshipRequest(request);
            } else {
                return addDirectRelationship(request);
            }

        } catch (Exception e) {
            log.error("Error adding relationship", e);
            return ApiResponse.error("Failed to add relationship: " + e.getMessage());
        }
    }

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
        relationshipRequest.setRelationshipSide(request.getRelationshipSide() != null ?
                request.getRelationshipSide() : request.getRelationshipType().getDefaultRelationshipSide());
        relationshipRequest.setGenerationLevel(request.getGenerationLevel() != null ?
                request.getGenerationLevel() : request.getRelationshipType().getDefaultGenerationLevel());
        relationshipRequest.setRequestMessage(request.getRequestMessage());
        relationshipRequest.setStatus(RequestStatus.PENDING);

        requestRepository.save(relationshipRequest);

        return ApiResponse.success("Relationship request sent successfully");
    }

    private ApiResponse<String> addDirectRelationship(AddRelationshipRequest request) {
        UserRelationship relationship = new UserRelationship();
        relationship.setUserId(request.getRequestingUserId());
        relationship.setRelatedUserId(request.getRelatedUserId());
        relationship.setRelationshipType(request.getRelationshipType());
        relationship.setRelationshipSide(request.getRelationshipSide() != null ?
                request.getRelationshipSide() : request.getRelationshipType().getDefaultRelationshipSide());
        relationship.setGenerationLevel(request.getGenerationLevel() != null ?
                request.getGenerationLevel() : request.getRelationshipType().getDefaultGenerationLevel());
        relationship.setCreatedBy(request.getRequestingUserId());
        relationship.setIsActive(true);

        relationshipRepository.save(relationship);

        return ApiResponse.success("Relationship added successfully");
    }

    public ApiResponse<String> removeRelationship(Long userId, Long relatedUserId) {
        try {
            relationshipRepository.softDeleteRelationship(userId, relatedUserId);
            return ApiResponse.success("Relationship removed successfully");
        } catch (Exception e) {
            log.error("Error removing relationship", e);
            return ApiResponse.error("Failed to remove relationship: " + e.getMessage());
        }
    }

    public List<RelationshipRequestDto> getPendingRequests(Long userId) {
        List<RelationshipRequest> requests = requestRepository.findByTargetUserIdAndStatus(userId, RequestStatus.PENDING);
        return requests.stream().map(this::buildRelationshipRequestDto).collect(Collectors.toList());
    }

    public List<RelationshipRequestDto> getSentRequests(Long userId) {
        List<RelationshipRequest> requests = requestRepository.findByRequesterUserIdAndStatus(userId, RequestStatus.PENDING);
        return requests.stream().map(this::buildRelationshipRequestDto).collect(Collectors.toList());
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
}