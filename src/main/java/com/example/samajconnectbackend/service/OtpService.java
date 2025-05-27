package com.example.samajconnectbackend.service;

import org.springframework.stereotype.Service;
import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
public class OtpService {

    private static final String CHARACTERS = "0123456789";
    private static final int OTP_LENGTH = 6;
    private static final int OTP_VALIDITY_MINUTES = 10;

    public String generateOtp() {
        SecureRandom random = new SecureRandom();
        StringBuilder otp = new StringBuilder();

        for (int i = 0; i < OTP_LENGTH; i++) {
            otp.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }

        return otp.toString();
    }

    public LocalDateTime getOtpExpiry() {
        return LocalDateTime.now().plusMinutes(OTP_VALIDITY_MINUTES);
    }

    public boolean isOtpValid(String inputOtp, String storedOtp, LocalDateTime expiry) {
        if (inputOtp == null || storedOtp == null || expiry == null) {
            return false;
        }

        return inputOtp.equals(storedOtp) && LocalDateTime.now().isBefore(expiry);
    }
}