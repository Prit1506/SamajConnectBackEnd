package com.example.samajconnectbackend.repository;
import com.example.samajconnectbackend.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findBySamajIdOrderByEventDateDesc(Long samajId);

    List<Event> findBySamajIdOrderByCreatedAtDesc(Long samajId);

    @Query("SELECT e FROM Event e WHERE e.samajId = :samajId AND e.eventDate >= CURRENT_TIMESTAMP ORDER BY e.eventDate ASC LIMIT 3")
    List<Event> findUpcomingEventsBySamajId(@Param("samajId") Long samajId);

    @Query("SELECT e FROM Event e WHERE e.samajId = :samajId ORDER BY e.createdAt DESC")
    List<Event> findRecentEventsBySamajId(@Param("samajId") Long samajId);
}