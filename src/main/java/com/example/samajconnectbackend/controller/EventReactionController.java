package com.example.samajconnectbackend.controller;

import com.example.samajconnectbackend.dto.ApiResponse;
import com.example.samajconnectbackend.dto.ReactionRequest;
import com.example.samajconnectbackend.dto.ReactionStats;
import com.example.samajconnectbackend.dto.UserIdRequest;
import com.example.samajconnectbackend.entity.EventReaction;
import com.example.samajconnectbackend.entity.User;
import com.example.samajconnectbackend.service.EventReactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/events/{eventId}/reactions")
@CrossOrigin(origins = "*")
public class EventReactionController {

    @Autowired
    private EventReactionService eventReactionService;

    @PostMapping
    public ResponseEntity<ApiResponse> addReaction(
            @PathVariable Long eventId,
            @RequestBody ReactionRequest request) {


        try {
            EventReaction reaction = eventReactionService.addOrUpdateReaction(
                    request.getUserId(), eventId, request.getReactionType());

            if (reaction == null) {
                return ResponseEntity.ok(new ApiResponse(true, "Reaction removed successfully"));
            } else {
                return ResponseEntity.ok(new ApiResponse(true, "Reaction added successfully", reaction));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Failed to add reaction: " + e.getMessage()));
        }
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse> removeReaction(
            @PathVariable Long eventId,
            @RequestBody UserIdRequest userId) {


        try {
            eventReactionService.removeReaction(userId.getUserId(), eventId);
            return ResponseEntity.ok(new ApiResponse(true, "Reaction removed successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Failed to remove reaction: " + e.getMessage()));
        }
    }

    @PostMapping("/stats")
    public ResponseEntity<ApiResponse> getReactionStats(
            @PathVariable Long eventId,
            @RequestBody UserIdRequest userId) {
        try {
            ReactionStats stats = eventReactionService.getReactionStatsWithUserReaction(eventId, userId.getUserId());
            return ResponseEntity.ok(new ApiResponse(true, "Stats retrieved successfully", stats));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Failed to get stats: " + e.getMessage()));
        }
    }

    @PostMapping("/my-reaction")
    public ResponseEntity<ApiResponse> getMyReaction(
            @PathVariable Long eventId,
            @RequestBody UserIdRequest userId) {

        try {
            var userReaction = eventReactionService.getUserReaction(userId.getUserId(), eventId);
            return ResponseEntity.ok(new ApiResponse(true, "User reaction retrieved", userReaction.orElse(null)));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Failed to get user reaction: " + e.getMessage()));
        }
    }

}