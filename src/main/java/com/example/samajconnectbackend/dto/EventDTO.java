package com.example.samajconnectbackend.dto;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.Base64;

public class EventDTO {
    private Long id;
    private String eventTitle;
    private String eventDescription;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime eventDate;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    private Long createdBy;
    private Long samajId;
    private String imageBase64; // Base64 encoded image

    // Constructors
    public EventDTO() {}

    public EventDTO(Long id, String eventTitle, String eventDescription,
                    LocalDateTime eventDate, LocalDateTime createdAt,
                    Long createdBy, Long samajId, byte[] imgData) {
        this.id = id;
        this.eventTitle = eventTitle;
        this.eventDescription = eventDescription;
        this.eventDate = eventDate;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.samajId = samajId;
        this.imageBase64 = imgData != null ? Base64.getEncoder().encodeToString(imgData) : null;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEventTitle() { return eventTitle; }
    public void setEventTitle(String eventTitle) { this.eventTitle = eventTitle; }

    public String getEventDescription() { return eventDescription; }
    public void setEventDescription(String eventDescription) { this.eventDescription = eventDescription; }

    public LocalDateTime getEventDate() { return eventDate; }
    public void setEventDate(LocalDateTime eventDate) { this.eventDate = eventDate; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public Long getSamajId() { return samajId; }
    public void setSamajId(Long samajId) { this.samajId = samajId; }

    public String getImageBase64() { return imageBase64; }
    public void setImageBase64(String imageBase64) { this.imageBase64 = imageBase64; }
}