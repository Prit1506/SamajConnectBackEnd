package com.example.samajconnectbackend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Base64;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DetailedUserDto {
    private Long id;
    private String name;
    private String email;
    private String gender;
    private String phoneNumber;
    private String address;
    private String profileImageBase64;
    private Boolean isAdmin;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private SamajDto samaj;

    // Method to set profile image from byte array
    public void setProfileImageFromBytes(byte[] profileImg) {
        if (profileImg != null && profileImg.length > 0) {
            this.profileImageBase64 = Base64.getEncoder().encodeToString(profileImg);
        }
    }
}