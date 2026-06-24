package com.elegax.hms.patients.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "patients")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, updatable = false)
    private String patientId;

    // Personal
    private String fullName;
    private LocalDate dateOfBirth;
    private String gender;
    private String bloodGroup;
    @Column(columnDefinition = "text")
    private String allergies;

    // Contact
    private String phoneNumber;
    private String emailAddress;
    @Column(columnDefinition = "text")
    private String residentialAddress;

    // Insurance
    private String insuranceProvider;
    private String policyNumber;
    private String groupId;
    private String insuranceExpirationDate; // stored as YYYY-MM or text for simplicity

    // Emergency
    private String emergencyContactName;
    private String emergencyContactRelationship;
    private String emergencyContactPhone;

    // Audit
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}

