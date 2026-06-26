package com.elegax.hms.patients.dto;

import lombok.*;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvestigationRequestDTO {
    private Long id;
    private Long consultationId;
    private Long patientId;
    private String requestType;
    private String testName;
    private String priority;
    private String notes;
    private String status;
    private OffsetDateTime createdAt;
}
