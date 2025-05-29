package com.example.samajconnectbackend.controller;

import com.example.samajconnectbackend.dto.*;
import com.example.samajconnectbackend.service.SamajService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/samaj")
@CrossOrigin(origins = "*")
public class SamajController {

    private static final Logger logger = LoggerFactory.getLogger(SamajController.class);

    @Autowired
    private SamajService samajService;

    /**
     * Get all available samajs
     */
    @GetMapping("/all")
    public ResponseEntity<SamajListResponse> getAllSamajs() {
        logger.info("Fetching all samajs");
        SamajListResponse response = samajService.getAllSamajs();
        return ResponseEntity.ok(response);
    }

    /**
     * Check if samaj name exists
     */
    @GetMapping("/check/{name}")
    public ResponseEntity<CheckSamajResponse> checkSamajExists(@PathVariable String name) {
        logger.info("Checking if samaj exists: {}", name);

        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new CheckSamajResponse(true, "Samaj name cannot be empty"));
        }

        CheckSamajResponse response = samajService.checkSamajExists(name);
        return ResponseEntity.ok(response);
    }

    /**
     * Create new samaj with admin user
     */
    @PostMapping("/create")
    public ResponseEntity<CreateSamajResponse> createSamaj(@RequestBody CreateSamajRequest request) {
        logger.info("Creating new samaj: {}", request.getName());

        // Validate request
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new CreateSamajResponse(false, "Samaj name is required", null));
        }

        if (request.getAdminEmail() == null || request.getAdminEmail().trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new CreateSamajResponse(false, "Admin email is required", null));
        }

        if (request.getAdminPassword() == null || request.getAdminPassword().trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new CreateSamajResponse(false, "Admin password is required", null));
        }

        CreateSamajResponse response = samajService.createSamajWithAdmin(request);
        return ResponseEntity.ok(response);
    }
}