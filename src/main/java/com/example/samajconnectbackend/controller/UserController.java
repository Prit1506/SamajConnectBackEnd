package com.example.samajconnectbackend.controller;

import com.example.samajconnectbackend.dto.*;
import com.example.samajconnectbackend.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserService userService;

    /**
     * Get user by ID
     */
    @GetMapping("/{userId}")
    public ResponseEntity<UserDetailsResponse> getUserById(@PathVariable Long userId) {
        try {
            UserDetailsResponse response = userService.getUserById(userId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error in getUserById endpoint for user ID {}: {}", userId, e.getMessage());
            UserDetailsResponse errorResponse = new UserDetailsResponse(
                    false,
                    "Failed to get user details: " + e.getMessage(),
                    null
            );
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * Update user profile
     */
    @PostMapping("/{userId}/profile")
    public ResponseEntity<UpdateUserProfileResponse> updateUserProfile(
            @PathVariable Long userId,
            @RequestBody UpdateUserProfileRequest request) {
        try {
            // Validate request
            if (request == null) {
                UpdateUserProfileResponse errorResponse = new UpdateUserProfileResponse(
                        false,
                        "Request body is required"
                );
                return ResponseEntity.badRequest().body(errorResponse);
            }

            UpdateUserProfileResponse response = userService.updateUserProfile(userId, request);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            logger.error("Validation error in updateUserProfile for user ID {}: {}", userId, e.getMessage());
            UpdateUserProfileResponse errorResponse = new UpdateUserProfileResponse(
                    false,
                    e.getMessage()
            );
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            logger.error("Error in updateUserProfile endpoint for user ID {}: {}", userId, e.getMessage());
            UpdateUserProfileResponse errorResponse = new UpdateUserProfileResponse(
                    false,
                    "Failed to update profile: " + e.getMessage()
            );
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Search samaj members for relationship requests
     */
    @PostMapping("/{userId}/search-samaj-members")
    public ResponseEntity<ApiResponse<SamajMemberSearchResponse>> searchSamajMembers(
            @PathVariable Long userId,
            @Valid @RequestBody SamajMemberSearchDto searchDto) {
        try {
            logger.info("Searching samaj members for user: {} with query: '{}'", userId, searchDto.getQuery());

            ApiResponse<SamajMemberSearchResponse> response = userService.searchSamajMembers(userId, searchDto);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }

        } catch (Exception e) {
            logger.error("Error in searchSamajMembers endpoint for user ID {}: {}", userId, e.getMessage());
            ApiResponse<SamajMemberSearchResponse> errorResponse = ApiResponse.error(
                    "Failed to search samaj members: " + e.getMessage()
            );
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    // Add these endpoints to your existing UserController class

    /**
     * Get all members of a specific samaj
     */
    @GetMapping("/samaj/{samajId}/members")
    public ResponseEntity<ApiResponse<SamajMembersResponse>> getAllSamajMembers(
            @PathVariable Long samajId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            logger.info("Getting all members for samaj: {} (page: {}, size: {})", samajId, page, size);

            // Validate pagination parameters
            if (page < 0) {
                return ResponseEntity.badRequest().body(
                        ApiResponse.error("Page number cannot be negative")
                );
            }
            if (size < 1 || size > 100) {
                return ResponseEntity.badRequest().body(
                        ApiResponse.error("Page size must be between 1 and 100")
                );
            }

            ApiResponse<SamajMembersResponse> response = userService.getAllSamajMembers(samajId, page, size);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }

        } catch (Exception e) {
            logger.error("Error in getAllSamajMembers endpoint for samaj ID {}: {}", samajId, e.getMessage());
            ApiResponse<SamajMembersResponse> errorResponse = ApiResponse.error(
                    "Failed to retrieve samaj members: " + e.getMessage()
            );
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Search members of a specific samaj
     */
    @PostMapping("/samaj/search-members")
    public ResponseEntity<ApiResponse<SamajMembersResponse>> searchSamajMembersBySamajId(
            @Valid @RequestBody SamajMemberSearchByIdDto searchDto) {
        try {
            logger.info("Searching members for samaj: {} with query: '{}'",
                    searchDto.getSamajId(), searchDto.getQuery());

            ApiResponse<SamajMembersResponse> response = userService.searchSamajMembersBySamajId(searchDto);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }

        } catch (Exception e) {
            logger.error("Error in searchSamajMembersBySamajId endpoint: {}", e.getMessage());
            ApiResponse<SamajMembersResponse> errorResponse = ApiResponse.error(
                    "Failed to search samaj members: " + e.getMessage()
            );
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Get samaj statistics
     */
    @GetMapping("/samaj/{samajId}/stats")
    public ResponseEntity<ApiResponse<SamajStatsDto>> getSamajStatistics(@PathVariable Long samajId) {
        try {
            logger.info("Getting statistics for samaj: {}", samajId);

            ApiResponse<SamajStatsDto> response = userService.getSamajStatistics(samajId);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }

        } catch (Exception e) {
            logger.error("Error in getSamajStatistics endpoint for samaj ID {}: {}", samajId, e.getMessage());
            ApiResponse<SamajStatsDto> errorResponse = ApiResponse.error(
                    "Failed to retrieve samaj statistics: " + e.getMessage()
            );
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}
