package com.elegax.hms.patients.dto;

import lombok.*;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsultationDTO {
    private Long id;
    private Long appointmentId;
    private Long patientId;
    private OffsetDateTime consultationAt;
    private String doctorNotes;
    private String diagnosis;
    private String prescriptions;
}

