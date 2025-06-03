package com.example.samajconnectbackend.service;
import com.example.samajconnectbackend.dto.EventDTO;
import com.example.samajconnectbackend.entity.Event;
import com.example.samajconnectbackend.repository.EventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class EventService {

    @Autowired
    private EventRepository eventRepository;

    public List<EventDTO> getEventsBySamajId(Long samajId) {
        List<Event> events = eventRepository.findBySamajIdOrderByCreatedAtDesc(samajId);
        return events.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<EventDTO> getUpcomingEventsBySamajId(Long samajId) {
        List<Event> events = eventRepository.findUpcomingEventsBySamajId(samajId);
        return events.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<EventDTO> getRecentEventsBySamajId(Long samajId) {
        List<Event> events = eventRepository.findRecentEventsBySamajId(samajId);
        return events.stream()
                .limit(5) // Limit to 5 recent events
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public EventDTO getEventById(Long id) {
        Event event = eventRepository.findById(id).orElse(null);
        return event != null ? convertToDTO(event) : null;
    }

    private EventDTO convertToDTO(Event event) {
        return new EventDTO(
                event.getId(),
                event.getEventTitle(),
                event.getEventDescription(),
                event.getEventDate(),
                event.getCreatedAt(),
                event.getCreatedBy(),
                event.getSamajId(),
                event.getImgData()
        );
    }
}