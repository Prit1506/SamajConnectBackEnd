package com.example.samajconnectbackend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public boolean sendOtpEmail(String toEmail, String otpCode, String userName) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Email Verification - OTP Code");
            message.setText(buildOtpEmailBody(otpCode, userName));

            mailSender.send(message);
            logger.info("OTP email sent successfully to: {}", toEmail);
            return true;
        } catch (Exception e) {
            logger.error("Failed to send OTP email to {}: {}", toEmail, e.getMessage());
            return false;
        }
    }

    private String buildOtpEmailBody(String otpCode, String userName) {
        return String.format(
                "Dear %s,\n\n" +
                        "Thank you for registering with Samaj Connect!\n\n" +
                        "Your email verification code is: %s\n\n" +
                        "This code will expire in 10 minutes. Please enter this code in the app to verify your email address.\n\n" +
                        "If you didn't request this verification, please ignore this email.\n\n" +
                        "Best regards,\n" +
                        "Samaj Connect Team",
                userName, otpCode
        );
    }
}