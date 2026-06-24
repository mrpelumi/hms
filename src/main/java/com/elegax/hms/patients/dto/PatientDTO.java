package com.elegax.hms.patients.dto;

import lombok.*;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientDTO {
    private Long id;
    private String patientId;
    private String fullName;
    private LocalDate dateOfBirth;
    private String gender;
    private String bloodGroup;
    private String allergies;

    private String phoneNumber;
    private String emailAddress;
    private String residentialAddress;

    private String insuranceProvider;
    private String policyNumber;
    private String groupId;
    private String insuranceExpirationDate;

    private String emergencyContactName;
    private String emergencyContactRelationship;
    private String emergencyContactPhone;
}

