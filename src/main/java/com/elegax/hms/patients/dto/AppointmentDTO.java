package com.elegax.hms.patients.dto;

import lombok.*;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppointmentDTO {
    private Long id;
    private Long patientId;
    private OffsetDateTime scheduledAt;
    private String provider;
    private String providerUsername;
    private String department;
    private String visitType;
    private String priority;
    private String reason;
    private String status;
    private OffsetDateTime checkedInAt;
}

