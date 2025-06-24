package com.example.samajconnectbackend.dto;

import lombok.Data;

@Data
public class UserDto {
    private Long id;
    private String name;
    private String email;
    private String gender;
    private Boolean isAdmin;
    private String profileImg;
    private String phoneNumber;
    private String address;
}
