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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

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
            userDto.setEmail(user.getEmail());
            userDto.setName(user.getName());

            return new LoginResponse(true, "Login successful", token, userDto);

        } catch (Exception e) {
            logger.error("Authentication failed for email {}: {}", loginRequest.getEmail(), e.getMessage());
            return new LoginResponse(false, "Authentication failed: " + e.getMessage());
        }
    }

    @Transactional
    public UserDetailsResponse getUserById(Long userId) {
        try {
            Optional<User> userOptional = userRepository.findById(userId);

            if (userOptional.isEmpty()) {
                return new UserDetailsResponse(false, "User not found", null);
            }

            User user = userOptional.get();

            // Create UserWithSamajDto
            UserWithSamajDto userDto = new UserWithSamajDto();
            userDto.setId(user.getId());
            userDto.setName(user.getName());
            userDto.setEmail(user.getEmail());
            userDto.setIsAdmin(user.getIsAdmin());
            userDto.setProfileImg(user.getProfileImg());
            userDto.setPhoneNumber(user.getPhoneNumber());
            userDto.setAddress(user.getAddress());

            // Add samaj information
            if (user.getSamaj() != null) {
                SamajDto samajDto = new SamajDto();
                samajDto.setId(user.getSamaj().getId());
                samajDto.setName(user.getSamaj().getName());
                samajDto.setDescription(user.getSamaj().getDescription());
                userDto.setSamaj(samajDto);
            }

            return new UserDetailsResponse(true, "User details retrieved successfully", userDto);

        } catch (Exception e) {
            logger.error("Error getting user by ID {}: {}", userId, e.getMessage());
            return new UserDetailsResponse(false, "Error retrieving user details: " + e.getMessage(), null);
        }
    }

    // Other methods stay the same
    
    /**
     * User registration method
     */
    public RegisterResponse registerUser(RegisterRequest registerRequest) {
        System.out.println("Register Method called : " + LocalDateTime.now());
        String email = registerRequest.getEmail().trim();

        try {
            // Check if email already exists
            boolean exists = emailExists(email);
            if (exists) {
                logger.info("Registration failed: Email already exists: {}", email);
                return new RegisterResponse(false, "Email already exists");
            }

            User user = new User();
            user.setEmail(email);
            user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
            user.setName(registerRequest.getName());
            user.setEmailVerified(false);

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

}