package com.elegax.hms.controller;

import com.elegax.hms.authentication.AuthenticationManager;
import com.elegax.hms.patients.entity.BillingRecord;
import com.elegax.hms.patients.entity.Patient;
import com.elegax.hms.patients.repository.BillingRecordRepository;
import com.elegax.hms.patients.repository.PatientRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/billing")
public class BillingController {

    private final BillingRecordRepository billingRecordRepository;
    private final PatientRepository patientRepository;
    private final AuthenticationManager authenticationManager;

    public BillingController(BillingRecordRepository billingRecordRepository,
                             PatientRepository patientRepository,
                             AuthenticationManager authenticationManager) {
        this.billingRecordRepository = billingRecordRepository;
        this.patientRepository = patientRepository;
        this.authenticationManager = authenticationManager;
    }

    @GetMapping({"/home", "/dashboard"})
    public String home(Model model, HttpSession session) {
        session.setAttribute("userRole", "BILLING");
        addBillingModel(model, records());
        return "billing/billingHome";
    }

    @PostMapping("/records/{id}/pay")
    public String pay(@PathVariable Long id,
                      @RequestParam(required = false, defaultValue = "Cash") String paymentMethod,
                      @RequestParam(required = false) BigDecimal amountPaid,
                      @RequestParam(required = false) String paymentReference,
                      @RequestParam(required = false) String paidBy,
                      @RequestParam(required = false) String confirmationNote,
                      @RequestParam(required = false) MultipartFile receiptProof) {
        BillingRecord record = billingRecord(id);
        BigDecimal confirmedAmount = amountPaid == null ? valueOr(record.getTotalAmount(), BigDecimal.ZERO) : amountPaid;
        BigDecimal totalAmount = valueOr(record.getTotalAmount(), BigDecimal.ZERO);
        record.setStatus(confirmedAmount.compareTo(totalAmount) >= 0 ? "PAID" : "PARTIALLY_PAID");
        record.setPaidAt(OffsetDateTime.now());
        record.setPaymentMethod(paymentMethod);
        record.setAmountPaid(confirmedAmount);
        record.setPaymentReference(paymentReference);
        record.setPaidBy(paidBy);
        record.setConfirmationNote(confirmationNote);
        record.setReceiptProofPath(storePaymentProof(record, receiptProof));
        record.setReceivedBy(authenticationManager.getDisplayName());
        billingRecordRepository.save(record);
        return "redirect:/billing/payments/" + id + "?paid";
    }

    @GetMapping("/invoices")
    public String invoices(Model model, HttpSession session) {
        session.setAttribute("userRole", "BILLING");
        addBillingModel(model, records());
        return "billing/invoices";
    }

    @GetMapping("/invoices/{id}")
    public String invoice(@PathVariable Long id, Model model, HttpSession session) {
        session.setAttribute("userRole", "BILLING");
        BillingRecord record = billingRecord(id);
        model.addAttribute("record", record);
        model.addAttribute("patient", patient(record.getPatientId()));
        model.addAttribute("dueDate", record.getCreatedAt() == null ? null : record.getCreatedAt().plusDays(7));
        return "billing/invoiceDetail";
    }

    @GetMapping("/invoices/{id}/download")
    public ResponseEntity<ByteArrayResource> invoiceDownload(@PathVariable Long id) {
        BillingRecord record = billingRecord(id);
        Patient patient = patient(record.getPatientId());
        byte[] invoice = buildInvoicePdf(record, patient);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=invoice-" + record.getId() + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(new ByteArrayResource(invoice));
    }

    @GetMapping("/payments")
    public String payments(Model model, HttpSession session) {
        session.setAttribute("userRole", "BILLING");
        addBillingModel(model, records());
        return "billing/payments";
    }

    @GetMapping("/payments/{id}")
    public String payment(@PathVariable Long id, Model model, HttpSession session) {
        session.setAttribute("userRole", "BILLING");
        BillingRecord record = billingRecord(id);
        model.addAttribute("record", record);
        model.addAttribute("patient", patient(record.getPatientId()));
        model.addAttribute("currentOfficer", authenticationManager.getDisplayName());
        model.addAttribute("defaultPaymentAmount", record.getAmountPaid() == null ? valueOr(record.getTotalAmount(), BigDecimal.ZERO) : record.getAmountPaid());
        return "billing/paymentDetail";
    }

