package com.example.samajconnectbackend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    @Column(name = "email_verified")
    private boolean emailVerified = false;

    @Column(name = "otp_code")
    private String otpCode;

    @Column(name = "otp_expiry")
    private LocalDateTime otpExpiry;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "profile_img")
    private String profileImg;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "address")
    private String address;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}