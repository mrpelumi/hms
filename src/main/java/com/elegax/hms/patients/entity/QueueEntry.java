package com.elegax.hms.patients.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "queue_entries")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueueEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long appointmentId;
    private Long patientId;
    private String token;
    private String status; // WAITING_FOR_NURSE, READY_FOR_DOCTOR, CALLED, SERVED
    private String temperature;
    private String bloodPressure;
    private String pulseRate;
    private String respiratoryRate;
    private String oxygenSaturation;
    private String painScore;
    private String weight;
    private String height;
    private String bmi;
    private String chiefComplaint;
    @Column(length = 2000)
    private String nurseNotes;
    private OffsetDateTime queuedAt;
    private OffsetDateTime vitalsCapturedAt;
    private OffsetDateTime servedAt;
}