    @GetMapping("/reports")
    public String reports(@RequestParam(required = false) String from,
                          @RequestParam(required = false) String to,
                          Model model,
                          HttpSession session) {
        session.setAttribute("userRole", "BILLING");
        LocalDate start = from == null || from.isBlank() ? LocalDate.now().withDayOfMonth(1) : LocalDate.parse(from);
        LocalDate end = to == null || to.isBlank() ? LocalDate.now() : LocalDate.parse(to);
        List<BillingRecord> periodRecords = records().stream()
                .filter(record -> record.getCreatedAt() != null)
                .filter(record -> !record.getCreatedAt().toLocalDate().isBefore(start) && !record.getCreatedAt().toLocalDate().isAfter(end))
                .toList();
        addBillingModel(model, periodRecords);
        model.addAttribute("from", start);
        model.addAttribute("to", end);
        model.addAttribute("sourceTotals", periodRecords.stream()
                .collect(Collectors.groupingBy(record -> valueOr(record.getSourceType(), "OTHER"),
                        Collectors.reducing(BigDecimal.ZERO, record -> valueOr(record.getTotalAmount(), BigDecimal.ZERO), BigDecimal::add))));
        model.addAttribute("paymentMethodTotals", periodRecords.stream()
                .filter(record -> "PAID".equalsIgnoreCase(valueOr(record.getStatus(), "")))
                .collect(Collectors.groupingBy(record -> valueOr(record.getPaymentMethod(), "Unspecified"),
                        Collectors.reducing(BigDecimal.ZERO, record -> valueOr(record.getTotalAmount(), BigDecimal.ZERO), BigDecimal::add))));
        return "billing/financialReports";
    }

