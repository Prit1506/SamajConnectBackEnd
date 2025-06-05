package com.example.samajconnectbackend.service;

import com.example.samajconnectbackend.dto.EventDTO;
import com.example.samajconnectbackend.entity.Event;
import com.example.samajconnectbackend.repository.EventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class EventService {

    private static final Logger logger = LoggerFactory.getLogger(EventService.class);

    @Autowired
    private EventRepository eventRepository;

    /**
     * Create a new event
     */
    @Transactional
    public EventDTO createEvent(EventDTO eventDTO) {
        try {
            // Validate required fields
            if (eventDTO.getEventTitle() == null || eventDTO.getEventTitle().trim().isEmpty()) {
                throw new IllegalArgumentException("Event title is required");
            }
            if (eventDTO.getEventDate() == null) {
                throw new IllegalArgumentException("Event date is required");
            }
            if (eventDTO.getSamajId() == null) {
                throw new IllegalArgumentException("Samaj ID is required");
            }
            if (eventDTO.getCreatedBy() == null) {
                throw new IllegalArgumentException("Created by user ID is required");
            }

            // Check if event date is in the future
            if (eventDTO.getEventDate().isBefore(LocalDateTime.now())) {
                throw new IllegalArgumentException("Event date must be in the future");
            }

            // Convert DTO to Entity
            Event event = new Event();
            event.setEventTitle(eventDTO.getEventTitle().trim());
            event.setEventDescription(eventDTO.getEventDescription());
            event.setLocation(eventDTO.getLocation());
            event.setEventDate(eventDTO.getEventDate());
            event.setCreatedBy(eventDTO.getCreatedBy());
            event.setSamajId(eventDTO.getSamajId());

            // Handle image data
            if (eventDTO.getImageBase64() != null && !eventDTO.getImageBase64().isEmpty()) {
                event.setImgData(eventDTO.getImageBytes());
            }

            // Save event
            Event savedEvent = eventRepository.save(event);

            logger.info("Event created successfully with ID: {} for Samaj ID: {}",
                    savedEvent.getId(), savedEvent.getSamajId());

            // Convert back to DTO and return
            return convertToDTO(savedEvent);

        } catch (IllegalArgumentException e) {
            logger.error("Validation error creating event: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Error creating event: {}", e.getMessage());
            throw new RuntimeException("Failed to create event: " + e.getMessage());
        }
    }

    /**
     * Get all events by samaj ID
     */
    public List<EventDTO> getEventsBySamajId(Long samajId) {
        try {
            List<Event> events = eventRepository.findBySamajIdOrderByEventDateDesc(samajId);
            return events.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("Error retrieving events for Samaj ID {}: {}", samajId, e.getMessage());
            throw new RuntimeException("Failed to retrieve events");
        }
    }

    /**
     * Get upcoming events by samaj ID
     */
    public List<EventDTO> getUpcomingEventsBySamajId(Long samajId) {
        try {
            List<Event> events = eventRepository.findUpcomingEventsBySamajId(samajId);
            return events.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("Error retrieving upcoming events for Samaj ID {}: {}", samajId, e.getMessage());
            throw new RuntimeException("Failed to retrieve upcoming events");
        }
    }

    /**
     * Get recent events by samaj ID
     */
    public List<EventDTO> getRecentEventsBySamajId(Long samajId) {
        try {
            List<Event> events = eventRepository.findRecentEventsBySamajId(samajId);
            return events.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("Error retrieving recent events for Samaj ID {}: {}", samajId, e.getMessage());
            throw new RuntimeException("Failed to retrieve recent events");
        }
    }

    /**
     * Get event by ID
     */
    public EventDTO getEventById(Long id) {
        try {
            Optional<Event> eventOptional = eventRepository.findById(id);
            return eventOptional.map(this::convertToDTO).orElse(null);
        } catch (Exception e) {
            logger.error("Error retrieving event with ID {}: {}", id, e.getMessage());
            throw new RuntimeException("Failed to retrieve event");
        }
    }

    /**
     * Update an existing event
     */
    @Transactional
    public EventDTO updateEvent(Long id, EventDTO eventDTO) {
        try {
            Optional<Event> eventOptional = eventRepository.findById(id);

            if (eventOptional.isEmpty()) {
                throw new IllegalArgumentException("Event not found with ID: " + id);
            }

            Event existingEvent = eventOptional.get();

            // Update fields if provided
            if (eventDTO.getEventTitle() != null && !eventDTO.getEventTitle().trim().isEmpty()) {
                existingEvent.setEventTitle(eventDTO.getEventTitle().trim());
            }
            if (eventDTO.getEventDescription() != null) {
                existingEvent.setEventDescription(eventDTO.getEventDescription());
            }
            if (eventDTO.getLocation() != null) {
                existingEvent.setLocation(eventDTO.getLocation());
            }
            if (eventDTO.getEventDate() != null) {
                if (eventDTO.getEventDate().isBefore(LocalDateTime.now())) {
                    throw new IllegalArgumentException("Event date must be in the future");
                }
                existingEvent.setEventDate(eventDTO.getEventDate());
            }

            // Handle image update
            if (eventDTO.getImageBase64() != null) {
                if (eventDTO.getImageBase64().isEmpty()) {
                    existingEvent.setImgData(null); // Remove image
                } else {
                    existingEvent.setImgData(eventDTO.getImageBytes()); // Update image
                }
            }

            Event updatedEvent = eventRepository.save(existingEvent);

            logger.info("Event updated successfully with ID: {}", updatedEvent.getId());

            return convertToDTO(updatedEvent);

        } catch (IllegalArgumentException e) {
            logger.error("Validation error updating event with ID {}: {}", id, e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Error updating event with ID {}: {}", id, e.getMessage());
            throw new RuntimeException("Failed to update event: " + e.getMessage());
        }
    }

    /**
     * Delete an event
     */
    @Transactional
    public boolean deleteEvent(Long id) {
        try {
            if (!eventRepository.existsById(id)) {
                throw new IllegalArgumentException("Event not found with ID: " + id);
            }

            eventRepository.deleteById(id);
            logger.info("Event deleted successfully with ID: {}", id);
            return true;

        } catch (IllegalArgumentException e) {
            logger.error("Event not found for deletion with ID {}: {}", id, e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Error deleting event with ID {}: {}", id, e.getMessage());
            throw new RuntimeException("Failed to delete event: " + e.getMessage());
        }
    }

    /**
     * Convert Event entity to EventDTO
     */
    private EventDTO convertToDTO(Event event) {
        return new EventDTO(
                event.getId(),
                event.getEventTitle(),
                event.getEventDescription(),
                event.getLocation(),
                event.getEventDate(),
                event.getCreatedAt(),
                event.getCreatedBy(),
                event.getSamajId(),
                event.getImgData()
        );
    }
}
