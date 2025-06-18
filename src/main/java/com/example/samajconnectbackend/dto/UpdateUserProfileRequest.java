package com.example.samajconnectbackend.dto;

import java.util.Base64;

public class UpdateUserProfileRequest {

    private String name;
    private String phoneNumber;
    private String address;
    private String imageBase64; // Base64 encoded image string

    // Constructors
    public UpdateUserProfileRequest() {}

    public UpdateUserProfileRequest(String name, String phoneNumber, String address, String imageBase64) {
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.address = address;
        this.imageBase64 = imageBase64;
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getImageBase64() {
        return imageBase64;
    }

    public void setImageBase64(String imageBase64) {
        this.imageBase64 = imageBase64;
    }

    // Helper method to convert base64 to bytes (similar to EventDTO)
    public byte[] getImageBytes() {
        if (imageBase64 != null && !imageBase64.isEmpty()) {
            try {
                // Remove data URL prefix if present (e.g., "data:image/jpeg;base64,")
                String base64Data = imageBase64;
                if (imageBase64.contains(",")) {
                    base64Data = imageBase64.substring(imageBase64.indexOf(",") + 1);
                }
                return Base64.getDecoder().decode(base64Data);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Invalid base64 image data");
            }
        }
        return null;
    }
}