package com.elegax.hms.patients.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "investigation_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvestigationRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long consultationId;
    private Long patientId;
    private String requestType;
    private String testName;
    private String priority;
    @Column(columnDefinition = "text")
    private String notes;
    private String status;
    private OffsetDateTime expectedResultAt;
    private OffsetDateTime resultReadyAt;

    private String sampleType;
    private String collectionLocation;
    private String containerType;
    private String sampleCondition;
    private String collectedBy;
    private OffsetDateTime collectedAt;
    @Column(columnDefinition = "text")
    private String sampleNotes;

    private String performedBy;
    private OffsetDateTime performedAt;
    @Column(columnDefinition = "text")
    private String resultSummary;
    @Column(columnDefinition = "text")
    private String resultParameters;
    private String verifiedBy;
    private OffsetDateTime verifiedAt;
    @Column(columnDefinition = "text")
    private String patientUpdateNote;
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = OffsetDateTime.now();
        if (status == null) {
            status = "REQUESTED";
        }
    }
}
