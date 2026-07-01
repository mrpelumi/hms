package com.elegax.hms.patients.repository;

import com.elegax.hms.patients.entity.BillingRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BillingRecordRepository extends JpaRepository<BillingRecord, Long> {
    List<BillingRecord> findByPatientId(Long patientId);
    List<BillingRecord> findByStatus(String status);
    Optional<BillingRecord> findBySourceTypeAndSourceId(String sourceType, Long sourceId);
}
