// Event Entity
package com.example.samajconnectbackend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_title")
    private String eventTitle;

    @Column(name = "event_description", columnDefinition = "TEXT")
    private String eventDescription;

    @Column(name = "event_date")
    private LocalDateTime eventDate;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "samaj_id")
    private Long samajId;

    @Lob
    @Column(name = "img_data", columnDefinition = "BYTEA")
    private byte[] imgData;


    // Constructors
    public Event() {}

    public Event(String eventTitle, String eventDescription, LocalDateTime eventDate,
                 Long createdBy, Long samajId, byte[] imgData) {
        this.eventTitle = eventTitle;
        this.eventDescription = eventDescription;
        this.eventDate = eventDate;
        this.createdBy = createdBy;
        this.samajId = samajId;
        this.imgData = imgData;
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

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public Long getSamajId() { return samajId; }
    public void setSamajId(Long samajId) { this.samajId = samajId; }

    public byte[] getImgData() { return imgData; }
    public void setImgData(byte[] imgData) { this.imgData = imgData; }
}