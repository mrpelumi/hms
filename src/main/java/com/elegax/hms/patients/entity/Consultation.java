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
    @Column(columnDefinition = "text")
    private String doctorNotes;
    @Column(columnDefinition = "text")
    private String diagnosis;
    @Column(columnDefinition = "text")
    private String prescriptions;
}

