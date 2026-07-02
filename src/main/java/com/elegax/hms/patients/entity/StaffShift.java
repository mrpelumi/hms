package com.elegax.hms.patients.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

@Entity
@Table(name = "staff_shifts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffShift {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long staffMemberId;
    private Long departmentId;
    private LocalDate shiftDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String shiftType;
    private String status;
    private String createdBy;
    @Column(columnDefinition = "text")
    private String notes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
        if (status == null || status.isBlank()) {
            status = "ASSIGNED";
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
