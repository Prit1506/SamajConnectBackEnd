package com.example.samajconnectbackend.dto;

public class UserIdRequest {

    private Long userId;

    public UserIdRequest() {
    }

    public UserIdRequest(Long userId) {
        this.userId = userId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
