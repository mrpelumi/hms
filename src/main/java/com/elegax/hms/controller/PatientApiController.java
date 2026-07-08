package com.elegax.hms.controller;

import com.elegax.hms.keycloak.KeycloakService;
import com.elegax.hms.patients.dto.PatientDTO;
import com.elegax.hms.patients.entity.Patient;
import com.elegax.hms.patients.repository.PatientRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/api/patients")
public class PatientApiController {

    private final PatientRepository patientRepository;
    private final KeycloakService keycloakService;

    public PatientApiController(PatientRepository patientRepository,
                                KeycloakService keycloakService) {
        this.patientRepository = patientRepository;
        this.keycloakService = keycloakService;
    }

    @PostMapping
    public String createPatient(@ModelAttribute PatientDTO dto, RedirectAttributes redirectAttributes) {
        if (dto.getEmailAddress() == null || dto.getEmailAddress().isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Patient email is required to create portal access.");
            return "redirect:/reception/registration?portalError";
        }
        if (patientRepository.existsByEmailAddress(dto.getEmailAddress())) {
            redirectAttributes.addFlashAttribute("errorMessage", "A patient record already exists with this email address.");
            return "redirect:/reception/registration?portalError";
        }

        Patient p = Patient.builder()
                .patientId(generatePatientId())
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

        Patient savedPatient = patientRepository.save(p);

        try {
            KeycloakService.ProvisionedUser portalUser = keycloakService.provisionPatientUser(savedPatient);
            savedPatient.setKeycloakUserId(portalUser.userId());
            savedPatient.setPortalUsername(portalUser.username());
            savedPatient.setPortalAccountStatus(portalUser.existingAccount() ? "EXISTING_ACCOUNT_LINKED" : "CREATED");
        } catch (RuntimeException ex) {
            patientRepository.delete(savedPatient);
            redirectAttributes.addFlashAttribute("errorMessage", "Patient was not saved because the portal account could not be created: " + ex.getMessage());
            return "redirect:/reception/registration?portalError";
        }

        patientRepository.save(savedPatient);
        return "redirect:/reception/registration?created";
    }

    @GetMapping
    @ResponseBody
    public List<PatientDTO> allPatients() {
        return patientRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @GetMapping("/{patientId}")
    @ResponseBody
    public PatientDTO getPatient(@PathVariable String patientId) {
        return patientRepository.findByPatientId(patientId)
                .map(this::toDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));
    }

    @PostMapping("/{patientId}")
    public String updatePatient(@PathVariable String patientId, @ModelAttribute PatientDTO dto) {
        Patient patient = patientRepository.findByPatientId(patientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));

        applyDto(patient, dto);
        patientRepository.save(patient);
        return "redirect:/reception/patients/" + patient.getPatientId() + "?updated";
    }

    @GetMapping("/search")
    @ResponseBody
    public List<PatientDTO> search(@RequestParam("q") String q) {
        return patientRepository.findByFullNameContainingIgnoreCase(q)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private String generatePatientId() {
        String patientId;
        int year = LocalDate.now().getYear();
        do {
            int sequence = ThreadLocalRandom.current().nextInt(0, 10_000);
            patientId = String.format("PID-%d-%04d", year, sequence);
        } while (patientRepository.existsByPatientId(patientId));
        return patientId;
    }

    private void applyDto(Patient patient, PatientDTO dto) {
        patient.setFullName(dto.getFullName());
        patient.setDateOfBirth(dto.getDateOfBirth());
        patient.setGender(dto.getGender());
        patient.setBloodGroup(dto.getBloodGroup());
        patient.setAllergies(dto.getAllergies());
        patient.setPhoneNumber(dto.getPhoneNumber());
        patient.setEmailAddress(dto.getEmailAddress());
        patient.setResidentialAddress(dto.getResidentialAddress());
        patient.setInsuranceProvider(dto.getInsuranceProvider());
        patient.setPolicyNumber(dto.getPolicyNumber());
        patient.setGroupId(dto.getGroupId());
        patient.setInsuranceExpirationDate(dto.getInsuranceExpirationDate());
        patient.setEmergencyContactName(dto.getEmergencyContactName());
        patient.setEmergencyContactRelationship(dto.getEmergencyContactRelationship());
        patient.setEmergencyContactPhone(dto.getEmergencyContactPhone());
    }

    private PatientDTO toDto(Patient p) {
        return PatientDTO.builder()
                .id(p.getId())
                .patientId(p.getPatientId())
                .fullName(p.getFullName())
                .dateOfBirth(p.getDateOfBirth())
                .gender(p.getGender())
                .bloodGroup(p.getBloodGroup())
                .allergies(p.getAllergies())
                .phoneNumber(p.getPhoneNumber())
                .emailAddress(p.getEmailAddress())
                .residentialAddress(p.getResidentialAddress())
                .portalUsername(p.getPortalUsername())
                .keycloakUserId(p.getKeycloakUserId())
                .portalAccountStatus(p.getPortalAccountStatus())
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

