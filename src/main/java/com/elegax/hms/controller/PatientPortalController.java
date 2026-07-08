package com.elegax.hms.controller;

import com.elegax.hms.authentication.AuthenticationManager;
import com.elegax.hms.patients.entity.*;
import com.elegax.hms.patients.repository.*;
import com.elegax.hms.patients.service.SchedulingService;
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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/patient")
public class PatientPortalController {

    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final DoctorScheduleRepository doctorScheduleRepository;
    private final StaffMemberRepository staffMemberRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final ConsultationRepository consultationRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final InvestigationRequestRepository investigationRequestRepository;
    private final BillingRecordRepository billingRecordRepository;
    private final PatientMessageRepository patientMessageRepository;
    private final SchedulingService schedulingService;
    private final AuthenticationManager authenticationManager;

    public PatientPortalController(PatientRepository patientRepository,
                                   AppointmentRepository appointmentRepository,
                                   DoctorScheduleRepository doctorScheduleRepository,
                                   StaffMemberRepository staffMemberRepository,
                                   LeaveRequestRepository leaveRequestRepository,
                                   ConsultationRepository consultationRepository,
                                   PrescriptionRepository prescriptionRepository,
                                   InvestigationRequestRepository investigationRequestRepository,
                                   BillingRecordRepository billingRecordRepository,
                                   PatientMessageRepository patientMessageRepository,
                                   SchedulingService schedulingService,
                                   AuthenticationManager authenticationManager) {
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
        this.doctorScheduleRepository = doctorScheduleRepository;
        this.staffMemberRepository = staffMemberRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.consultationRepository = consultationRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.investigationRequestRepository = investigationRequestRepository;
        this.billingRecordRepository = billingRecordRepository;
        this.patientMessageRepository = patientMessageRepository;
        this.schedulingService = schedulingService;
        this.authenticationManager = authenticationManager;
    }

    @GetMapping({"", "/", "/home", "/dashboard"})
    public String dashboard(Model model, HttpSession session) {
        addBaseModel(model, session);
        return "patient/patientDashboard";
    }

    @GetMapping("/profile")
    public String profile(Model model, HttpSession session) {
        addBaseModel(model, session);
        return "patient/profile";
    }

    @PostMapping("/profile")
    public String saveProfile(@ModelAttribute Patient form, RedirectAttributes redirectAttributes) {
        Patient patient = currentPatient().orElseGet(Patient::new);
        patient.setFullName(valueOr(form.getFullName(), authenticationManager.getDisplayName()));
        patient.setDateOfBirth(form.getDateOfBirth());
        patient.setGender(form.getGender());
        patient.setBloodGroup(form.getBloodGroup());
        patient.setAllergies(form.getAllergies());
        patient.setPhoneNumber(form.getPhoneNumber());
        patient.setEmailAddress(valueOr(form.getEmailAddress(), authenticationManager.getUsername()));
        patient.setResidentialAddress(form.getResidentialAddress());
        patient.setInsuranceProvider(form.getInsuranceProvider());
        patient.setPolicyNumber(form.getPolicyNumber());
        patient.setGroupId(form.getGroupId());
        patient.setInsuranceExpirationDate(form.getInsuranceExpirationDate());
        patient.setEmergencyContactName(form.getEmergencyContactName());
        patient.setEmergencyContactRelationship(form.getEmergencyContactRelationship());
        patient.setEmergencyContactPhone(form.getEmergencyContactPhone());
        if (patient.getPatientId() == null || patient.getPatientId().isBlank()) {
            patient.setPatientId(generatePatientId());
        }
        patientRepository.save(patient);
        redirectAttributes.addFlashAttribute("successMessage", "Profile saved.");
        return "redirect:/patient/dashboard";
    }

