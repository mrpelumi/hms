package com.elegax.hms.patients.repository;

import com.elegax.hms.patients.entity.PatientMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PatientMessageRepository extends JpaRepository<PatientMessage, Long> {
    List<PatientMessage> findByPatientIdOrderByCreatedAtDesc(Long patientId);
}
