package com.elegax.hms.patients.repository;

import com.elegax.hms.patients.entity.QueueEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QueueEntryRepository extends JpaRepository<QueueEntry, Long> {
    List<QueueEntry> findByStatus(String status);
    List<QueueEntry> findByPatientId(Long patientId);
}

