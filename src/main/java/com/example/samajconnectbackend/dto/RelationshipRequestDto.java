package com.example.samajconnectbackend.dto;

import com.example.samajconnectbackend.entity.RelationshipSide;
import com.example.samajconnectbackend.entity.RelationshipType;
import com.example.samajconnectbackend.entity.RequestStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelationshipRequestDto {
    private Long id;
    private Long requesterUserId;
    private String requesterName;
    private String requesterEmail;
    private String requesterProfileImage;
    private Long targetUserId;
    private String targetName;
    private String targetEmail;
    private String targetProfileImage;
    private RelationshipType relationshipType;
    private String relationshipDisplayName;
    private RelationshipSide relationshipSide;
    private String relationshipSideDisplayName;
    private Integer generationLevel;
    private String requestMessage;
    private RequestStatus status;
    private String statusDisplayName;
    private LocalDateTime createdAt;
    private LocalDateTime respondedAt;
    private boolean isIncoming; // true if current user is target

}