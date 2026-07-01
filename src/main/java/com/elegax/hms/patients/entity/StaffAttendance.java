package com.elegax.hms.patients.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

@Entity
@Table(name = "staff_attendance")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffAttendance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long staffMemberId;
    private LocalDate attendanceDate;
    private String scheduledShift;
    private LocalTime clockIn;
    private LocalTime clockOut;
    private String status;
    @Column(columnDefinition = "text")
    private String notes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
        if (status == null) {
            status = "PRESENT";
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
