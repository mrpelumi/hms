package com.elegax.hms.controller;

import com.elegax.hms.patients.entity.Patient;
import com.elegax.hms.patients.entity.Prescription;
import com.elegax.hms.patients.repository.PatientRepository;
import com.elegax.hms.patients.repository.PrescriptionRepository;
import com.elegax.hms.patients.service.BillingWorkflowService;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/pharmacy")
public class PharmacyController {

    private final PrescriptionRepository prescriptionRepository;
    private final PatientRepository patientRepository;
    private final BillingWorkflowService billingWorkflowService;

    public PharmacyController(PrescriptionRepository prescriptionRepository,
                              PatientRepository patientRepository,
                              BillingWorkflowService billingWorkflowService) {
        this.prescriptionRepository = prescriptionRepository;
        this.patientRepository = patientRepository;
        this.billingWorkflowService = billingWorkflowService;
    }

    @GetMapping({"/dispense", "/dashboard", "/home"})
    public String dispense(Model model, HttpSession session) {
        session.setAttribute("userRole", "PHARMACY");
        var prescriptions = prescriptionRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        Map<Long, Patient> patientsById = patientRepository.findAllById(prescriptions.stream()
                        .map(Prescription::getPatientId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(Patient::getId, Function.identity()));
        model.addAttribute("prescriptions", prescriptions);
        model.addAttribute("patientsById", patientsById);
        model.addAttribute("pendingCount", prescriptions.stream().filter(prescription -> !"DISPENSED".equals(prescription.getStatus())).count());
        model.addAttribute("dispensedCount", prescriptions.stream().filter(prescription -> "DISPENSED".equals(prescription.getStatus())).count());
        model.addAttribute("totalCount", prescriptions.size());
        return "pharmacy/dispense";
    }

    @PostMapping("/prescriptions/{id}/dispense")
    public String dispensePrescription(@PathVariable Long id) {
        Prescription prescription = prescriptionRepository.findById(id).orElseThrow();
        prescription.setStatus("DISPENSED");
        prescription.setDispensedAt(OffsetDateTime.now());
        prescriptionRepository.save(prescription);
        billingWorkflowService.createForPrescription(prescription);
        return "redirect:/pharmacy/dispense?dispensed";
    }
}