    @GetMapping("/appointments/new")
    public String newAppointment(Model model, HttpSession session) {
        addBaseModel(model, session);
        Map<String, StaffMember> activeDoctorsByUsername = staffMemberRepository.findAll(Sort.by(Sort.Direction.ASC, "fullName"))
                .stream()
                .filter(staff -> "DOCTOR".equalsIgnoreCase(valueOr(staff.getStaffRole(), "")))
                .filter(staff -> "ACTIVE".equalsIgnoreCase(valueOr(staff.getStatus(), "ACTIVE")))
                .filter(staff -> staff.getEmail() != null && !staff.getEmail().isBlank())
                .collect(Collectors.toMap(staff -> staff.getEmail().toLowerCase(), Function.identity(), (first, ignored) -> first));
        List<DoctorSchedule> availableSchedules = doctorScheduleRepository.findByAvailableTrueOrderByDoctorNameAscDayOfWeekAscStartTimeAsc()
                .stream()
                .filter(schedule -> schedule.getDoctorUsername() != null)
                .filter(schedule -> activeDoctorsByUsername.containsKey(schedule.getDoctorUsername().toLowerCase()))
                .toList();
        List<Appointment> bookedAppointments = appointmentRepository.findAll(Sort.by(Sort.Direction.ASC, "scheduledAt"))
                .stream()
                .filter(appointment -> appointment.getScheduledAt() != null)
                .filter(appointment -> appointment.getProviderUsername() != null)
                .filter(appointment -> !"CANCELLED".equalsIgnoreCase(valueOr(appointment.getStatus(), ""))
                        && !"COMPLETED".equalsIgnoreCase(valueOr(appointment.getStatus(), "")))
                .toList();
        List<LeaveRequest> approvedDoctorLeaves = leaveRequestRepository.findAll(Sort.by(Sort.Direction.ASC, "startDate"))
                .stream()
                .filter(leave -> "APPROVED".equalsIgnoreCase(valueOr(leave.getStatus(), "")))
                .filter(leave -> leave.getStartDate() != null && leave.getEndDate() != null)
                .filter(leave -> activeDoctorsByUsername.values().stream().anyMatch(doctor -> Objects.equals(doctor.getId(), leave.getStaffMemberId())))
                .toList();
        model.addAttribute("doctorSchedules", availableSchedules);
        model.addAttribute("bookedAppointments", bookedAppointments);
        model.addAttribute("approvedDoctorLeaves", approvedDoctorLeaves);
        model.addAttribute("staffById", activeDoctorsByUsername.values().stream().collect(Collectors.toMap(StaffMember::getId, Function.identity())));
        model.addAttribute("doctorStaffByUsername", activeDoctorsByUsername);
        model.addAttribute("clinicalDepartments", activeDoctorsByUsername.values()
                .stream()
                .map(StaffMember::getDepartment)
                .filter(department -> department != null && !department.isBlank())
                .distinct()
                .sorted(String::compareToIgnoreCase)
                .toList());
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("doctors", availableSchedules.stream()
                .collect(Collectors.toMap(DoctorSchedule::getDoctorUsername, Function.identity(), (first, ignored) -> first))
                .values()
                .stream()
                .sorted((first, second) -> String.valueOf(first.getDoctorName()).compareToIgnoreCase(String.valueOf(second.getDoctorName())))
                .toList());
        return "patient/bookAppointment";
    }

