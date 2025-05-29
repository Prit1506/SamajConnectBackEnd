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

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    private String address;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "profile_img")
    private String profileImg;

    @Column(name = "is_admin")
    private Boolean isAdmin = false;

    @Column(name = "email_verified")
    private Boolean emailVerified = false;

    @Column(name = "otp_code")
    private String otpCode;

    @Column(name = "otp_expiry")
    private LocalDateTime otpExpiry;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // Many users belong to one samaj
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "samaj_id", nullable = false)
    private Samaj samaj;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public boolean isEmailVerified() {
        return emailVerified != null && emailVerified;
    }
}