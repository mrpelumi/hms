package com.elegax.hms.controller;

import com.elegax.hms.authentication.AuthenticationManager;
import com.elegax.hms.patients.entity.*;
import com.elegax.hms.patients.repository.BillingRecordRepository;
import com.elegax.hms.patients.repository.PatientRepository;
import com.elegax.hms.patients.repository.PharmacyDispenseRepository;
import com.elegax.hms.patients.repository.PharmacyInventoryItemRepository;
import com.elegax.hms.patients.repository.PrescriptionRepository;
import com.elegax.hms.patients.service.BillingWorkflowService;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/pharmacy")
public class PharmacyController {

    private final PrescriptionRepository prescriptionRepository;
    private final PatientRepository patientRepository;
    private final PharmacyInventoryItemRepository inventoryItemRepository;
    private final PharmacyDispenseRepository pharmacyDispenseRepository;
    private final BillingRecordRepository billingRecordRepository;
    private final BillingWorkflowService billingWorkflowService;
    private final AuthenticationManager authenticationManager;

    public PharmacyController(PrescriptionRepository prescriptionRepository,
                              PatientRepository patientRepository,
                              PharmacyInventoryItemRepository inventoryItemRepository,
                              PharmacyDispenseRepository pharmacyDispenseRepository,
                              BillingRecordRepository billingRecordRepository,
                              BillingWorkflowService billingWorkflowService,
                              AuthenticationManager authenticationManager) {
        this.prescriptionRepository = prescriptionRepository;
        this.patientRepository = patientRepository;
        this.inventoryItemRepository = inventoryItemRepository;
        this.pharmacyDispenseRepository = pharmacyDispenseRepository;
        this.billingRecordRepository = billingRecordRepository;
        this.billingWorkflowService = billingWorkflowService;
        this.authenticationManager = authenticationManager;
    }

