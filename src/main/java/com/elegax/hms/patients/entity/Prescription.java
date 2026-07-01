package com.elegax.hms.patients.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "prescriptions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Prescription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long consultationId;
    private Long patientId;
    private String medicationName;
    private String dosage;
    private String frequency;
    private String duration;
    private String route;
    @Column(columnDefinition = "text")
    private String instructions;
    private String status;
    private OffsetDateTime dispensedAt;
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = OffsetDateTime.now();
        if (status == null) {
            status = "PENDING";
        }
    }
}