    @PostMapping("/appointments")
    public String bookAppointment(@RequestParam String providerUsername,
                                  @RequestParam String department,
                                  @RequestParam String scheduledAt,
                                  @RequestParam String visitType,
                                  @RequestParam(required = false) String reason,
                                  RedirectAttributes redirectAttributes) {
        try {
            Patient patient = requireCurrentPatient();
            schedulingService.bookAppointment(patient.getId(), providerUsername, parseAppointmentDateTime(scheduledAt), department, visitType, "Routine", reason);
        } catch (ResponseStatusException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", valueOr(exception.getReason(), "Unable to book this appointment. Please select another available slot."));
            return "redirect:/patient/appointments/new";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Appointment booked.");
        return "redirect:/patient/appointments";
    }

    @GetMapping("/appointments")
    public String appointments(Model model, HttpSession session) {
        addBaseModel(model, session);
        return "patient/appointments";
    }

    @GetMapping("/records")
    public String records(Model model, HttpSession session) {
        addBaseModel(model, session);
        return "patient/medicalRecords";
    }

    @GetMapping("/prescriptions")
    public String prescriptions(Model model, HttpSession session) {
        addBaseModel(model, session);
        return "patient/prescriptions";
    }

    @GetMapping("/reports")
    public String reports(Model model, HttpSession session) {
        addBaseModel(model, session);
        return "patient/reports";
    }

    @GetMapping("/reports/{id}/download")
    public ResponseEntity<ByteArrayResource> downloadReport(@PathVariable Long id) {
        Patient patient = requireCurrentPatient();
        InvestigationRequest request = investigationRequestRepository.findById(id)
                .filter(report -> Objects.equals(report.getPatientId(), patient.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));
        String content = """
                Tel Health Investigation Report

                Patient: %s
                Patient ID: %s
                Test: %s
                Type: %s
                Status: %s
                Sample: %s
                Collected: %s by %s
                Verified: %s by %s

                Result Summary:
                %s

                Result Parameters:
                %s
                """.formatted(patient.getFullName(), patient.getPatientId(), request.getTestName(), request.getRequestType(),
                request.getStatus(), valueOr(request.getSampleType(), "-"),
                request.getCollectedAt() == null ? "-" : request.getCollectedAt(), valueOr(request.getCollectedBy(), "-"),
                request.getVerifiedAt() == null ? "-" : request.getVerifiedAt(), valueOr(request.getVerifiedBy(), "-"),
                valueOr(request.getResultSummary(), "Result is not ready yet."),
                valueOr(request.getResultParameters(), "-"));
        ByteArrayResource resource = new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=report-" + request.getId() + ".txt")
                .contentType(MediaType.TEXT_PLAIN)
                .body(resource);
    }

    @GetMapping("/billing")
    public String billing(Model model, HttpSession session) {
        addBaseModel(model, session);
        return "patient/billing";
    }

    @PostMapping("/billing/{id}/pay")
    public String payBill(@PathVariable Long id,
                          @RequestParam String paymentMethod,
                          @RequestParam(required = false) BigDecimal amountPaid,
                          @RequestParam String paymentReference,
                          @RequestParam String paidBy,
                          @RequestParam(required = false) String patientPaymentNote,
                          @RequestParam(required = false) MultipartFile receiptProof,
                          RedirectAttributes redirectAttributes) {
        Patient patient = requireCurrentPatient();
        BillingRecord bill = billingRecordRepository.findById(id)
                .filter(record -> Objects.equals(record.getPatientId(), patient.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bill not found"));
        BigDecimal submittedAmount = amountPaid == null ? valueOr(bill.getTotalAmount(), BigDecimal.ZERO) : amountPaid;
        BigDecimal totalAmount = valueOr(bill.getTotalAmount(), BigDecimal.ZERO);
        bill.setStatus(submittedAmount.compareTo(totalAmount) >= 0 ? "PAYMENT_SUBMITTED" : "PARTIAL_PAYMENT_SUBMITTED");
        bill.setPaymentMethod(paymentMethod);
        bill.setAmountPaid(submittedAmount);
        bill.setPaymentReference(paymentReference);
        bill.setPaidBy(paidBy);
        bill.setPatientPaymentNote(patientPaymentNote);
        bill.setReceiptProofPath(storePatientPaymentProof(bill, receiptProof));
        bill.setPaymentSubmittedAt(OffsetDateTime.now());
        bill.setPaidAt(null);
        bill.setReceivedBy(null);
        billingRecordRepository.save(bill);
        redirectAttributes.addFlashAttribute("successMessage", "Payment submitted for billing confirmation.");
        return "redirect:/patient/billing";
    }

    @GetMapping("/messages")
    public String messages(Model model, HttpSession session) {
        addBaseModel(model, session);
        return "patient/messaging";
    }

    @PostMapping("/messages")
    public String sendMessage(@RequestParam String subject,
                              @RequestParam String message,
                              RedirectAttributes redirectAttributes) {
        Patient patient = requireCurrentPatient();
        patientMessageRepository.save(PatientMessage.builder()
                .patientId(patient.getId())
                .subject(subject)
                .message(message)
                .status("SENT")
                .build());
        redirectAttributes.addFlashAttribute("successMessage", "Message sent to your care team.");
        return "redirect:/patient/messages";
    }

    private void addBaseModel(Model model, HttpSession session) {
        session.setAttribute("userRole", "PATIENT");
        Optional<Patient> patient = currentPatient();
        Patient profile = patient.orElse(null);
        List<Appointment> appointments = profile == null ? List.of() : appointmentRepository.findByPatientId(profile.getId()).stream()
                .sorted(Comparator.comparing(Appointment::getScheduledAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        List<Consultation> consultations = profile == null ? List.of() : consultationRepository.findByPatientId(profile.getId()).stream()
                .sorted(Comparator.comparing(Consultation::getConsultationAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        List<Prescription> prescriptions = profile == null ? List.of() : prescriptionRepository.findByPatientId(profile.getId()).stream()
                .sorted(Comparator.comparing(Prescription::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        List<InvestigationRequest> investigations = profile == null ? List.of() : investigationRequestRepository.findByPatientId(profile.getId()).stream()
                .sorted(Comparator.comparing(InvestigationRequest::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        List<BillingRecord> bills = profile == null ? List.of() : billingRecordRepository.findByPatientId(profile.getId()).stream()
                .sorted(Comparator.comparing(BillingRecord::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        BigDecimal outstandingTotal = bills.stream()
                .filter(record -> !"PAID".equalsIgnoreCase(valueOr(record.getStatus(), "")))
                .map(BillingRecord::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("patient", profile);
        model.addAttribute("profileComplete", profile != null && hasText(profile.getPhoneNumber()) && hasText(profile.getResidentialAddress()));
        model.addAttribute("patientName", profile != null ? profile.getFullName() : authenticationManager.getDisplayName());
        model.addAttribute("appointments", appointments);
        model.addAttribute("consultations", consultations);
        model.addAttribute("prescriptions", prescriptions);
        model.addAttribute("investigations", investigations);
        model.addAttribute("bills", bills);
        model.addAttribute("messages", profile == null ? List.of() : patientMessageRepository.findByPatientIdOrderByCreatedAtDesc(profile.getId()));
        model.addAttribute("upcomingCount", appointments.stream().filter(appointment -> "BOOKED".equalsIgnoreCase(valueOr(appointment.getStatus(), ""))).count());
        model.addAttribute("outstandingTotal", outstandingTotal);
        model.addAttribute("outstandingCount", bills.stream().filter(record -> !"PAID".equalsIgnoreCase(valueOr(record.getStatus(), ""))).count());
        model.addAttribute("readyReportCount", investigations.stream().filter(report -> "RESULT_READY".equalsIgnoreCase(valueOr(report.getStatus(), ""))).count());
    }

    private Optional<Patient> currentPatient() {
        String username = authenticationManager.getUsername();
        String displayName = authenticationManager.getDisplayName();
        return patientRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .filter(patient -> valueOr(patient.getEmailAddress(), "").equalsIgnoreCase(username)
                        || valueOr(patient.getFullName(), "").equalsIgnoreCase(displayName))
                .findFirst();
    }

    private Patient requireCurrentPatient() {
        return currentPatient().orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Complete your patient profile before using this feature"));
    }

    private String generatePatientId() {
        String patientId;
        long nextNumber = patientRepository.count() + 1;
        do {
            patientId = "PID-" + LocalDate.now().getYear() + "-" + String.format("%04d", nextNumber++);
        } while (patientRepository.existsByPatientId(patientId));
        return patientId;
    }

    private OffsetDateTime parseAppointmentDateTime(String scheduledAt) {
        if (scheduledAt == null || scheduledAt.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Appointment date and time is required");
        }
        if (scheduledAt.endsWith("Z") || scheduledAt.matches(".*[+-]\\d{2}:\\d{2}$")) {
            return OffsetDateTime.parse(scheduledAt);
        }
        return LocalDateTime.parse(scheduledAt).atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private BigDecimal valueOr(BigDecimal value, BigDecimal fallback) {
        return value == null ? fallback : value;
    }

    private String storePatientPaymentProof(BillingRecord bill, MultipartFile receiptProof) {
        if (receiptProof == null || receiptProof.isEmpty()) {
            return bill.getReceiptProofPath();
        }
        try {
            Path uploadDirectory = Path.of("src", "main", "resources", "static", "uploads", "patient-payment-proofs");
            Files.createDirectories(uploadDirectory);
            String originalName = valueOr(receiptProof.getOriginalFilename(), "payment-proof").replaceAll("[^A-Za-z0-9._-]", "_");
            String filename = "invoice-" + bill.getId() + "-" + System.currentTimeMillis() + "-" + originalName;
            Path target = uploadDirectory.resolve(filename).toAbsolutePath();
            receiptProof.transferTo(target);
            return "/uploads/patient-payment-proofs/" + filename;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to store payment proof", exception);
        }
    }
}