    @GetMapping({"/dispense", "/dashboard", "/home"})
    public String dispense(Model model, HttpSession session) {
        session.setAttribute("userRole", "PHARMACY");
        var prescriptions = prescriptionRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        List<PharmacyInventoryItem> inventoryItems = inventoryItemRepository.findAll(Sort.by(Sort.Direction.ASC, "medicationName"));
        Map<Long, Patient> patientsById = patientRepository.findAllById(prescriptions.stream()
                        .map(Prescription::getPatientId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(Patient::getId, Function.identity()));
        model.addAttribute("prescriptions", prescriptions);
        model.addAttribute("patientsById", patientsById);
        model.addAttribute("activeInventoryItems", inventoryItems.stream()
                .filter(item -> "ACTIVE".equalsIgnoreCase(valueOr(item.getStatus(), "ACTIVE")))
                .filter(item -> valueOr(item.getQuantityOnHand(), 0) > 0)
                .toList());
        model.addAttribute("pendingCount", prescriptions.stream().filter(prescription -> !"DISPENSED".equals(prescription.getStatus())).count());
        model.addAttribute("dispensedCount", prescriptions.stream().filter(prescription -> "DISPENSED".equals(prescription.getStatus())).count());
        model.addAttribute("totalCount", prescriptions.size());
        model.addAttribute("availableStockCount", inventoryItems.stream().filter(item -> valueOr(item.getQuantityOnHand(), 0) > 0).count());
        return "pharmacy/dispense";
    }

    @GetMapping("/inventory")
    public String inventory(Model model, HttpSession session) {
        session.setAttribute("userRole", "PHARMACY");
        List<PharmacyInventoryItem> inventoryItems = inventoryItemRepository.findAll(Sort.by(Sort.Direction.ASC, "medicationName"));
        model.addAttribute("inventoryItems", inventoryItems);
        model.addAttribute("activeCount", inventoryItems.stream().filter(item -> "ACTIVE".equalsIgnoreCase(valueOr(item.getStatus(), "ACTIVE"))).count());
        model.addAttribute("lowStockCount", inventoryItems.stream()
                .filter(item -> valueOr(item.getQuantityOnHand(), 0) <= valueOr(item.getReorderLevel(), 0))
                .count());
        model.addAttribute("expiredCount", inventoryItems.stream()
                .filter(item -> item.getExpiryDate() != null && item.getExpiryDate().isBefore(LocalDate.now()))
                .count());
        model.addAttribute("inventoryValue", inventoryItems.stream()
                .map(item -> valueOr(item.getSellingPrice(), BigDecimal.ZERO).multiply(BigDecimal.valueOf(valueOr(item.getQuantityOnHand(), 0))))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        return "pharmacy/inventory";
    }

    @GetMapping("/billing")
    public String billing(Model model, HttpSession session) {
        session.setAttribute("userRole", "PHARMACY");
        List<PharmacyDispense> dispenses = pharmacyDispenseRepository.findAll(Sort.by(Sort.Direction.DESC, "dispensedAt"));
        List<BillingRecord> pharmacyBills = billingRecordRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .filter(record -> "PHARMACY".equalsIgnoreCase(valueOr(record.getSourceType(), "")))
                .toList();
        Map<Long, Patient> patientsById = patientRepository.findAllById(pharmacyBills.stream()
                        .map(BillingRecord::getPatientId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(Patient::getId, Function.identity()));
        model.addAttribute("dispenses", dispenses);
        model.addAttribute("pharmacyBills", pharmacyBills);
        model.addAttribute("patientsById", patientsById);
        model.addAttribute("totalPharmacyRevenue", pharmacyBills.stream()
                .filter(record -> "PAID".equalsIgnoreCase(valueOr(record.getStatus(), "")))
                .map(BillingRecord::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("pendingPharmacyAmount", pharmacyBills.stream()
                .filter(record -> !"PAID".equalsIgnoreCase(valueOr(record.getStatus(), "")))
                .map(BillingRecord::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("submittedCount", pharmacyBills.stream()
                .filter(record -> valueOr(record.getStatus(), "").contains("SUBMITTED"))
                .count());
        return "pharmacy/billing";
    }

    @PostMapping("/prescriptions/{id}/dispense")
    public String dispensePrescription(@PathVariable Long id,
                                       @RequestParam Long inventoryItemId,
                                       @RequestParam Integer quantityDispensed,
                                       @RequestParam(required = false) String pharmacistNote) {
        Prescription prescription = prescriptionRepository.findById(id).orElseThrow();
        PharmacyInventoryItem item = inventoryItemRepository.findById(inventoryItemId).orElseThrow();
        int requestedQuantity = quantityDispensed == null || quantityDispensed < 1 ? 1 : quantityDispensed;
        int currentStock = valueOr(item.getQuantityOnHand(), 0);
        if (!"ACTIVE".equalsIgnoreCase(valueOr(item.getStatus(), "ACTIVE"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected inventory item is not active");
        }
        if (currentStock < requestedQuantity) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient stock for selected medication");
        }
        item.setQuantityOnHand(currentStock - requestedQuantity);
        inventoryItemRepository.save(item);

        BigDecimal unitPrice = valueOr(item.getSellingPrice(), BigDecimal.ZERO);
        PharmacyDispense dispense = pharmacyDispenseRepository.save(PharmacyDispense.builder()
                .prescriptionId(prescription.getId())
                .patientId(prescription.getPatientId())
                .inventoryItemId(item.getId())
                .medicationName(item.getMedicationName())
                .batchNumber(item.getBatchNumber())
                .quantityDispensed(requestedQuantity)
                .unitPrice(unitPrice)
                .totalAmount(unitPrice.multiply(BigDecimal.valueOf(requestedQuantity)))
                .dispensedBy(authenticationManager.getDisplayName())
                .pharmacistNote(pharmacistNote)
                .build());
        prescription.setStatus("DISPENSED");
        prescription.setDispensedAt(OffsetDateTime.now());
        prescriptionRepository.save(prescription);
        billingWorkflowService.createForPharmacyDispense(dispense);
        return "redirect:/pharmacy/dispense?dispensed";
    }

    @PostMapping("/inventory")
    public String addInventoryItem(@RequestParam String medicationName,
                                   @RequestParam(required = false) String strength,
                                   @RequestParam(required = false) String dosageForm,
                                   @RequestParam(required = false) String batchNumber,
                                   @RequestParam(required = false) String expiryDate,
                                   @RequestParam(required = false) Integer quantityOnHand,
                                   @RequestParam(required = false) Integer reorderLevel,
                                   @RequestParam(required = false) BigDecimal unitCost,
                                   @RequestParam(required = false) BigDecimal sellingPrice) {
        inventoryItemRepository.save(PharmacyInventoryItem.builder()
                .medicationName(medicationName)
                .strength(strength)
                .dosageForm(dosageForm)
                .batchNumber(batchNumber)
                .expiryDate(expiryDate == null || expiryDate.isBlank() ? null : LocalDate.parse(expiryDate))
                .quantityOnHand(quantityOnHand)
                .reorderLevel(reorderLevel)
                .unitCost(unitCost)
                .sellingPrice(sellingPrice)
                .status("ACTIVE")
                .build());
        return "redirect:/pharmacy/inventory?created";
    }

    @PostMapping("/inventory/{id}")
    public String updateInventoryItem(@PathVariable Long id,
                                      @RequestParam String medicationName,
                                      @RequestParam(required = false) String strength,
                                      @RequestParam(required = false) String dosageForm,
                                      @RequestParam(required = false) String batchNumber,
                                      @RequestParam(required = false) String expiryDate,
                                      @RequestParam(required = false) Integer quantityOnHand,
                                      @RequestParam(required = false) Integer reorderLevel,
                                      @RequestParam(required = false) BigDecimal unitCost,
                                      @RequestParam(required = false) BigDecimal sellingPrice,
                                      @RequestParam(required = false, defaultValue = "ACTIVE") String status) {
        PharmacyInventoryItem item = inventoryItemRepository.findById(id).orElseThrow();
        item.setMedicationName(medicationName);
        item.setStrength(strength);
        item.setDosageForm(dosageForm);
        item.setBatchNumber(batchNumber);
        item.setExpiryDate(expiryDate == null || expiryDate.isBlank() ? null : LocalDate.parse(expiryDate));
        item.setQuantityOnHand(quantityOnHand);
        item.setReorderLevel(reorderLevel);
        item.setUnitCost(unitCost);
        item.setSellingPrice(sellingPrice);
        item.setStatus(status);
        inventoryItemRepository.save(item);
        return "redirect:/pharmacy/inventory?updated";
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private Integer valueOr(Integer value, Integer fallback) {
        return value == null ? fallback : value;
    }

    private BigDecimal valueOr(BigDecimal value, BigDecimal fallback) {
        return value == null ? fallback : value;
    }
}
