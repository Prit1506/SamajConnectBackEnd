package com.example.samajconnectbackend.controller;
import com.example.samajconnectbackend.dto.*;
import com.example.samajconnectbackend.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {
    @Autowired
    private UserService userService;

    @GetMapping("/{userId}")
    public ResponseEntity<UserDetailsResponse> getUserById(@PathVariable Long userId) {
        try {
            UserDetailsResponse response = userService.getUserById(userId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            UserDetailsResponse errorResponse = new UserDetailsResponse(false, "Failed to get user details: " + e.getMessage(), null);
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
}