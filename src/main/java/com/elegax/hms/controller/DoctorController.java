package com.elegax.hms.controller;

import com.elegax.hms.authentication.AuthenticationManager;
import com.elegax.hms.patients.entity.*;
import com.elegax.hms.patients.repository.*;
import com.elegax.hms.patients.service.BillingWorkflowService;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/doctor")
public class DoctorController {

    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final QueueEntryRepository queueEntryRepository;
    private final ConsultationRepository consultationRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final InvestigationRequestRepository investigationRequestRepository;
    private final DoctorScheduleRepository doctorScheduleRepository;
    private final StaffMemberRepository staffMemberRepository;
    private final AuthenticationManager authenticationManager;
    private final BillingWorkflowService billingWorkflowService;

    public DoctorController(PatientRepository patientRepository,
                            AppointmentRepository appointmentRepository,
                            QueueEntryRepository queueEntryRepository,
                            ConsultationRepository consultationRepository,
                            PrescriptionRepository prescriptionRepository,
                            InvestigationRequestRepository investigationRequestRepository,
                            DoctorScheduleRepository doctorScheduleRepository,
                            StaffMemberRepository staffMemberRepository,
                            AuthenticationManager authenticationManager,
                            BillingWorkflowService billingWorkflowService) {
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
        this.queueEntryRepository = queueEntryRepository;
        this.consultationRepository = consultationRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.investigationRequestRepository = investigationRequestRepository;
        this.doctorScheduleRepository = doctorScheduleRepository;
        this.staffMemberRepository = staffMemberRepository;
        this.authenticationManager = authenticationManager;
        this.billingWorkflowService = billingWorkflowService;
    }

    @GetMapping({"/home", "/dashboard"})
    public String dashboard(Model model, HttpSession session) {
        session.setAttribute("userRole", "DOCTOR");
        List<QueueEntry> queueEntries = activeQueueEntries();
        addQueueModel(model, queueEntries);
        model.addAttribute("completedToday", appointmentsForCurrentDoctor().stream()
                .filter(appointment -> "COMPLETED".equals(appointment.getStatus()))
                .count());
        return "doctor/doctorDashboard";
    }

    @GetMapping("/queue")
    public String queue(Model model, HttpSession session) {
        session.setAttribute("userRole", "DOCTOR");
        addQueueModel(model, activeQueueEntries());
        return "doctor/patientQueue";
    }

    @GetMapping("/profile")
    public String profile(Model model, HttpSession session) {
        session.setAttribute("userRole", "DOCTOR");
        StaffMember doctor = currentDoctorStaffMember();
        List<DoctorSchedule> schedules = doctorScheduleRepository.findByDoctorUsernameOrderByIdAsc(currentDoctorUsername());
        model.addAttribute("doctor", doctor);
        model.addAttribute("doctorName", doctor != null ? doctor.getFullName() : authenticationManager.getDisplayName());
        model.addAttribute("doctorEmail", doctor != null ? doctor.getEmail() : authenticationManager.getUsername());
        model.addAttribute("schedules", schedules);
        model.addAttribute("appointmentCount", appointmentsForCurrentDoctor().size());
        model.addAttribute("completedCount", appointmentsForCurrentDoctor().stream()
                .filter(appointment -> "COMPLETED".equals(appointment.getStatus()))
                .count());
        return "doctor/profile";
    }

    @PostMapping("/queue/{queueEntryId}/call")
    public String callPatient(@PathVariable Long queueEntryId) {
        QueueEntry queueEntry = getQueueEntry(queueEntryId);
        requireCurrentDoctorQueue(queueEntry);
        if ("READY_FOR_DOCTOR".equals(queueEntry.getStatus())) {
            queueEntry.setStatus("CALLED");
        }
        queueEntryRepository.save(queueEntry);
        return "redirect:/doctor/patients/" + queueEntry.getPatientId() + "/history?queueEntryId=" + queueEntry.getId();
    }

