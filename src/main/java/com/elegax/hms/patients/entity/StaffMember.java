package com.elegax.hms.patients.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "staff_members")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String staffId;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String department;
    private String jobTitle;
    private String staffRole;
    private String employmentType;
    private String shift;
    private LocalDate hireDate;
    private LocalDate credentialExpiryDate;
    private BigDecimal baseSalary;
    private BigDecimal allowances;
    private BigDecimal deductions;
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
            status = "ACTIVE";
        }
        if (allowances == null) {
            allowances = BigDecimal.ZERO;
        }
        if (deductions == null) {
            deductions = BigDecimal.ZERO;
        }
        if (baseSalary == null) {
            baseSalary = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
