package com.example.samajconnectbackend.dto;

public class UpdateUserProfileResponse {

    private boolean success;
    private String message;
    private UserWithSamajDto userData;

    // Constructors
    public UpdateUserProfileResponse() {}

    public UpdateUserProfileResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public UpdateUserProfileResponse(boolean success, String message, UserWithSamajDto userData) {
        this.success = success;
        this.message = message;
        this.userData = userData;
    }

    // Getters and Setters
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public UserWithSamajDto getUserData() {
        return userData;
    }

    public void setUserData(UserWithSamajDto userData) {
        this.userData = userData;
    }
}