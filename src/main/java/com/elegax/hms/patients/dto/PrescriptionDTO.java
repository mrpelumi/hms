package com.elegax.hms.patients.dto;

import lombok.*;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrescriptionDTO {
    private Long id;
    private Long consultationId;
    private Long patientId;
    private String medicationName;
    private String dosage;
    private String frequency;
    private String duration;
    private String route;
    private String instructions;
    private OffsetDateTime createdAt;
}
