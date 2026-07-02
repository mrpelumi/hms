package com.elegax.hms.patients.dto;

import lombok.*;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvestigationRequestDTO {
    private Long id;
    private Long consultationId;
    private Long patientId;
    private String requestType;
    private String testName;
    private String priority;
    private String notes;
    private String status;
    private String sampleType;
    private String collectionLocation;
    private String containerType;
    private String sampleCondition;
    private String collectedBy;
    private OffsetDateTime collectedAt;
    private String sampleNotes;
    private String performedBy;
    private OffsetDateTime performedAt;
    private String resultSummary;
    private String resultParameters;
    private String verifiedBy;
    private OffsetDateTime verifiedAt;
    private OffsetDateTime expectedResultAt;
    private OffsetDateTime resultReadyAt;
    private String patientUpdateNote;
    private OffsetDateTime createdAt;
}
