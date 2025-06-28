package com.example.samajconnectbackend.service;

import com.example.samajconnectbackend.dto.*;
import com.example.samajconnectbackend.entity.Samaj;
import com.example.samajconnectbackend.entity.User;
import com.example.samajconnectbackend.repository.SamajRepository;
import com.example.samajconnectbackend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private OtpService otpService;

    @Autowired
    private SamajRepository samajRepository;

    public LoginResponse authenticateUser(LoginRequest loginRequest) {
        System.out.println("Login Method called : " + LocalDateTime.now());
        try {
            Optional<User> userOptional = userRepository.findByEmail(loginRequest.getEmail());

            if (userOptional.isEmpty()) {
                return new LoginResponse(false, "User not found");
            }

            User user = userOptional.get();

            // Check if email is verified
            if (!user.isEmailVerified()) {
                return new LoginResponse(false, "Please verify your email before logging in");
            }

            if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
                return new LoginResponse(false, "Invalid password");
            }

            String token = jwtService.generateToken(user.getEmail());

            UserDto userDto = new UserDto();
            userDto.setId(user.getId());
            userDto.setGender(String.valueOf(user.getGender()));
            userDto.setEmail(user.getEmail());
            userDto.setName(user.getName());

            return new LoginResponse(true, "Login successful", token, userDto);

        } catch (Exception e) {
            logger.error("Authentication failed for email {}: {}", loginRequest.getEmail(), e.getMessage());
            return new LoginResponse(false, "Authentication failed: " + e.getMessage());
        }
    }

    /**
     * Updated getUserById method to use the new conversion method
     */
    @Transactional
    public UserDetailsResponse getUserById(Long userId) {
        try {
            Optional<User> userOptional = userRepository.findById(userId);
            if (userOptional.isEmpty()) {
                return new UserDetailsResponse(false, "User not found", null);
            }
            User user = userOptional.get();
            UserWithSamajDto userDto = convertToUserWithSamajDto(user);
            return new UserDetailsResponse(true, "User details retrieved successfully", userDto);
        } catch (Exception e) {
            logger.error("Error getting user by ID {}: {}", userId, e.getMessage());
            return new UserDetailsResponse(false, "Error retrieving user details: " + e.getMessage(), null);
        }
    }

    /**
     * User registration method
     */
    public RegisterResponse registerUser(RegisterRequest registerRequest) {
        System.out.println("Register Method called : " + LocalDateTime.now());
        String email = registerRequest.getEmail().trim();
        String userGender;
        try {
            // Check if email already exists
            boolean exists = emailExists(email);
            if (exists) {
                logger.info("Registration failed: Email already exists: {}", email);
                return new RegisterResponse(false, "Email already exists");
            }
            try {
                userGender = registerRequest.getGender().trim().toUpperCase();
            } catch (IllegalArgumentException e) {
                return new RegisterResponse(false, "Invalid gender. Allowed values: MALE, FEMALE, OTHER");
            }
            User user = new User();
            user.setEmail(email);
            user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
            user.setName(registerRequest.getName());
            user.setEmailVerified(false);
            user.setGender(userGender);

            // Generate OTP for email verification
            String otp = otpService.generateOtp();
            user.setOtpCode(otp);
            user.setOtpExpiry(LocalDateTime.now().plusMinutes(10));

            // Handle admin vs individual user registration
            if (registerRequest.getIsAdmin() != null && registerRequest.getIsAdmin()) {
                // Admin user creating samaj - this should be handled by SamajService.createSamajWithAdmin
                return new RegisterResponse(false, "Admin registration should use samaj creation endpoint");
            } else {
                // Individual user joining existing samaj
                if (registerRequest.getSamajId() == null) {
                    return new RegisterResponse(false, "Samaj selection is required for individual users");
                }

                // Verify samaj exists
                Optional<Samaj> samajOptional = samajRepository.findById(registerRequest.getSamajId());
                if (samajOptional.isEmpty()) {
                    return new RegisterResponse(false, "Selected samaj does not exist");
                }

                user.setIsAdmin(false);
                user.setSamaj(samajOptional.get());
            }

            // Save the user
            User savedUser = userRepository.save(user);

            // Send verification email
            boolean emailSent = emailService.sendOtpEmail(savedUser.getEmail(), otp, savedUser.getName());

            if (!emailSent) {
                logger.warn("Registration successful but failed to send verification email: {}", email);
                return new RegisterResponse(false, "Registration successful but failed to send verification email. Please try resending OTP.");
            }

            logger.info("User registered successfully: {}", email);
            return new RegisterResponse(true, "Registration successful! Please check your email for verification code.");

        } catch (DataIntegrityViolationException e) {
            logger.error("Registration failed due to data integrity violation: {}", e.getMessage());
            return new RegisterResponse(false, "Email already exists");
        } catch (Exception e) {
            logger.error("Registration failed: {}", e.getMessage());
            return new RegisterResponse(false, "Registration failed: " + e.getMessage());
        }
    }

    /**
     * Email verification method
     */
    public VerifyEmailResponse verifyEmail(VerifyEmailRequest request) {
        try {
            Optional<User> userOptional = userRepository.findByEmail(request.getEmail());

            if (userOptional.isEmpty()) {
                return new VerifyEmailResponse(false, "User not found");
            }

            User user = userOptional.get();

            if (user.isEmailVerified()) {
                return new VerifyEmailResponse(false, "Email is already verified");
            }

            // Validate OTP
            if (!otpService.isOtpValid(request.getOtp(), user.getOtpCode(), user.getOtpExpiry())) {
                return new VerifyEmailResponse(false, "Invalid or expired OTP");
            }

            // Update user verification status
            user.setEmailVerified(true);
            user.setOtpCode(null);
            user.setOtpExpiry(null);
            userRepository.save(user);

            logger.info("Email verified successfully for user: {}", user.getEmail());
            return new VerifyEmailResponse(true, "Email verified successfully! You can now login.");

        } catch (Exception e) {
            logger.error("Email verification failed for {}: {}", request.getEmail(), e.getMessage());
            return new VerifyEmailResponse(false, "Verification failed: " + e.getMessage());
        }
    }

    /**
     * Method to check if email exists
     */
    public boolean emailExists(String email) {
        return userRepository.findByEmail(email).isPresent();
    }

    public ResendOtpResponse resendOtp(String email) {
        try {
            Optional<User> userOptional = userRepository.findByEmail(email);

            if (userOptional.isEmpty()) {
                return new ResendOtpResponse(false, "User not found");
            }

            User user = userOptional.get();

            if (user.isEmailVerified()) {
                return new ResendOtpResponse(false, "Email is already verified");
            }

            // Generate new OTP
            String newOtp = otpService.generateOtp();
            user.setOtpCode(newOtp);
            user.setOtpExpiry(otpService.getOtpExpiry());
            userRepository.save(user);

            // Send new OTP email
            boolean emailSent = emailService.sendOtpEmail(user.getEmail(), newOtp, user.getName());

            if (!emailSent) {
                return new ResendOtpResponse(false, "Failed to send OTP email");
            }

            logger.info("OTP resent successfully to: {}", email);
            return new ResendOtpResponse(true, "OTP sent successfully! Please check your email.");

        } catch (Exception e) {
            logger.error("Failed to resend OTP to {}: {}", email, e.getMessage());
            return new ResendOtpResponse(false, "Failed to resend OTP: " + e.getMessage());
        }
    }

    public String initiatePasswordReset(String email) {
        Optional<User> userOptional = userRepository.findByEmail(email);

        if (userOptional.isEmpty()) {
            return "Email not registered";
        }

        User user = userOptional.get();

        String otp = otpService.generateOtp();
        user.setOtpCode(otp);
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(10));
        userRepository.save(user);

        boolean sent = emailService.sendOtpEmail(user.getEmail(), otp, user.getName());
        return sent ? "OTP sent to your email" : "Failed to send OTP";
    }

    public String resetPassword(ResetPasswordRequest request) {
        Optional<User> userOptional = userRepository.findByEmail(request.getEmail());

        if (userOptional.isEmpty()) {
            return "Invalid email";
        }

        User user = userOptional.get();
        if (!otpService.isOtpValid(request.getOtp(), user.getOtpCode(), user.getOtpExpiry())) {
            return "Invalid or expired OTP";
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setOtpCode(null);
        user.setOtpExpiry(null);
        userRepository.save(user);

        return "Password reset successfully";
    }

    /**
     * Update user profile
     */
    @Transactional
    public UpdateUserProfileResponse updateUserProfile(Long userId, UpdateUserProfileRequest request) {
        try {
            Optional<User> userOptional = userRepository.findById(userId);
            if (userOptional.isEmpty()) {
                return new UpdateUserProfileResponse(false, "User not found");
            }
            System.out.println(userOptional.get());
            User existingUser = userOptional.get();

            // Update fields if provided (similar to EventService approach)
            if (request.getName() != null && !request.getName().trim().isEmpty()) {
                existingUser.setName(request.getName().trim());
            }

            if (request.getPhoneNumber() != null) {
                existingUser.setPhoneNumber(request.getPhoneNumber().trim());
            }

            if (request.getAddress() != null) {
                existingUser.setAddress(request.getAddress().trim());
            }

            // Handle profile image update (similar to event image handling)
            if (request.getImageBase64() != null) {
                if (request.getImageBase64().isEmpty()) {
                    existingUser.setProfileImg(null); // Remove image
                } else {
                    try {
                        existingUser.setProfileImg(request.getImageBytes()); // Update image
                    } catch (RuntimeException e) {
                        return new UpdateUserProfileResponse(false, "Invalid image format: " + e.getMessage());
                    }
                }
            }

            // Save the updated user
            User updatedUser = userRepository.save(existingUser);
            logger.info("User profile updated successfully for user ID: {}", userId);

            // Convert to DTO and return
            UserWithSamajDto userDto = convertToUserWithSamajDto(updatedUser);
            System.out.println(userDto.toString());

            return new UpdateUserProfileResponse(true, "Profile updated successfully", userDto);
        } catch (Exception e) {
            logger.error("Error updating user profile for user ID {}: {}", userId, e.getMessage());
            return new UpdateUserProfileResponse(false, "Failed to update profile: " + e.getMessage());
        }
    }

    /**
     * Helper method to convert User entity to UserWithSamajDto
     */
    private UserWithSamajDto convertToUserWithSamajDto(User user) {
        UserWithSamajDto userDto = new UserWithSamajDto();
        userDto.setId(user.getId());
        userDto.setName(user.getName());
        userDto.setEmail(user.getEmail());
        userDto.setGender(String.valueOf(user.getGender()));
        userDto.setIsAdmin(user.getIsAdmin());
        userDto.setProfileImg(user.getProfileImg());
        userDto.setPhoneNumber(user.getPhoneNumber());
        userDto.setAddress(user.getAddress());
        userDto.setCreatedAt(user.getCreatedAt());
        userDto.setUpdatedAt(user.getUpdatedAt());

        // Add samaj information
        if (user.getSamaj() != null) {
            SamajDto samajDto = new SamajDto();
            samajDto.setId(user.getSamaj().getId());
            samajDto.setName(user.getSamaj().getName());
            samajDto.setDescription(user.getSamaj().getDescription());
            userDto.setSamaj(samajDto);
        }

        return userDto;
    }

    /**
     * Search samaj members for relationship requests
     */
    @Transactional(readOnly = true)
    public ApiResponse<SamajMemberSearchResponse> searchSamajMembers(Long currentUserId, SamajMemberSearchDto searchDto) {
        try {
            // Get current user to validate and get samaj info
            Optional<User> currentUserOpt = userRepository.findById(currentUserId);
            if (currentUserOpt.isEmpty()) {
                return ApiResponse.error("Current user not found");
            }

            User currentUser = currentUserOpt.get();
            if (currentUser.getSamaj() == null) {
                return ApiResponse.error("User is not associated with any samaj");
            }

            Long samajId = currentUser.getSamaj().getId();

            // Create pageable
            Pageable pageable = PageRequest.of(searchDto.getPage(), searchDto.getSize());

            // Search users
            Page<User> userPage;
            if (searchDto.getQuery() != null && !searchDto.getQuery().trim().isEmpty()) {
                userPage = userRepository.findSamajMembersByQuery(samajId, currentUserId, searchDto.getQuery().trim(), pageable);
            } else {
                userPage = userRepository.findAllSamajMembers(samajId, currentUserId, pageable);
            }

            // Convert to DTOs with relationship status
            List<SamajMemberDto> memberDtos = userPage.getContent().stream()
                    .map(user -> convertToSamajMemberDto(user, currentUserId))
                    .collect(Collectors.toList());

            // Build response
            SamajMemberSearchResponse response = new SamajMemberSearchResponse();
            response.setMembers(memberDtos);
            response.setTotalResults(userPage.getTotalElements());
            response.setCurrentPage(userPage.getNumber());
            response.setTotalPages(userPage.getTotalPages());
            response.setHasNext(userPage.hasNext());
            response.setHasPrevious(userPage.hasPrevious());
            response.setMessage("Search completed successfully");

            logger.info("Samaj member search completed for user {} in samaj {}. Found {} results",
                    currentUserId, samajId, userPage.getTotalElements());

            return ApiResponse.success("Samaj members retrieved successfully", response);

        } catch (Exception e) {
            logger.error("Error searching samaj members for user {}: {}", currentUserId, e.getMessage(), e);
            return ApiResponse.error("Failed to search samaj members: " + e.getMessage());
        }
    }

    /**
     * Convert User entity to SamajMemberDto with relationship status
     */
    private SamajMemberDto convertToSamajMemberDto(User user, Long currentUserId) {
        SamajMemberDto dto = new SamajMemberDto();
        dto.setUserId(user.getId());
        dto.setName(user.getName());
        dto.setEmail(user.getEmail());
        dto.setGender(user.getGender());
        dto.setPhoneNumber(user.getPhoneNumber());
        dto.setAddress(user.getAddress());

        // Set profile image
        dto.setProfileImageFromBytes(user.getProfileImg());

        // Determine relationship status
        SamajMemberDto.RelationshipStatus status = determineRelationshipStatus(currentUserId, user.getId());
        dto.setRelationshipStatus(status);
        dto.setRelationshipStatusText(getRelationshipStatusText(status));

        return dto;
    }

    /**
     * Determine the relationship status between current user and target user
     */
    private SamajMemberDto.RelationshipStatus determineRelationshipStatus(Long currentUserId, Long targetUserId) {
        try {
            // Check if they already have a relationship
            if (userRepository.hasRelationship(currentUserId, targetUserId)) {
                return SamajMemberDto.RelationshipStatus.ALREADY_RELATED;
            }

            // Check if current user sent a request to target user
            if (userRepository.hasSentRequestTo(currentUserId, targetUserId)) {
                return SamajMemberDto.RelationshipStatus.REQUEST_SENT;
            }

            // Check if target user sent a request to current user
            if (userRepository.hasReceivedRequestFrom(currentUserId, targetUserId)) {
                return SamajMemberDto.RelationshipStatus.REQUEST_RECEIVED;
            }

            // No relationship or pending requests - available for new request
            return SamajMemberDto.RelationshipStatus.AVAILABLE;

        } catch (Exception e) {
            logger.error("Error determining relationship status between users {} and {}: {}",
                    currentUserId, targetUserId, e.getMessage());
            return SamajMemberDto.RelationshipStatus.AVAILABLE; // Default to available on error
        }
    }

    /**
     * Get human-readable text for relationship status
     */
    private String getRelationshipStatusText(SamajMemberDto.RelationshipStatus status) {
        return switch (status) {
            case AVAILABLE -> "Available";
            case ALREADY_RELATED -> "Already Related";
            case REQUEST_SENT -> "Request Sent";
            case REQUEST_RECEIVED -> "Request Received";
            case SAME_USER -> "You";
        };
    }
    // Add these methods to your existing UserService class

    /**
     * Get all members of a specific samaj by samaj ID
     */
    @Transactional(readOnly = true)
    public ApiResponse<SamajMembersResponse> getAllSamajMembers(Long samajId, int page, int size) {
        try {
            // Validate if samaj exists
            Optional<Samaj> samajOptional = samajRepository.findById(samajId);
            if (samajOptional.isEmpty()) {
                return ApiResponse.error("Samaj not found");
            }

            Samaj samaj = samajOptional.get();

            // Create pageable
            Pageable pageable = PageRequest.of(page, size);

            // Get all members of the samaj
            Page<User> userPage = userRepository.findAllMembersBySamajId(samajId, pageable);

            // Convert to detailed DTOs
            List<DetailedUserDto> memberDtos = userPage.getContent().stream()
                    .map(this::convertToDetailedUserDto)
                    .collect(Collectors.toList());

            // Build response
            SamajMembersResponse response = new SamajMembersResponse();
            response.setMembers(memberDtos);
            response.setTotalMembers(userPage.getTotalElements());
            response.setCurrentPage(userPage.getNumber());
            response.setTotalPages(userPage.getTotalPages());
            response.setHasNext(userPage.hasNext());
            response.setHasPrevious(userPage.hasPrevious());
            response.setSamajId(samajId);
            response.setSamajName(samaj.getName());
            response.setMessage("Members retrieved successfully");

            logger.info("Retrieved {} members for samaj {} (page {}/{})",
                    memberDtos.size(), samajId, page + 1, userPage.getTotalPages());

            return ApiResponse.success("Samaj members retrieved successfully", response);

        } catch (Exception e) {
            logger.error("Error retrieving samaj members for samaj {}: {}", samajId, e.getMessage(), e);
            return ApiResponse.error("Failed to retrieve samaj members: " + e.getMessage());
        }
    }

    /**
     * Search members of a specific samaj by samaj ID
     */
    @Transactional(readOnly = true)
    public ApiResponse<SamajMembersResponse> searchSamajMembersBySamajId(SamajMemberSearchByIdDto searchDto) {
        try {
            // Validate if samaj exists
            Optional<Samaj> samajOptional = samajRepository.findById(searchDto.getSamajId());
            if (samajOptional.isEmpty()) {
                return ApiResponse.error("Samaj not found");
            }

            Samaj samaj = samajOptional.get();

            // Create pageable
            Pageable pageable = PageRequest.of(searchDto.getPage(), searchDto.getSize());

            // Search members
            Page<User> userPage;
            if (searchDto.getQuery() != null && !searchDto.getQuery().trim().isEmpty()) {
                userPage = userRepository.findSamajMembersBySamajIdAndQuery(
                        searchDto.getSamajId(),
                        searchDto.getQuery().trim(),
                        pageable
                );
            } else {
                userPage = userRepository.findAllMembersBySamajId(searchDto.getSamajId(), pageable);
            }

            // Convert to detailed DTOs
            List<DetailedUserDto> memberDtos = userPage.getContent().stream()
                    .map(this::convertToDetailedUserDto)
                    .collect(Collectors.toList());

            // Build response
            SamajMembersResponse response = new SamajMembersResponse();
            response.setMembers(memberDtos);
            response.setTotalMembers(userPage.getTotalElements());
            response.setCurrentPage(userPage.getNumber());
            response.setTotalPages(userPage.getTotalPages());
            response.setHasNext(userPage.hasNext());
            response.setHasPrevious(userPage.hasPrevious());
            response.setSamajId(searchDto.getSamajId());
            response.setSamajName(samaj.getName());
            response.setMessage("Search completed successfully");

            logger.info("Search completed for samaj {} with query '{}'. Found {} results",
                    searchDto.getSamajId(), searchDto.getQuery(), userPage.getTotalElements());

            return ApiResponse.success("Search completed successfully", response);

        } catch (Exception e) {
            logger.error("Error searching samaj members for samaj {}: {}",
                    searchDto.getSamajId(), e.getMessage(), e);
            return ApiResponse.error("Failed to search samaj members: " + e.getMessage());
        }
    }

    /**
     * Get basic samaj statistics
     */
    @Transactional(readOnly = true)
    public ApiResponse<SamajStatsDto> getSamajStatistics(Long samajId) {
        try {
            // Validate if samaj exists
            Optional<Samaj> samajOptional = samajRepository.findById(samajId);
            if (samajOptional.isEmpty()) {
                return ApiResponse.error("Samaj not found");
            }

            Samaj samaj = samajOptional.get();

            // Get member count
            long totalMembers = userRepository.countMembersBySamajId(samajId);

            // Build stats
            SamajStatsDto stats = new SamajStatsDto();
            stats.setSamajId(samajId);
            stats.setSamajName(samaj.getName());
            stats.setTotalMembers(totalMembers);
            stats.setDescription(samaj.getDescription());

            return ApiResponse.success("Statistics retrieved successfully", stats);

        } catch (Exception e) {
            logger.error("Error retrieving samaj statistics for samaj {}: {}", samajId, e.getMessage(), e);
            return ApiResponse.error("Failed to retrieve statistics: " + e.getMessage());
        }
    }

    /**
     * Helper method to convert User entity to DetailedUserDto
     */
    private DetailedUserDto convertToDetailedUserDto(User user) {
        DetailedUserDto dto = new DetailedUserDto();
        dto.setId(user.getId());
        dto.setName(user.getName());
        dto.setEmail(user.getEmail());
        dto.setGender(user.getGender());
        dto.setPhoneNumber(user.getPhoneNumber());
        dto.setAddress(user.getAddress());
        dto.setIsAdmin(user.getIsAdmin());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setUpdatedAt(user.getUpdatedAt());

        // Set profile image
        dto.setProfileImageFromBytes(user.getProfileImg());

        // Add samaj information
        if (user.getSamaj() != null) {
            SamajDto samajDto = new SamajDto();
            samajDto.setId(user.getSamaj().getId());
            samajDto.setName(user.getSamaj().getName());
            samajDto.setDescription(user.getSamaj().getDescription());
            dto.setSamaj(samajDto);
        }

        return dto;
    }
}