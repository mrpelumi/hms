package com.elegax.hms.patients.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "consultations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Consultation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long appointmentId;
    private Long patientId;
    private OffsetDateTime consultationAt;
    private String status;
    @Column(columnDefinition = "text")
    private String symptoms;
    @Column(columnDefinition = "text")
    private String subjectiveNotes;
    @Column(columnDefinition = "text")
    private String objectiveNotes;
    @Column(columnDefinition = "text")
    private String assessment;
    @Column(columnDefinition = "text")
    private String treatmentPlan;
    private String followUpDate;
    @Column(columnDefinition = "text")
    private String doctorNotes;
    @Column(columnDefinition = "text")
    private String diagnosis;
    @Column(columnDefinition = "text")
    private String prescriptions;
}

