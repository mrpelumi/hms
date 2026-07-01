package com.elegax.hms.patients.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "leave_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long staffMemberId;
    private String leaveType;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    @Column(columnDefinition = "text")
    private String reason;
    @Column(columnDefinition = "text")
    private String hrComment;
    private OffsetDateTime requestedAt;
    private OffsetDateTime decidedAt;

    @PrePersist
    public void prePersist() {
        requestedAt = OffsetDateTime.now();
        if (status == null) {
            status = "PENDING";
        }
    }
}
