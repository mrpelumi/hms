package com.elegax.hms.patients.repository;

import com.elegax.hms.patients.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PatientRepository extends JpaRepository<Patient, Long> {
    List<Patient> findByFullNameContainingIgnoreCase(String name);
    List<Patient> findByPhoneNumber(String phoneNumber);
    boolean existsByEmailAddress(String emailAddress);
    boolean existsByPhoneNumber(String phoneNumber);
}

