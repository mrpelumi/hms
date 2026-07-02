package com.elegax.hms.patients.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "patient_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long patientId;
    private String subject;
    @Column(columnDefinition = "text")
    private String message;
    private String status;
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = OffsetDateTime.now();
        if (status == null || status.isBlank()) {
            status = "SENT";
        }
    }
}