    @GetMapping("/patients/{patientId}/history")
    public String patientHistory(@PathVariable Long patientId,
                                 @RequestParam(required = false) Long queueEntryId,
                                 Model model,
                                 HttpSession session) {
        session.setAttribute("userRole", "DOCTOR");
        if (!patientBookedWithCurrentDoctor(patientId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This patient is booked with another doctor");
        }
        QueueEntry queueEntry = queueEntryId == null ? null : getQueueEntry(queueEntryId);
        if (queueEntry != null) {
            requireCurrentDoctorQueue(queueEntry);
        }
        Patient patient = getPatient(patientId);
        model.addAttribute("patient", patient);
        model.addAttribute("queueEntry", queueEntry);
        model.addAttribute("consultations", latestConsultations(consultationRepository.findByPatientId(patientId)));
        model.addAttribute("prescriptions", latestPrescriptions(prescriptionRepository.findByPatientId(patientId)));
        model.addAttribute("investigations", latestInvestigations(investigationRequestRepository.findByPatientId(patientId)));
        return "doctor/patientMedicalHistory";
    }

    @GetMapping("/consultation/{queueEntryId}")
    public String consultation(@PathVariable Long queueEntryId, Model model, HttpSession session) {
        session.setAttribute("userRole", "DOCTOR");
        QueueEntry queueEntry = getQueueEntry(queueEntryId);
        requireCurrentDoctorQueue(queueEntry);
        Appointment appointment = getAppointment(queueEntry.getAppointmentId());
        Patient patient = getPatient(queueEntry.getPatientId());
        Consultation consultation = consultationRepository.findByAppointmentId(appointment.getId())
                .stream()
                .filter(existing -> !"COMPLETED".equals(existing.getStatus()))
                .findFirst()
                .orElseGet(() -> consultationRepository.save(Consultation.builder()
                        .appointmentId(appointment.getId())
                        .patientId(patient.getId())
                        .consultationAt(OffsetDateTime.now())
                        .status("IN_PROGRESS")
                        .build()));

        boolean resultsReadyForReview = "RESULTS_READY".equals(consultation.getStatus()) || "RESULTS_READY".equals(queueEntry.getStatus());
        consultation.setStatus("IN_PROGRESS");
        consultationRepository.save(consultation);

        queueEntry.setStatus("IN_PROGRESS");
        queueEntryRepository.save(queueEntry);
        List<InvestigationRequest> investigations = latestInvestigations(investigationRequestRepository.findByConsultationId(consultation.getId()));
        long pendingInvestigationCount = investigations.stream()
                .filter(investigation -> !"RESULT_READY".equals(investigation.getStatus()) && !"COMPLETED".equals(investigation.getStatus()))
                .count();

        model.addAttribute("queueEntry", queueEntry);
        model.addAttribute("appointment", appointment);
        model.addAttribute("patient", patient);
        model.addAttribute("consultation", consultation);
        model.addAttribute("prescriptions", latestPrescriptions(prescriptionRepository.findByConsultationId(consultation.getId())));
        model.addAttribute("investigations", investigations);
        model.addAttribute("pendingInvestigationCount", pendingInvestigationCount);
        model.addAttribute("resultsReadyForReview", resultsReadyForReview);
        return "reception/doctorConsultation";
    }

    @PostMapping("/consultation/{queueEntryId}/save")
    public String saveConsultation(@PathVariable Long queueEntryId,
                                   @RequestParam(required = false) String symptoms,
                                   @RequestParam(required = false) String subjectiveNotes,
                                   @RequestParam(required = false) String objectiveNotes,
                                   @RequestParam(required = false) String assessment,
                                   @RequestParam(required = false) String diagnosis,
                                   @RequestParam(required = false) String treatmentPlan,
                                   @RequestParam(required = false) String followUpDate,
                                   @RequestParam(required = false) String doctorNotes) {
        QueueEntry queueEntry = getQueueEntry(queueEntryId);
        requireCurrentDoctorQueue(queueEntry);
        Appointment appointment = getAppointment(queueEntry.getAppointmentId());
        Consultation consultation = consultationRepository.findByAppointmentId(appointment.getId())
                .stream()
                .filter(existing -> !"COMPLETED".equals(existing.getStatus()))
                .findFirst()
                .orElseGet(() -> Consultation.builder()
                        .appointmentId(appointment.getId())
                        .patientId(queueEntry.getPatientId())
                        .consultationAt(OffsetDateTime.now())
                        .build());

        consultation.setStatus("IN_PROGRESS");
        consultation.setSymptoms(symptoms);
        consultation.setSubjectiveNotes(subjectiveNotes);
        consultation.setObjectiveNotes(objectiveNotes);
        consultation.setAssessment(assessment);
        consultation.setDiagnosis(diagnosis);
        consultation.setTreatmentPlan(treatmentPlan);
        consultation.setFollowUpDate(followUpDate);
        consultation.setDoctorNotes(doctorNotes);
        consultationRepository.save(consultation);
        queueEntry.setStatus("IN_PROGRESS");
        queueEntryRepository.save(queueEntry);
        return "redirect:/doctor/consultation/" + queueEntryId + "?saved";
    }

    @PostMapping("/consultation/{queueEntryId}/prescriptions")
    public String addPrescription(@PathVariable Long queueEntryId,
                                  @RequestParam String medicationName,
                                  @RequestParam(required = false) String dosage,
                                  @RequestParam(required = false) String frequency,
                                  @RequestParam(required = false) String duration,
                                  @RequestParam(required = false) String route,
                                  @RequestParam(required = false) String instructions) {
        if (!allText(medicationName, dosage, frequency, duration, route, instructions)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Complete all prescription fields before adding a prescription");
        }
        Consultation consultation = activeConsultation(queueEntryId);
        prescriptionRepository.save(Prescription.builder()
                .consultationId(consultation.getId())
                .patientId(consultation.getPatientId())
                .medicationName(medicationName)
                .dosage(dosage)
                .frequency(frequency)
                .duration(duration)
                .route(route)
                .instructions(instructions)
                .build());
        return "redirect:/doctor/consultation/" + queueEntryId + "?prescriptionAdded";
    }

    @PostMapping("/consultation/{queueEntryId}/investigations")
    public String addInvestigation(@PathVariable Long queueEntryId,
                                   @RequestParam String requestType,
                                   @RequestParam String testName,
                                   @RequestParam(required = false, defaultValue = "Routine") String priority,
                                   @RequestParam(required = false) String notes) {
        Consultation consultation = activeConsultation(queueEntryId);
        investigationRequestRepository.save(InvestigationRequest.builder()
                .consultationId(consultation.getId())
                .patientId(consultation.getPatientId())
                .requestType(requestType)
                .testName(testName)
                .priority(priority)
                .notes(notes)
                .build());
        return "redirect:/doctor/consultation/" + queueEntryId + "?investigationAdded";
    }

    @GetMapping("/investigations/{id}/report")
    public ResponseEntity<ByteArrayResource> investigationReport(@PathVariable Long id) {
        InvestigationRequest request = investigationRequestRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));
        if (!patientBookedWithCurrentDoctor(request.getPatientId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This patient is booked with another doctor");
        }
        if (!"RESULT_READY".equals(request.getStatus()) && !"COMPLETED".equals(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Report is not ready");
        }
        Patient patient = getPatient(request.getPatientId());
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
                valueOr(request.getResultSummary(), "-"), valueOr(request.getResultParameters(), "-"));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=investigation-report-" + request.getId() + ".txt")
                .contentType(MediaType.TEXT_PLAIN)
                .body(new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)));
    }

    @PostMapping("/consultation/{queueEntryId}/await-results")
    public String awaitResults(@PathVariable Long queueEntryId) {
        QueueEntry queueEntry = getQueueEntry(queueEntryId);
        requireCurrentDoctorQueue(queueEntry);
        Consultation consultation = activeConsultation(queueEntryId);
        boolean hasPendingInvestigations = investigationRequestRepository.findByConsultationId(consultation.getId())
                .stream()
                .anyMatch(investigation -> !"RESULT_READY".equals(investigation.getStatus()) && !"COMPLETED".equals(investigation.getStatus()));
        if (!hasPendingInvestigations) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No pending lab or radiology requests exist for this consultation");
        }

        consultation.setStatus("AWAITING_RESULTS");
        consultationRepository.save(consultation);
        queueEntry.setStatus("AWAITING_RESULTS");
        queueEntryRepository.save(queueEntry);
        return "redirect:/doctor/queue?awaitingResults";
    }

    @PostMapping("/consultation/{queueEntryId}/complete")
    public String completeConsultation(@PathVariable Long queueEntryId,
                                       @RequestParam(required = false) String symptoms,
                                       @RequestParam(required = false) String subjectiveNotes,
                                       @RequestParam(required = false) String objectiveNotes,
                                       @RequestParam(required = false) String assessment,
                                       @RequestParam(required = false) String diagnosis,
                                       @RequestParam(required = false) String treatmentPlan,
                                       @RequestParam(required = false) String followUpDate,
                                       @RequestParam(required = false) String doctorNotes) {
        QueueEntry queueEntry = getQueueEntry(queueEntryId);
        requireCurrentDoctorQueue(queueEntry);
        Appointment appointment = getAppointment(queueEntry.getAppointmentId());
        Consultation consultation = activeConsultation(queueEntryId);
        if (hasText(symptoms) || hasText(subjectiveNotes) || hasText(objectiveNotes) || hasText(assessment) || hasText(diagnosis) || hasText(treatmentPlan) || hasText(followUpDate) || hasText(doctorNotes)) {
            consultation.setSymptoms(symptoms);
            consultation.setSubjectiveNotes(subjectiveNotes);
            consultation.setObjectiveNotes(objectiveNotes);
            consultation.setAssessment(assessment);
            consultation.setDiagnosis(diagnosis);
            consultation.setTreatmentPlan(treatmentPlan);
            consultation.setFollowUpDate(followUpDate);
            consultation.setDoctorNotes(doctorNotes);
        }
        if (!allText(consultation.getSymptoms(), consultation.getSubjectiveNotes(), consultation.getObjectiveNotes(), consultation.getAssessment(), consultation.getDiagnosis(), consultation.getTreatmentPlan())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Complete SOAP notes and diagnosis before completing consultation");
        }
        boolean hasPendingInvestigations = investigationRequestRepository.findByConsultationId(consultation.getId())
                .stream()
                .anyMatch(investigation -> !"RESULT_READY".equals(investigation.getStatus()) && !"COMPLETED".equals(investigation.getStatus()));
        if (hasPendingInvestigations) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pending lab or radiology results must be reviewed before completing consultation");
        }
        consultation.setStatus("COMPLETED");
        consultationRepository.save(consultation);
        billingWorkflowService.createForConsultation(consultation.getPatientId(), consultation.getId());

        queueEntry.setStatus("SERVED");
        queueEntry.setServedAt(OffsetDateTime.now());
        queueEntryRepository.save(queueEntry);

        appointment.setStatus("COMPLETED");
        appointmentRepository.save(appointment);
        return "redirect:/doctor/queue?completed";
    }

    @GetMapping("/patients/{patientId}/prescriptions")
    public String prescriptions(@PathVariable Long patientId, Model model, HttpSession session) {
        session.setAttribute("userRole", "DOCTOR");
        if (!patientBookedWithCurrentDoctor(patientId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This patient is booked with another doctor");
        }
        Patient patient = getPatient(patientId);
        model.addAttribute("patient", patient);
        model.addAttribute("patientsById", Map.of(patient.getId(), patient));
        model.addAttribute("prescriptions", latestPrescriptions(prescriptionRepository.findByPatientId(patientId)));
        return "doctor/patientPrescription";
    }

    @GetMapping("/prescriptions")
    public String prescriptionsIndex(Model model, HttpSession session) {
        session.setAttribute("userRole", "DOCTOR");
        List<Long> patientIds = appointmentsForCurrentDoctor().stream()
                .map(Appointment::getPatientId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        model.addAttribute("patient", null);
        List<Prescription> prescriptions = latestPrescriptions(prescriptionRepository.findAll()
                .stream()
                .filter(prescription -> patientIds.contains(prescription.getPatientId()))
                .toList());
        model.addAttribute("patientsById", patientRepository.findAllById(patientIds)
                .stream()
                .collect(Collectors.toMap(Patient::getId, Function.identity())));
        model.addAttribute("prescriptions", prescriptions);
        return "doctor/patientPrescription";
    }

    @GetMapping("/medical-history")
    public String medicalHistoryIndex(Model model, HttpSession session) {
        session.setAttribute("userRole", "DOCTOR");
        List<Appointment> appointments = appointmentsForCurrentDoctor();
        List<Long> patientIds = appointments.stream()
                .map(Appointment::getPatientId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Patient> patientsById = patientRepository.findAllById(patientIds)
                .stream()
                .collect(Collectors.toMap(Patient::getId, Function.identity()));
        List<Patient> patients = patientIds.stream()
                .map(patientsById::get)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, Appointment> latestAppointmentsByPatientId = appointments.stream()
                .filter(appointment -> appointment.getPatientId() != null)
                .collect(Collectors.toMap(Appointment::getPatientId, Function.identity(), (first, ignored) -> first, LinkedHashMap::new));
        Map<Long, QueueEntry> activeQueueEntriesByPatientId = queueEntryRepository.findAll(Sort.by(Sort.Direction.DESC, "queuedAt"))
                .stream()
                .filter(entry -> patientIds.contains(entry.getPatientId()))
                .filter(entry -> "READY_FOR_DOCTOR".equals(entry.getStatus()) || "CALLED".equals(entry.getStatus()) || "IN_PROGRESS".equals(entry.getStatus()) || "RESULTS_READY".equals(entry.getStatus()))
                .collect(Collectors.toMap(QueueEntry::getPatientId, Function.identity(), (first, ignored) -> first, LinkedHashMap::new));

        model.addAttribute("patient", null);
        model.addAttribute("patients", patients);
        model.addAttribute("patientsById", patientsById);
        model.addAttribute("latestAppointmentsByPatientId", latestAppointmentsByPatientId);
        model.addAttribute("activeQueueEntriesByPatientId", activeQueueEntriesByPatientId);
        model.addAttribute("consultations", List.of());
        model.addAttribute("prescriptions", List.of());
        model.addAttribute("investigations", List.of());
        return "doctor/patientMedicalHistory";
    }

    @GetMapping("/schedule")
    public String schedule(Model model, HttpSession session) {
        session.setAttribute("userRole", "DOCTOR");
        List<String> days = scheduleDays();
        Map<String, DoctorSchedule> scheduleByDay = doctorScheduleRepository.findByDoctorUsernameOrderByIdAsc(currentDoctorUsername())
                .stream()
                .collect(Collectors.toMap(DoctorSchedule::getDayOfWeek, Function.identity(), (first, ignored) -> first));
        model.addAttribute("days", days);
        model.addAttribute("scheduleByDay", scheduleByDay);
        return "doctor/schedule";
    }

    @PostMapping("/schedule")
    public String saveSchedule(@RequestParam List<String> days,
                               @RequestParam(required = false) List<String> availableDays,
                               @RequestParam List<String> startTimes,
                               @RequestParam List<String> endTimes) {
        String doctorUsername = currentDoctorUsername();
        String doctorName = authenticationManager.getDisplayName();
        List<String> selectedDays = availableDays == null ? List.of() : availableDays;

        for (int index = 0; index < days.size(); index++) {
            String day = days.get(index);
            DoctorSchedule schedule = doctorScheduleRepository.findByDoctorUsernameAndDayOfWeek(doctorUsername, day)
                    .orElseGet(() -> DoctorSchedule.builder()
                            .doctorUsername(doctorUsername)
                            .dayOfWeek(day)
                            .build());
            schedule.setDoctorName(doctorName);
            schedule.setAvailable(selectedDays.contains(day));
            schedule.setStartTime(LocalTime.parse(startTimes.get(index)));
            schedule.setEndTime(LocalTime.parse(endTimes.get(index)));
            doctorScheduleRepository.save(schedule);
        }

        return "redirect:/doctor/schedule?saved";
    }

    private List<QueueEntry> activeQueueEntries() {
        String doctorUsername = currentDoctorUsername();
        return queueEntryRepository.findAll(Sort.by(Sort.Direction.DESC, "queuedAt"))
                .stream()
                .filter(entry -> !"SERVED".equals(entry.getStatus()))
                .filter(entry -> "READY_FOR_DOCTOR".equals(entry.getStatus()) || "CALLED".equals(entry.getStatus()) || "IN_PROGRESS".equals(entry.getStatus()) || "RESULTS_READY".equals(entry.getStatus()))
                .filter(entry -> appointmentBelongsToCurrentDoctor(entry, doctorUsername))
                .toList();
    }

    private void addQueueModel(Model model, List<QueueEntry> queueEntries) {
        Map<Long, Patient> patientsById = patientRepository.findAllById(queueEntries.stream()
                        .map(QueueEntry::getPatientId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(Patient::getId, Function.identity()));
        Map<Long, Appointment> appointmentsById = appointmentRepository.findAllById(queueEntries.stream()
                        .map(QueueEntry::getAppointmentId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(Appointment::getId, Function.identity()));
        Map<Long, Consultation> consultationsByAppointmentId = consultationRepository.findAll()
                .stream()
                .filter(consultation -> consultation.getAppointmentId() != null)
                .collect(Collectors.toMap(Consultation::getAppointmentId, Function.identity(), (first, second) -> {
                    OffsetDateTime firstDate = first.getConsultationAt();
                    OffsetDateTime secondDate = second.getConsultationAt();
                    if (firstDate == null) {
                        return second;
                    }
                    if (secondDate == null) {
                        return first;
                    }
                    return firstDate.isAfter(secondDate) ? first : second;
                }));

        model.addAttribute("queueEntries", queueEntries);
        model.addAttribute("patientsById", patientsById);
        model.addAttribute("appointmentsById", appointmentsById);
        model.addAttribute("consultationsByAppointmentId", consultationsByAppointmentId);
        model.addAttribute("waitingCount", queueEntries.stream().filter(entry -> "READY_FOR_DOCTOR".equals(entry.getStatus())).count());
        model.addAttribute("calledCount", queueEntries.stream().filter(entry -> "CALLED".equals(entry.getStatus())).count());
        model.addAttribute("inProgressCount", queueEntries.stream().filter(entry -> "IN_PROGRESS".equals(entry.getStatus())).count());
        model.addAttribute("resultsReadyCount", queueEntries.stream().filter(entry -> "RESULTS_READY".equals(entry.getStatus())).count());
        model.addAttribute("criticalCount", queueEntries.stream()
                .map(entry -> appointmentsById.get(entry.getAppointmentId()))
                .filter(appointment -> appointment != null && "Emergency".equalsIgnoreCase(appointment.getPriority()))
                .count());
    }

    private Consultation activeConsultation(Long queueEntryId) {
        QueueEntry queueEntry = getQueueEntry(queueEntryId);
        requireCurrentDoctorQueue(queueEntry);
        Appointment appointment = getAppointment(queueEntry.getAppointmentId());
        return consultationRepository.findByAppointmentId(appointment.getId())
                .stream()
                .filter(existing -> !"COMPLETED".equals(existing.getStatus()))
                .findFirst()
                .orElseGet(() -> consultationRepository.save(Consultation.builder()
                        .appointmentId(appointment.getId())
                        .patientId(queueEntry.getPatientId())
                        .consultationAt(OffsetDateTime.now())
                        .status("IN_PROGRESS")
                        .build()));
    }

    private Patient getPatient(Long id) {
        return patientRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));
    }

    private Appointment getAppointment(Long id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));
    }

    private QueueEntry getQueueEntry(Long id) {
        return queueEntryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Queue entry not found"));
    }

    private boolean appointmentBelongsToCurrentDoctor(QueueEntry entry, String doctorUsername) {
        if (entry.getAppointmentId() == null) {
            return false;
        }
        return appointmentRepository.findById(entry.getAppointmentId())
                .map(appointment -> Objects.equals(appointment.getProviderUsername(), doctorUsername)
                        || Objects.equals(appointment.getProvider(), authenticationManager.getDisplayName()))
                .orElse(false);
    }

    private List<Appointment> appointmentsForCurrentDoctor() {
        String doctorUsername = currentDoctorUsername();
        String doctorName = authenticationManager.getDisplayName();
        return appointmentRepository.findAll(Sort.by(Sort.Direction.DESC, "scheduledAt"))
                .stream()
                .filter(appointment -> Objects.equals(appointment.getProviderUsername(), doctorUsername)
                        || Objects.equals(appointment.getProvider(), doctorName))
                .toList();
    }

    private void requireCurrentDoctorQueue(QueueEntry queueEntry) {
        if (!appointmentBelongsToCurrentDoctor(queueEntry, currentDoctorUsername())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This patient is booked with another doctor");
        }
        if (!"READY_FOR_DOCTOR".equals(queueEntry.getStatus()) && !"CALLED".equals(queueEntry.getStatus()) && !"IN_PROGRESS".equals(queueEntry.getStatus()) && !"RESULTS_READY".equals(queueEntry.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nurse intake is not complete for this patient");
        }
    }

    private boolean patientBookedWithCurrentDoctor(Long patientId) {
        return appointmentsForCurrentDoctor()
                .stream()
                .anyMatch(appointment -> Objects.equals(appointment.getPatientId(), patientId));
    }

    private String currentDoctorUsername() {
        String username = authenticationManager.getUsername();
        return username.isBlank() ? authenticationManager.getDisplayName() : username;
    }

    private StaffMember currentDoctorStaffMember() {
        String username = currentDoctorUsername();
        String displayName = authenticationManager.getDisplayName();
        return staffMemberRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .filter(member -> "DOCTOR".equalsIgnoreCase(valueOr(member.getStaffRole(), "")))
                .filter(member -> valueOr(member.getEmail(), "").equalsIgnoreCase(username)
                        || valueOr(member.getFullName(), "").equalsIgnoreCase(displayName))
                .findFirst()
                .orElse(null);
    }

    private boolean allText(String... values) {
        return Arrays.stream(values).allMatch(this::hasText);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private List<String> scheduleDays() {
        return Arrays.asList("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday");
    }

    private List<Consultation> latestConsultations(List<Consultation> consultations) {
        return consultations.stream()
                .sorted(Comparator.comparing(Consultation::getConsultationAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private List<Prescription> latestPrescriptions(List<Prescription> prescriptions) {
        return prescriptions.stream()
                .sorted(Comparator.comparing(Prescription::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private List<InvestigationRequest> latestInvestigations(List<InvestigationRequest> investigations) {
        return investigations.stream()
                .sorted(Comparator.comparing(InvestigationRequest::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }
}
