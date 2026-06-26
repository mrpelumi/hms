package com.elegax.hms.patients.dto;

import lombok.*;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueueEntryDTO {
    private Long id;
    private Long appointmentId;
    private Long patientId;
    private String token;
    private String status;
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
    private String nurseNotes;
    private OffsetDateTime queuedAt;
    private OffsetDateTime vitalsCapturedAt;
    private OffsetDateTime servedAt;
}

