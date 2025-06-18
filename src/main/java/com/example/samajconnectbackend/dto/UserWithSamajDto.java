package com.example.samajconnectbackend.dto;

import java.time.LocalDateTime;
import java.util.Base64;

public class UserWithSamajDto {

    private Long id;
    private String name;
    private String email;
    private Boolean isAdmin;
    private byte[] profileImg;
    private String phoneNumber;
    private String address;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private SamajDto samaj;

    // Constructors
    public UserWithSamajDto() {}

    public UserWithSamajDto(Long id, String name, String email, Boolean isAdmin,
                            byte[] profileImg, String phoneNumber, String address,
                            LocalDateTime createdAt, LocalDateTime updatedAt, SamajDto samaj) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.isAdmin = isAdmin;
        this.profileImg = profileImg;
        this.phoneNumber = phoneNumber;
        this.address = address;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.samaj = samaj;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Boolean getIsAdmin() {
        return isAdmin;
    }

    public void setIsAdmin(Boolean isAdmin) {
        this.isAdmin = isAdmin;
    }

    public byte[] getProfileImg() {
        return profileImg;
    }

    public void setProfileImg(byte[] profileImg) {
        this.profileImg = profileImg;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public SamajDto getSamaj() {
        return samaj;
    }

    public void setSamaj(SamajDto samaj) {
        this.samaj = samaj;
    }

    // Helper method to get base64 encoded image for frontend
    public String getProfileImgBase64() {
        if (profileImg != null && profileImg.length > 0) {
            return Base64.getEncoder().encodeToString(profileImg);
        }
        return null;
    }
}