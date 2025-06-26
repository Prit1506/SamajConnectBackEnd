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
}
