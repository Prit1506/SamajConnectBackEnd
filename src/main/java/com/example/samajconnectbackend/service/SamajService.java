package com.example.samajconnectbackend.service;
import com.example.samajconnectbackend.dto.*;
import com.example.samajconnectbackend.entity.Samaj;
import com.example.samajconnectbackend.entity.User;
import com.example.samajconnectbackend.repository.SamajRepository;
import com.example.samajconnectbackend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class SamajService {

    private static final Logger logger = LoggerFactory.getLogger(SamajService.class);

    @Autowired
    private SamajRepository samajRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailService emailService;

    @Autowired
    private OtpService otpService;

    /**
     * Get all samajs
     */
    public SamajListResponse getAllSamajs() {
        try {
            List<Samaj> samajs = samajRepository.findAllOrderByName();

            List<SamajDto> samajDtos = samajs.stream()
                    .map(this::convertToSamajDto)
                    .collect(Collectors.toList());

            return new SamajListResponse(true, "Samajs retrieved successfully", samajDtos);
        } catch (Exception e) {
            logger.error("Error retrieving samajs: {}", e.getMessage());
            return new SamajListResponse(false, "Failed to retrieve samajs", null);
        }
    }

    /**
     * Check if samaj name exists
     */
    public CheckSamajResponse checkSamajExists(String name) {
        try {
            boolean exists = samajRepository.existsByName(name.trim());
            String message = exists ? "Samaj name already exists" : "Samaj name is available";
            return new CheckSamajResponse(exists, message);
        } catch (Exception e) {
            logger.error("Error checking samaj existence: {}", e.getMessage());
            return new CheckSamajResponse(true, "Error checking samaj availability");
        }
    }

    /**
     * Create new samaj with admin user
     */
    @Transactional
    public CreateSamajResponse createSamajWithAdmin(CreateSamajRequest request) {
        try {
            // Check if samaj name already exists
            if (samajRepository.existsByName(request.getName().trim())) {
                return new CreateSamajResponse(false, "Samaj name already exists", null);
            }

            // Check if admin email already exists
            if (userRepository.findByEmail(request.getAdminEmail()).isPresent()) {
                return new CreateSamajResponse(false, "Admin email already exists", null);
            }

            // Create samaj
            Samaj samaj = new Samaj();
            samaj.setName(request.getName().trim());
            samaj.setDescription(request.getDescription());
            samaj.setRules(request.getRules());
            samaj.setEstablishedDate(request.getEstablishedDate());

            Samaj savedSamaj = samajRepository.save(samaj);

            // Create admin user
            User adminUser = new User();
            adminUser.setName(request.getAdminName());
            adminUser.setEmail(request.getAdminEmail().trim());
            adminUser.setPassword(passwordEncoder.encode(request.getAdminPassword()));
            adminUser.setIsAdmin(true);
            adminUser.setEmailVerified(false);
            adminUser.setSamaj(savedSamaj);

            // Generate OTP for email verification
            String otp = otpService.generateOtp();
            adminUser.setOtpCode(otp);
            adminUser.setOtpExpiry(LocalDateTime.now().plusMinutes(10));

            User savedAdmin = userRepository.save(adminUser);

            // Send verification email
            boolean emailSent = emailService.sendOtpEmail(savedAdmin.getEmail(), otp, savedAdmin.getName());

            if (!emailSent) {
                logger.warn("Samaj created but failed to send verification email to admin: {}", request.getAdminEmail());
            }

            SamajDto samajDto = convertToSamajDto(savedSamaj);

            logger.info("Samaj '{}' created successfully with admin: {}", savedSamaj.getName(), request.getAdminEmail());
            return new CreateSamajResponse(true,
                    "Samaj created successfully! Please check your email for verification code.",
                    samajDto);

        } catch (Exception e) {
            logger.error("Error creating samaj: {}", e.getMessage());
            return new CreateSamajResponse(false, "Failed to create samaj: " + e.getMessage(), null);
        }
    }

    /**
     * Get samaj by ID - returns CreateSamajResponse for consistency with frontend
     */
    public CreateSamajResponse getSamajById(Long id) {
        try {
            Optional<Samaj> samajOptional = samajRepository.findById(id);

            if (samajOptional.isPresent()) {
                Samaj samaj = samajOptional.get();
                SamajDto samajDto = convertToSamajDto(samaj);

                logger.info("Samaj retrieved successfully: {}", samaj.getName());
                return new CreateSamajResponse(true, "Samaj retrieved successfully", samajDto);
            } else {
                logger.warn("Samaj not found with ID: {}", id);
                return new CreateSamajResponse(false, "Samaj not found", null);
            }
        } catch (Exception e) {
            logger.error("Error retrieving samaj with ID {}: {}", id, e.getMessage());
            return new CreateSamajResponse(false, "Failed to retrieve samaj: " + e.getMessage(), null);
        }
    }

    /**
     * Get samaj by ID - original method (keeping for backward compatibility)
     */
    public Optional<Samaj> getSamajEntityById(Long id) {
        return samajRepository.findById(id);
    }



    /**
     * Convert Samaj entity to DTO
     */
    private SamajDto convertToSamajDto(Samaj samaj) {
        int memberCount = samajRepository.countMembersBySamajId(samaj.getId());

        return new SamajDto(
                samaj.getId(),
                samaj.getName(),
                samaj.getDescription(),
                samaj.getRules(),
                samaj.getEstablishedDate(),
                memberCount
        );
    }
}