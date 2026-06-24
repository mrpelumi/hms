package com.elegax.hms.patients.repository;

import com.elegax.hms.patients.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PatientRepository extends JpaRepository<Patient, Long> {
    Optional<Patient> findByPatientId(String patientId);
    List<Patient> findByFullNameContainingIgnoreCase(String name);
    List<Patient> findByPhoneNumber(String phoneNumber);
    boolean existsByPatientId(String patientId);
    boolean existsByEmailAddress(String emailAddress);
    boolean existsByPhoneNumber(String phoneNumber);
}

