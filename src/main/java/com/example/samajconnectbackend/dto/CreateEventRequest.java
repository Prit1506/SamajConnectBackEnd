package com.example.samajconnectbackend.dto;

public class CreateEventRequest {
    private String eventTitle;
    private String eventDescription;
    private String location;
    private String eventDate; // Will be parsed to LocalDateTime
    private Long samajId;
    private Long createdBy;
    private String imageBase64;

    // Constructors
    public CreateEventRequest() {}

    // Getters and Setters
    public String getEventTitle() { return eventTitle; }
    public void setEventTitle(String eventTitle) { this.eventTitle = eventTitle; }

    public String getEventDescription() { return eventDescription; }
    public void setEventDescription(String eventDescription) { this.eventDescription = eventDescription; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getEventDate() { return eventDate; }
    public void setEventDate(String eventDate) { this.eventDate = eventDate; }

    public Long getSamajId() { return samajId; }
    public void setSamajId(Long samajId) { this.samajId = samajId; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public String getImageBase64() { return imageBase64; }
    public void setImageBase64(String imageBase64) { this.imageBase64 = imageBase64; }
}