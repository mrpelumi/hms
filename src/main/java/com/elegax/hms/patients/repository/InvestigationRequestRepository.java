package com.elegax.hms.patients.repository;

import com.elegax.hms.patients.entity.InvestigationRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvestigationRequestRepository extends JpaRepository<InvestigationRequest, Long> {
    List<InvestigationRequest> findByPatientId(Long patientId);
    List<InvestigationRequest> findByConsultationId(Long consultationId);
}
