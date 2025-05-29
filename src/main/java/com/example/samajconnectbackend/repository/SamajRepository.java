package com.example.samajconnectbackend.repository;

import com.example.samajconnectbackend.entity.Samaj;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SamajRepository extends JpaRepository<Samaj, Long> {

    Optional<Samaj> findByName(String name);

    boolean existsByName(String name);

    @Query("SELECT s FROM Samaj s ORDER BY s.name ASC")
    List<Samaj> findAllOrderByName();

    @Query("SELECT COUNT(u) FROM User u WHERE u.samaj.id = :samajId")
    int countMembersBySamajId(Long samajId);
}