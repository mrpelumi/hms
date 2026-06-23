package com.elegax.hms.controller;

import com.elegax.hms.patients.dto.PatientDTO;
import com.elegax.hms.patients.entity.Patient;
import com.elegax.hms.patients.repository.PatientRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/api/patients")
public class PatientApiController {

    private final PatientRepository patientRepository;

    public PatientApiController(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    @PostMapping
    public String createPatient(@ModelAttribute PatientDTO dto) {
        Patient p = Patient.builder()
                .fullName(dto.getFullName())
                .dateOfBirth(dto.getDateOfBirth())
                .gender(dto.getGender())
                .bloodGroup(dto.getBloodGroup())
                .allergies(dto.getAllergies())
                .phoneNumber(dto.getPhoneNumber())
                .emailAddress(dto.getEmailAddress())
                .residentialAddress(dto.getResidentialAddress())
                .insuranceProvider(dto.getInsuranceProvider())
                .policyNumber(dto.getPolicyNumber())
                .groupId(dto.getGroupId())
                .insuranceExpirationDate(dto.getInsuranceExpirationDate())
                .emergencyContactName(dto.getEmergencyContactName())
                .emergencyContactRelationship(dto.getEmergencyContactRelationship())
                .emergencyContactPhone(dto.getEmergencyContactPhone())
                .build();

        patientRepository.save(p);
        return "redirect:/patients";
    }

    @GetMapping("/search")
    public List<PatientDTO> search(@RequestParam("q") String q) {
        return patientRepository.findByFullNameContainingIgnoreCase(q)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private PatientDTO toDto(Patient p) {
        return PatientDTO.builder()
                .id(p.getId())
                .fullName(p.getFullName())
                .dateOfBirth(p.getDateOfBirth())
                .gender(p.getGender())
                .bloodGroup(p.getBloodGroup())
                .allergies(p.getAllergies())
                .phoneNumber(p.getPhoneNumber())
                .emailAddress(p.getEmailAddress())
                .residentialAddress(p.getResidentialAddress())
                .insuranceProvider(p.getInsuranceProvider())
                .policyNumber(p.getPolicyNumber())
                .groupId(p.getGroupId())
                .insuranceExpirationDate(p.getInsuranceExpirationDate())
                .emergencyContactName(p.getEmergencyContactName())
                .emergencyContactRelationship(p.getEmergencyContactRelationship())
                .emergencyContactPhone(p.getEmergencyContactPhone())
                .build();
    }
}

