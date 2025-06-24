package com.example.samajconnectbackend.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
@Getter
@Setter
public class UserWithSamajDto {

    private Long id;
    private String name;
    private String email;
    private Boolean isAdmin;
    private byte[] profileImg;
    private String phoneNumber;
    private String gender;
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

    // Helper method to get base64 encoded image for frontend
    public String getProfileImgBase64() {
        if (profileImg != null && profileImg.length > 0) {
            return Base64.getEncoder().encodeToString(profileImg);
        }
        return null;
    }

    @Override
    public String toString() {
        return "UserWithSamajDto{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", isAdmin=" + isAdmin +
                ", profileImg=" + Arrays.toString(profileImg) +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", address='" + address + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", samaj=" + samaj +
                '}';
    }
}