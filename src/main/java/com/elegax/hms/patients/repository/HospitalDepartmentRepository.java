package com.elegax.hms.patients.repository;

import com.elegax.hms.patients.entity.HospitalDepartment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HospitalDepartmentRepository extends JpaRepository<HospitalDepartment, Long> {
    Optional<HospitalDepartment> findByNameIgnoreCase(String name);
}
