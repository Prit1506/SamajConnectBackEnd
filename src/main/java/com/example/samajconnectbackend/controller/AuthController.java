package com.example.samajconnectbackend.controller;

import com.example.samajconnectbackend.dto.*;
import com.example.samajconnectbackend.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private UserService userService;

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest loginRequest) {
        LoginResponse response = userService.authenticateUser(loginRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@RequestBody RegisterRequest registerRequest) {

        if (registerRequest.getEmail() == null || registerRequest.getEmail().isBlank()) {
            return ResponseEntity.badRequest().body(new RegisterResponse(false, "Email cannot be empty"));
        }
        RegisterResponse response = userService.registerUser(registerRequest);
        if (response == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new RegisterResponse(false, "Internal server error: No response from service"));
        }

        // Log the incoming request
        logger.info("Received registration request for email: {}", registerRequest.getEmail());
        
        // Log the response
        logger.info("Registration response: success={}, message={}", 
                   response.isSuccess(), 
                   response.getMessage());
                   
        // Return the response
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-email")
    public ResponseEntity<VerifyEmailResponse> verifyEmail(@RequestBody VerifyEmailRequest request) {
        VerifyEmailResponse response = userService.verifyEmail(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<ResendOtpResponse> resendOtp(@RequestParam String email) {
        ResendOtpResponse response = userService.resendOtp(email);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        String result = userService.initiatePasswordReset(request.getEmail());

        Map<String, String> response = new HashMap<>();
        response.put("message", result);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@RequestBody ResetPasswordRequest request) {
        String result = userService.resetPassword(request);

        Map<String, String> response = new HashMap<>();
        response.put("message", result);

        return ResponseEntity.ok(response);
    }
}