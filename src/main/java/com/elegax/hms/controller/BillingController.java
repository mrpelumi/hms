package com.elegax.hms.controller;

import com.elegax.hms.patients.entity.BillingRecord;
import com.elegax.hms.patients.entity.Patient;
import com.elegax.hms.patients.repository.BillingRecordRepository;
import com.elegax.hms.patients.repository.PatientRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/billing")
public class BillingController {

    private final BillingRecordRepository billingRecordRepository;
    private final PatientRepository patientRepository;

    public BillingController(BillingRecordRepository billingRecordRepository,
                             PatientRepository patientRepository) {
        this.billingRecordRepository = billingRecordRepository;
        this.patientRepository = patientRepository;
    }

    @GetMapping({"/home", "/dashboard"})
    public String home(Model model, HttpSession session) {
        session.setAttribute("userRole", "BILLING");
        var records = billingRecordRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        Map<Long, Patient> patientsById = patientRepository.findAllById(records.stream()
                        .map(BillingRecord::getPatientId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(Patient::getId, Function.identity()));
        BigDecimal unpaidTotal = records.stream()
                .filter(record -> !"PAID".equals(record.getStatus()))
                .map(BillingRecord::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paidTotal = records.stream()
                .filter(record -> "PAID".equals(record.getStatus()))
                .map(BillingRecord::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("records", records);
        model.addAttribute("patientsById", patientsById);
        model.addAttribute("unpaidCount", records.stream().filter(record -> !"PAID".equals(record.getStatus())).count());
        model.addAttribute("paidCount", records.stream().filter(record -> "PAID".equals(record.getStatus())).count());
        model.addAttribute("unpaidTotal", unpaidTotal);
        model.addAttribute("paidTotal", paidTotal);
        return "billing/billingHome";
    }

    @PostMapping("/records/{id}/pay")
    public String pay(@PathVariable Long id) {
        BillingRecord record = billingRecordRepository.findById(id).orElseThrow();
        record.setStatus("PAID");
        record.setPaidAt(OffsetDateTime.now());
        billingRecordRepository.save(record);
        return "redirect:/billing/home?paid";
    }
}
