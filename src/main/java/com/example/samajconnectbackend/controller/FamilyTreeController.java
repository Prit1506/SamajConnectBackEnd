package com.example.samajconnectbackend.controller;

import com.example.samajconnectbackend.dto.*;
import com.example.samajconnectbackend.entity.RelationshipSide;
import com.example.samajconnectbackend.entity.RelationshipType;
import com.example.samajconnectbackend.service.FamilyTreeService;
import com.example.samajconnectbackend.service.RelationshipValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/family-tree")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class FamilyTreeController {

    private final FamilyTreeService familyTreeService;
    private final RelationshipValidationService validationService;

    /**
     * Get complete family tree for a user
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<FamilyTreeResponse>> getFamilyTree(@PathVariable Long userId) {
        try {
            log.info("Fetching family tree for user: {}", userId);
            FamilyTreeResponse familyTree = familyTreeService.getFamilyTree(userId);
            return ResponseEntity.ok(ApiResponse.success("Family tree retrieved successfully", familyTree));
        } catch (Exception e) {
            log.error("Error fetching family tree for user: {}", userId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to retrieve family tree: " + e.getMessage()));
        }
    }

    /**
     * Add a new relationship
     */
    @PostMapping("/relationship")
    public ResponseEntity<ApiResponse<String>> addRelationship(@Valid @RequestBody AddRelationshipRequest request) {
        try {
            log.info("Adding relationship: {} -> {} as {}",
                    request.getRequestingUserId(), request.getRelatedUserId(), request.getRelationshipType());

            ApiResponse<String> response = familyTreeService.addRelationship(request);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            log.error("Error adding relationship", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to add relationship: " + e.getMessage()));
        }
    }

    /**
     * Update an existing relationship
     */
    @PutMapping("/relationship")
    public ResponseEntity<ApiResponse<String>> updateRelationship(@Valid @RequestBody UpdateRelationshipRequest request) {
        try {
            log.info("Updating relationship: {}", request.getRelationshipId());

            ApiResponse<String> response = familyTreeService.updateRelationship(request);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            log.error("Error updating relationship", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to update relationship: " + e.getMessage()));
        }
    }

    /**
     * Remove a relationship
     */
    @DeleteMapping("/relationship/{userId}/{relatedUserId}")
    public ResponseEntity<ApiResponse<String>> removeRelationship(
            @PathVariable Long userId,
            @PathVariable Long relatedUserId) {
        try {
            log.info("Removing relationship between users: {} and {}", userId, relatedUserId);

            ApiResponse<String> response = familyTreeService.removeRelationship(userId, relatedUserId);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            log.error("Error removing relationship between users: {} and {}", userId, relatedUserId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to remove relationship: " + e.getMessage()));
        }
    }

    /**
     * Get pending relationship requests for a user
     */
    @GetMapping("/requests/pending/{userId}")
    public ResponseEntity<ApiResponse<List<RelationshipRequestDto>>> getPendingRequests(@PathVariable Long userId) {
        try {
            log.info("Fetching pending requests for user: {}", userId);
            List<RelationshipRequestDto> requests = familyTreeService.getPendingRequests(userId);
            return ResponseEntity.ok(ApiResponse.success("Pending requests retrieved successfully", requests));
        } catch (Exception e) {
            log.error("Error fetching pending requests for user: {}", userId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to retrieve pending requests: " + e.getMessage()));
        }
    }

    /**
     * Get sent relationship requests by a user
     */
    @GetMapping("/requests/sent/{userId}")
    public ResponseEntity<ApiResponse<List<RelationshipRequestDto>>> getSentRequests(@PathVariable Long userId) {
        try {
            log.info("Fetching sent requests for user: {}", userId);
            List<RelationshipRequestDto> requests = familyTreeService.getSentRequests(userId);
            return ResponseEntity.ok(ApiResponse.success("Sent requests retrieved successfully", requests));
        } catch (Exception e) {
            log.error("Error fetching sent requests for user: {}", userId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to retrieve sent requests: " + e.getMessage()));
        }
    }

    /**
     * Respond to a relationship request
     */
    @PostMapping("/requests/respond")
    public ResponseEntity<ApiResponse<String>> respondToRequest(@Valid @RequestBody RespondToRequestDto request) {
        try {
            log.info("Responding to request: {} with status: {}", request.getRequestId(), request.getStatus());

            ApiResponse<String> response = familyTreeService.respondToRequest(request);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            log.error("Error responding to request", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to respond to request: " + e.getMessage()));
        }
    }

    /**
     * Search family members
     */
    @PostMapping("/search/{userId}")
    public ResponseEntity<ApiResponse<FamilyMemberSearchResponse>> searchFamilyMembers(
            @PathVariable Long userId,
            @RequestBody FamilyMemberSearchDto searchDto) {
        try {
            log.info("Searching family members for user: {} with query: {}", userId, searchDto.getQuery());

            FamilyMemberSearchResponse response = familyTreeService.searchFamilyMembers(userId, searchDto);
            return ResponseEntity.ok(ApiResponse.success("Search completed successfully", response));
        } catch (Exception e) {
            log.error("Error searching family members for user: {}", userId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to search family members: " + e.getMessage()));
        }
    }

    /**
     * Get family members by generation level
     */
    @GetMapping("/generation/{userId}/{generationLevel}")
    public ResponseEntity<ApiResponse<List<UserNodeDto>>> getFamilyMembersByGeneration(
            @PathVariable Long userId,
            @PathVariable Integer generationLevel) {
        try {
            log.info("Fetching family members for user: {} at generation level: {}", userId, generationLevel);

            List<UserNodeDto> members = familyTreeService.getFamilyMembersByGeneration(userId, generationLevel);
            return ResponseEntity.ok(ApiResponse.success("Generation members retrieved successfully", members));
        } catch (Exception e) {
            log.error("Error fetching generation members for user: {}", userId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to retrieve generation members: " + e.getMessage()));
        }
    }

    /**
     * Get family members by relationship side
     */
    @GetMapping("/side/{userId}/{relationshipSide}")
    public ResponseEntity<ApiResponse<List<UserNodeDto>>> getFamilyMembersBySide(
            @PathVariable Long userId,
            @PathVariable RelationshipSide relationshipSide) {
        try {
            log.info("Fetching family members for user: {} on {} side", userId, relationshipSide);

            List<UserNodeDto> members = familyTreeService.getFamilyMembersBySide(userId, relationshipSide);
            return ResponseEntity.ok(ApiResponse.success("Side members retrieved successfully", members));
        } catch (Exception e) {
            log.error("Error fetching side members for user: {}", userId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to retrieve side members: " + e.getMessage()));
        }
    }

    /**
     * Get mutual relatives between two users
     */
    @GetMapping("/mutual/{userId1}/{userId2}")
    public ResponseEntity<ApiResponse<List<UserNodeDto>>> getMutualRelatives(
            @PathVariable Long userId1,
            @PathVariable Long userId2) {
        try {
            log.info("Fetching mutual relatives between users: {} and {}", userId1, userId2);

            List<UserNodeDto> mutualRelatives = familyTreeService.getMutualRelatives(userId1, userId2);
            return ResponseEntity.ok(ApiResponse.success("Mutual relatives retrieved successfully", mutualRelatives));
        } catch (Exception e) {
            log.error("Error fetching mutual relatives between users: {} and {}", userId1, userId2, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to retrieve mutual relatives: " + e.getMessage()));
        }
    }

    /**
     * Validate a relationship before creating
     */
//    @PostMapping("/validate")
//    public ResponseEntity<ApiResponse<RelationshipValidationResponse>> validateRelationship(
//            @RequestParam Long userId,
//            @RequestParam Long relatedUserId,
//            @RequestParam RelationshipType relationshipType) {
//        try {
//            log.info("Validating relationship: {} -> {} as {}", userId, relatedUserId, relationshipType);
//
//            RelationshipValidationResponse validation = validationService.validateRelationship(userId, relatedUserId, relationshipType);
//            return ResponseEntity.ok(ApiResponse.success("Validation completed", validation));
//        } catch (Exception e) {
//            log.error("Error validating relationship", e);
//            return ResponseEntity.internalServerError()
//                    .body(ApiResponse.error("Failed to validate relationship: " + e.getMessage()));
//        }
//    }

    /**
     * Get all available relationship types
     */
    @GetMapping("/relationship-types")
    public ResponseEntity<ApiResponse<RelationshipType[]>> getRelationshipTypes() {
        try {
            return ResponseEntity.ok(ApiResponse.success("Relationship types retrieved successfully", RelationshipType.values()));
        } catch (Exception e) {
            log.error("Error fetching relationship types", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to retrieve relationship types: " + e.getMessage()));
        }
    }

    /**
     * Get all available relationship sides
     */
    @GetMapping("/relationship-sides")
    public ResponseEntity<ApiResponse<RelationshipSide[]>> getRelationshipSides() {
        try {
            return ResponseEntity.ok(ApiResponse.success("Relationship sides retrieved successfully", RelationshipSide.values()));
        } catch (Exception e) {
            log.error("Error fetching relationship sides", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to retrieve relationship sides: " + e.getMessage()));
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<String>> healthCheck() {
        return ResponseEntity.ok(ApiResponse.success("Family Tree API is running"));
    }
}