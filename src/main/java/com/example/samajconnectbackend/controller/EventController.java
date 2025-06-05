package com.example.samajconnectbackend.controller;
import com.example.samajconnectbackend.dto.EventDTO;
import com.example.samajconnectbackend.service.EventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/events")
@CrossOrigin(origins = "*")
public class EventController {

    @Autowired
    private EventService eventService;

    /**
     * Create a new event
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createEvent(@RequestBody EventDTO eventDTO) {
        Map<String, Object> response = new HashMap<>();

        try {
            EventDTO createdEvent = eventService.createEvent(eventDTO);

            response.put("success", true);
            response.put("message", "Event created successfully");
            response.put("event", createdEvent);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            response.put("event", null);

            return ResponseEntity.badRequest().body(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error creating event: " + e.getMessage());
            response.put("event", null);

            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Update an existing event
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateEvent(@PathVariable Long id, @RequestBody EventDTO eventDTO) {
        Map<String, Object> response = new HashMap<>();

        try {
            EventDTO updatedEvent = eventService.updateEvent(id, eventDTO);

            response.put("success", true);
            response.put("message", "Event updated successfully");
            response.put("event", updatedEvent);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            response.put("event", null);

            return ResponseEntity.badRequest().body(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error updating event: " + e.getMessage());
            response.put("event", null);

            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Delete an event
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteEvent(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();

        try {
            boolean deleted = eventService.deleteEvent(id);

            if (deleted) {
                response.put("success", true);
                response.put("message", "Event deleted successfully");
            } else {
                response.put("success", false);
                response.put("message", "Failed to delete event");
            }

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.badRequest().body(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error deleting event: " + e.getMessage());

            return ResponseEntity.status(500).body(response);
        }
    }

    // Existing GET endpoints (keeping all your original methods)

    @GetMapping("/samaj/{samajId}")
    public ResponseEntity<Map<String, Object>> getEventsBySamajId(@PathVariable Long samajId) {
        Map<String, Object> response = new HashMap<>();

        try {
            List<EventDTO> events = eventService.getEventsBySamajId(samajId);

            response.put("success", true);
            response.put("message", "Events retrieved successfully");
            response.put("events", events);
            response.put("count", events.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error retrieving events: " + e.getMessage());
            response.put("events", null);

            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/samaj/{samajId}/upcoming")
    public ResponseEntity<Map<String, Object>> getUpcomingEventsBySamajId(@PathVariable Long samajId) {
        Map<String, Object> response = new HashMap<>();

        try {
            List<EventDTO> events = eventService.getUpcomingEventsBySamajId(samajId);

            response.put("success", true);
            response.put("message", "Upcoming events retrieved successfully");
            response.put("events", events);
            response.put("count", events.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error retrieving upcoming events: " + e.getMessage());
            response.put("events", null);

            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/samaj/{samajId}/recent")
    public ResponseEntity<Map<String, Object>> getRecentEventsBySamajId(@PathVariable Long samajId) {
        Map<String, Object> response = new HashMap<>();

        try {
            List<EventDTO> events = eventService.getRecentEventsBySamajId(samajId);

            response.put("success", true);
            response.put("message", "Recent events retrieved successfully");
            response.put("events", events);
            response.put("count", events.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error retrieving recent events: " + e.getMessage());
            response.put("events", null);

            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getEventById(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();

        try {
            EventDTO event = eventService.getEventById(id);

            if (event != null) {
                response.put("success", true);
                response.put("message", "Event retrieved successfully");
                response.put("event", event);
            } else {
                response.put("success", false);
                response.put("message", "Event not found");
                response.put("event", null);
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error retrieving event: " + e.getMessage());
            response.put("event", null);

            return ResponseEntity.status(500).body(response);
        }
    }
}