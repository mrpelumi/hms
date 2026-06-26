package com.elegax.hms.patients.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;
import java.time.OffsetDateTime;

@Entity
@Table(name = "doctor_schedules")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DoctorSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String doctorUsername;
    private String doctorName;
    private String dayOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;
    private boolean available;
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void markUpdatedAt() {
        updatedAt = OffsetDateTime.now();
    }
}