    private void addBillingModel(Model model, List<BillingRecord> records) {
        Map<Long, Patient> patientsById = patientRepository.findAllById(records.stream()
                        .map(BillingRecord::getPatientId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(Patient::getId, Function.identity()));
        BigDecimal unpaidTotal = records.stream()
                .filter(record -> !"PAID".equalsIgnoreCase(valueOr(record.getStatus(), "")))
                .map(BillingRecord::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paidTotal = records.stream()
                .filter(record -> "PAID".equalsIgnoreCase(valueOr(record.getStatus(), "")))
                .map(BillingRecord::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("records", records);
        model.addAttribute("patientsById", patientsById);
        model.addAttribute("unpaidCount", records.stream().filter(record -> !"PAID".equalsIgnoreCase(valueOr(record.getStatus(), ""))).count());
        model.addAttribute("paidCount", records.stream().filter(record -> "PAID".equalsIgnoreCase(valueOr(record.getStatus(), ""))).count());
        model.addAttribute("unpaidTotal", unpaidTotal);
        model.addAttribute("paidTotal", paidTotal);
        model.addAttribute("totalRevenue", paidTotal);
        model.addAttribute("grossBillings", paidTotal.add(unpaidTotal));
    }

    private List<BillingRecord> records() {
        return billingRecordRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private BillingRecord billingRecord(Long id) {
        return billingRecordRepository.findById(id).orElseThrow();
    }

    private Patient patient(Long id) {
        return id == null ? null : patientRepository.findById(id).orElse(null);
    }

    private String amount(BigDecimal value) {
        return value == null ? "0.00" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private BigDecimal valueOr(BigDecimal value, BigDecimal fallback) {
        return value == null ? fallback : value;
    }

    private byte[] buildInvoicePdf(BillingRecord record, Patient patient) {
        StringBuilder content = new StringBuilder();
        rect(content, 0, 790, 595, 52, "0.00 0.29 0.78");
        rect(content, 0, 0, 595, 790, "0.97 0.98 0.99");
        text(content, "TEL HEALTH", 42, 812, 18, true, "1 1 1");
        text(content, "Clinical Precision Redefined", 42, 795, 9, false, "0.86 0.91 1");
        text(content, "INVOICE", 460, 810, 22, true, "1 1 1");
        text(content, valueOr(record.getInvoiceNumber(), "INV-" + record.getId()), 460, 792, 10, false, "0.86 0.91 1");

        rect(content, 42, 690, 238, 72, "1 1 1");
        strokeRect(content, 42, 690, 238, 72, "0.88 0.91 0.94");
        text(content, "Provider", 56, 740, 9, true, "0.28 0.33 0.42");
        text(content, "Tel Health Enterprise", 56, 724, 13, true, "0.06 0.09 0.16");
        text(content, "Outpatient Department Billing", 56, 710, 9, false, "0.30 0.36 0.44");
        text(content, "billing@telhealth.local", 56, 698, 9, false, "0.30 0.36 0.44");

        rect(content, 315, 690, 238, 72, "1 1 1");
        strokeRect(content, 315, 690, 238, 72, "0.88 0.91 0.94");
        text(content, "Bill To", 329, 740, 9, true, "0.28 0.33 0.42");
        text(content, patient == null ? "Unknown Patient" : valueOr(patient.getFullName(), "Unknown Patient"), 329, 724, 13, true, "0.06 0.09 0.16");
        text(content, "Patient ID: " + (patient == null ? "-" : valueOr(patient.getPatientId(), "-")), 329, 710, 9, false, "0.30 0.36 0.44");
        text(content, "Phone: " + (patient == null ? "-" : valueOr(patient.getPhoneNumber(), "-")), 329, 698, 9, false, "0.30 0.36 0.44");

        rect(content, 42, 615, 511, 48, "1 1 1");
        strokeRect(content, 42, 615, 511, 48, "0.88 0.91 0.94");
        text(content, "Invoice Date", 56, 646, 8, true, "0.39 0.45 0.55");
        text(content, formatDate(record.getCreatedAt()), 56, 629, 11, true, "0.06 0.09 0.16");
        text(content, "Due Date", 180, 646, 8, true, "0.39 0.45 0.55");
        text(content, formatDueDate(record.getCreatedAt()), 180, 629, 11, true, "0.06 0.09 0.16");
        text(content, "Status", 304, 646, 8, true, "0.39 0.45 0.55");
        text(content, valueOr(record.getStatus(), "UNPAID"), 304, 629, 11, true, "0.00 0.29 0.78");
        text(content, "Source", 428, 646, 8, true, "0.39 0.45 0.55");
        text(content, valueOr(record.getSourceType(), "-"), 428, 629, 11, true, "0.06 0.09 0.16");

        rect(content, 42, 558, 511, 26, "0.00 0.29 0.78");
        text(content, "Description", 56, 567, 9, true, "1 1 1");
        text(content, "Qty", 330, 567, 9, true, "1 1 1");
        text(content, "Unit Price", 385, 567, 9, true, "1 1 1");
        text(content, "Total", 492, 567, 9, true, "1 1 1");
        rect(content, 42, 510, 511, 48, "1 1 1");
        strokeRect(content, 42, 510, 511, 48, "0.88 0.91 0.94");
        text(content, valueOr(record.getDescription(), "Clinical service"), 56, 538, 10, false, "0.06 0.09 0.16");
        text(content, valueOr(record.getSourceType(), "-"), 56, 523, 8, false, "0.39 0.45 0.55");
        text(content, String.valueOf(record.getQuantity() == null ? 1 : record.getQuantity()), 334, 532, 10, false, "0.06 0.09 0.16");
        text(content, amount(record.getUnitPrice()), 389, 532, 10, false, "0.06 0.09 0.16");
        text(content, amount(record.getTotalAmount()), 492, 532, 10, true, "0.06 0.09 0.16");

        rect(content, 340, 424, 213, 68, "1 1 1");
        strokeRect(content, 340, 424, 213, 68, "0.88 0.91 0.94");
        text(content, "Subtotal", 356, 470, 10, false, "0.30 0.36 0.44");
        text(content, amount(record.getTotalAmount()), 494, 470, 10, false, "0.30 0.36 0.44");
        text(content, "Tax", 356, 450, 10, false, "0.30 0.36 0.44");
        text(content, "0.00", 513, 450, 10, false, "0.30 0.36 0.44");
        text(content, "Total Due", 356, 430, 12, true, "0.06 0.09 0.16");
        text(content, amount(record.getTotalAmount()), 486, 430, 12, true, "0.00 0.29 0.78");

        rect(content, 42, 380, 511, 28, "0.93 0.97 1.00");
        strokeRect(content, 42, 380, 511, 28, "0.75 0.85 1.00");
        text(content, "Payment: " + valueOr(record.getPaymentMethod(), "Pending")
                + " | Reference: " + valueOr(record.getPaymentReference(), "-")
                + " | Received by: " + valueOr(record.getReceivedBy(), "-"), 56, 390, 9, false, "0.06 0.09 0.16");

        text(content, "Notes", 42, 330, 11, true, "0.06 0.09 0.16");
        text(content, "Please keep this invoice for payment reconciliation and patient account records.", 42, 314, 9, false, "0.30 0.36 0.44");
        text(content, "This computer-generated invoice is valid without signature.", 42, 302, 9, false, "0.30 0.36 0.44");
        text(content, "Generated on " + OffsetDateTime.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")), 42, 48, 8, false, "0.45 0.50 0.60");
        text(content, "Tel Health Enterprise", 440, 48, 8, true, "0.45 0.50 0.60");
        return pdf(content.toString());
    }

    private void text(StringBuilder builder, String value, int x, int y, int size, boolean bold, String color) {
        builder.append(color).append(" rg\n")
                .append("BT /").append(bold ? "F2" : "F1").append(" ").append(size).append(" Tf ")
                .append(x).append(" ").append(y).append(" Td (").append(pdfEscape(value)).append(") Tj ET\n");
    }

    private void rect(StringBuilder builder, int x, int y, int width, int height, String color) {
        builder.append(color).append(" rg\n")
                .append(x).append(" ").append(y).append(" ").append(width).append(" ").append(height).append(" re f\n");
    }

    private void strokeRect(StringBuilder builder, int x, int y, int width, int height, String color) {
        builder.append(color).append(" RG\n")
                .append("0.8 w\n")
                .append(x).append(" ").append(y).append(" ").append(width).append(" ").append(height).append(" re S\n");
    }

    private byte[] pdf(String content) {
        byte[] stream = content.getBytes(StandardCharsets.UTF_8);
        String[] objects = new String[]{
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R /F2 5 0 R >> >> /Contents 6 0 R >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>",
                "<< /Length " + stream.length + " >>\nstream\n" + content + "endstream"
        };
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, "%PDF-1.4\n");
        int[] offsets = new int[objects.length + 1];
        for (int index = 0; index < objects.length; index++) {
            offsets[index + 1] = out.size();
            write(out, (index + 1) + " 0 obj\n" + objects[index] + "\nendobj\n");
        }
        int xref = out.size();
        write(out, "xref\n0 " + (objects.length + 1) + "\n0000000000 65535 f \n");
        for (int index = 1; index < offsets.length; index++) {
            write(out, String.format("%010d 00000 n \n", offsets[index]));
        }
        write(out, "trailer\n<< /Size " + (objects.length + 1) + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF");
        return out.toByteArray();
    }

    private void write(ByteArrayOutputStream out, String value) {
        out.writeBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private String pdfEscape(String value) {
        return valueOr(value, "").replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private String formatDate(OffsetDateTime value) {
        return value == null ? "-" : value.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"));
    }

    private String formatDueDate(OffsetDateTime value) {
        return value == null ? "-" : value.plusDays(7).format(DateTimeFormatter.ofPattern("MMM dd, yyyy"));
    }

    private String storePaymentProof(BillingRecord record, MultipartFile receiptProof) {
        if (receiptProof == null || receiptProof.isEmpty()) {
            return record.getReceiptProofPath();
        }
        try {
            Path uploadDirectory = Path.of("src", "main", "resources", "static", "uploads", "payment-receipts");
            Files.createDirectories(uploadDirectory);
            String originalName = valueOr(receiptProof.getOriginalFilename(), "receipt").replaceAll("[^A-Za-z0-9._-]", "_");
            String filename = "invoice-" + record.getId() + "-" + System.currentTimeMillis() + "-" + originalName;
            Path target = uploadDirectory.resolve(filename).toAbsolutePath();
            receiptProof.transferTo(target);
            return "/uploads/payment-receipts/" + filename;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to store payment proof", exception);
        }
    }
}
