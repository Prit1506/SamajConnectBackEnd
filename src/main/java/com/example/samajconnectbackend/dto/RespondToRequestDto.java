package com.example.samajconnectbackend.dto;

import com.example.samajconnectbackend.entity.RequestStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotNull;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RespondToRequestDto {
    @NotNull(message = "Request ID is required")
    private Long requestId;

    @NotNull(message = "Response status is required")
    private RequestStatus status; // APPROVED or REJECTED

    private String responseMessage;

    @NotNull(message = "Responding user ID is required")
    private Long respondingUserId;
}