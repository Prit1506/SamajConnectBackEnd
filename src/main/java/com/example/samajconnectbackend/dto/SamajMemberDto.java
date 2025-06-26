package com.example.samajconnectbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Base64;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SamajMemberDto {
    private Long userId;
    private String name;
    private String email;
    private String gender;
    private String phoneNumber;
    private String address;
    private String profileImageBase64;
    private boolean hasProfileImage;
    private RelationshipStatus relationshipStatus;
    private String relationshipStatusText;

    public enum RelationshipStatus {
        AVAILABLE,           // Can send relationship request
        ALREADY_RELATED,     // Already have a relationship
        REQUEST_SENT,        // Current user sent request to this user
        REQUEST_RECEIVED,    // This user sent request to current user
        SAME_USER           // This is the current user (shouldn't happen in search)
    }

    // Helper method to set profile image from byte array
    public void setProfileImageFromBytes(byte[] profileImg) {
        if (profileImg != null && profileImg.length > 0) {
            this.profileImageBase64 = Base64.getEncoder().encodeToString(profileImg);
            this.hasProfileImage = true;
        } else {
            this.profileImageBase64 = null;
            this.hasProfileImage = false;
        }
    }
}
