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
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = OffsetDateTime.now();
        if (status == null) {
            status = "REQUESTED";
        }
    }
}
