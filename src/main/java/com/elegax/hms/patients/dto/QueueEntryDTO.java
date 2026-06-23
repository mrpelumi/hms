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
    private String status;
    private OffsetDateTime queuedAt;
    private OffsetDateTime servedAt;
}

