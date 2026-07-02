package com.elegax.hms.patients.service;

import com.elegax.hms.patients.entity.BillingRecord;
import com.elegax.hms.patients.entity.InvestigationRequest;
import com.elegax.hms.patients.entity.Prescription;
import com.elegax.hms.patients.repository.BillingRecordRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class BillingWorkflowService {

    private final BillingRecordRepository billingRecordRepository;

    public BillingWorkflowService(BillingRecordRepository billingRecordRepository) {
        this.billingRecordRepository = billingRecordRepository;
    }

    public void createForConsultation(Long patientId, Long consultationId) {
        if (patientId == null || consultationId == null) {
            return;
        }
        billingRecordRepository.findBySourceTypeAndSourceId("CONSULTATION", consultationId)
                .orElseGet(() -> billingRecordRepository.save(BillingRecord.builder()
                        .patientId(patientId)
                        .sourceId(consultationId)
                        .sourceType("CONSULTATION")
                        .description("OPD Consultation")
                        .quantity(1)
                        .unitPrice(new BigDecimal("5000.00"))
                        .build()));
    }

    public void createForAppointmentCheckIn(Long patientId, Long appointmentId) {
        if (patientId == null || appointmentId == null) {
            return;
        }
        billingRecordRepository.findBySourceTypeAndSourceId("APPOINTMENT_CHECK_IN", appointmentId)
                .orElseGet(() -> billingRecordRepository.save(BillingRecord.builder()
                        .patientId(patientId)
                        .sourceId(appointmentId)
                        .sourceType("APPOINTMENT_CHECK_IN")
                        .description("Hospital visit registration")
                        .quantity(1)
                        .unitPrice(new BigDecimal("1500.00"))
                        .build()));
    }

    public void createForInvestigation(InvestigationRequest request) {
        if (request == null || request.getId() == null || request.getPatientId() == null) {
            return;
        }
        String sourceType = isRadiology(request) ? "RADIOLOGY" : "LABORATORY";
        billingRecordRepository.findBySourceTypeAndSourceId(sourceType, request.getId())
                .orElseGet(() -> billingRecordRepository.save(BillingRecord.builder()
                        .patientId(request.getPatientId())
                        .sourceId(request.getId())
                        .sourceType(sourceType)
                        .description(request.getTestName())
                        .quantity(1)
                        .unitPrice(isRadiology(request) ? new BigDecimal("12000.00") : new BigDecimal("3500.00"))
                        .build()));
    }

    public void createForPrescription(Prescription prescription) {
        if (prescription == null || prescription.getId() == null || prescription.getPatientId() == null) {
            return;
        }
        billingRecordRepository.findBySourceTypeAndSourceId("PHARMACY", prescription.getId())
                .orElseGet(() -> billingRecordRepository.save(BillingRecord.builder()
                        .patientId(prescription.getPatientId())
                        .sourceId(prescription.getId())
                        .sourceType("PHARMACY")
                        .description(prescription.getMedicationName())
                        .quantity(1)
                        .unitPrice(new BigDecimal("2500.00"))
                        .build()));
    }

    private boolean isRadiology(InvestigationRequest request) {
        String type = request.getRequestType() == null ? "" : request.getRequestType();
        return type.equalsIgnoreCase("Radiology") || type.equalsIgnoreCase("Imaging");
    }
}
