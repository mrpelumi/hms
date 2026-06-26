package com.elegax.hms.controller;

import com.elegax.hms.authentication.AuthenticationManager;
import com.elegax.hms.patients.entity.*;
import com.elegax.hms.patients.repository.*;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.LocalTime;
import java.util.Arrays;
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
    private final AuthenticationManager authenticationManager;

    public DoctorController(PatientRepository patientRepository,
                            AppointmentRepository appointmentRepository,
                            QueueEntryRepository queueEntryRepository,
                            ConsultationRepository consultationRepository,
                            PrescriptionRepository prescriptionRepository,
                            InvestigationRequestRepository investigationRequestRepository,
                            DoctorScheduleRepository doctorScheduleRepository,
                            AuthenticationManager authenticationManager) {
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
        this.queueEntryRepository = queueEntryRepository;
        this.consultationRepository = consultationRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.investigationRequestRepository = investigationRequestRepository;
        this.doctorScheduleRepository = doctorScheduleRepository;
        this.authenticationManager = authenticationManager;
    }

    @GetMapping({"/home", "/dashboard"})
    public String dashboard(Model model) {
        List<QueueEntry> queueEntries = activeQueueEntries();
        addQueueModel(model, queueEntries);
        model.addAttribute("completedToday", appointmentsForCurrentDoctor().stream()
                .filter(appointment -> "COMPLETED".equals(appointment.getStatus()))
                .count());
        return "doctor/doctorDashboard";
    }

    @GetMapping("/queue")
    public String queue(Model model) {
        addQueueModel(model, activeQueueEntries());
        return "doctor/patientQueue";
    }

    @PostMapping("/queue/{queueEntryId}/call")
    public String callPatient(@PathVariable Long queueEntryId) {
        QueueEntry queueEntry = getQueueEntry(queueEntryId);
        requireCurrentDoctorQueue(queueEntry);
        queueEntry.setStatus("CALLED");
        queueEntryRepository.save(queueEntry);
        return "redirect:/doctor/patients/" + queueEntry.getPatientId() + "/history?queueEntryId=" + queueEntry.getId();
    }

    @GetMapping("/patients/{patientId}/history")
    public String patientHistory(@PathVariable Long patientId,
                                 @RequestParam(required = false) Long queueEntryId,
                                 Model model) {
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
        model.addAttribute("consultations", consultationRepository.findByPatientId(patientId));
        model.addAttribute("prescriptions", prescriptionRepository.findByPatientId(patientId));
        model.addAttribute("investigations", investigationRequestRepository.findByPatientId(patientId));
        return "doctor/patientMedicalHistory";
    }

    @GetMapping("/consultation/{queueEntryId}")
    public String consultation(@PathVariable Long queueEntryId, Model model) {
        QueueEntry queueEntry = getQueueEntry(queueEntryId);
        requireCurrentDoctorQueue(queueEntry);
        Appointment appointment = getAppointment(queueEntry.getAppointmentId());
        Patient patient = getPatient(queueEntry.getPatientId());
        Consultation consultation = consultationRepository.findByAppointmentId(appointment.getId())
                .stream()
                .findFirst()
                .orElseGet(() -> consultationRepository.save(Consultation.builder()
                        .appointmentId(appointment.getId())
                        .patientId(patient.getId())
                        .consultationAt(OffsetDateTime.now())
                        .status("IN_PROGRESS")
                        .build()));

        queueEntry.setStatus("CALLED");
        queueEntryRepository.save(queueEntry);

        model.addAttribute("queueEntry", queueEntry);
        model.addAttribute("appointment", appointment);
        model.addAttribute("patient", patient);
        model.addAttribute("consultation", consultation);
        model.addAttribute("prescriptions", prescriptionRepository.findByConsultationId(consultation.getId()));
        model.addAttribute("investigations", investigationRequestRepository.findByConsultationId(consultation.getId()));
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

    @PostMapping("/consultation/{queueEntryId}/complete")
    public String completeConsultation(@PathVariable Long queueEntryId) {
        QueueEntry queueEntry = getQueueEntry(queueEntryId);
        requireCurrentDoctorQueue(queueEntry);
        Appointment appointment = getAppointment(queueEntry.getAppointmentId());
        Consultation consultation = activeConsultation(queueEntryId);
        consultation.setStatus("COMPLETED");
        consultationRepository.save(consultation);

        queueEntry.setStatus("SERVED");
        queueEntry.setServedAt(OffsetDateTime.now());
        queueEntryRepository.save(queueEntry);

        appointment.setStatus("COMPLETED");
        appointmentRepository.save(appointment);
        return "redirect:/doctor/queue?completed";
    }

    @GetMapping("/patients/{patientId}/prescriptions")
    public String prescriptions(@PathVariable Long patientId, Model model) {
        if (!patientBookedWithCurrentDoctor(patientId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This patient is booked with another doctor");
        }
        Patient patient = getPatient(patientId);
        model.addAttribute("patient", patient);
        model.addAttribute("prescriptions", prescriptionRepository.findByPatientId(patientId));
        return "doctor/patientPrescription";
    }

    @GetMapping("/prescriptions")
    public String prescriptionsIndex(Model model) {
        List<Long> patientIds = appointmentsForCurrentDoctor().stream()
                .map(Appointment::getPatientId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        model.addAttribute("patient", null);
        model.addAttribute("prescriptions", prescriptionRepository.findAll()
                .stream()
                .filter(prescription -> patientIds.contains(prescription.getPatientId()))
                .toList());
        return "doctor/patientPrescription";
    }

    @GetMapping("/medical-history")
    public String medicalHistoryIndex(Model model) {
        List<Long> patientIds = appointmentsForCurrentDoctor().stream()
                .map(Appointment::getPatientId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<Patient> patients = patientRepository.findAllById(patientIds)
                .stream()
                .sorted((first, second) -> String.valueOf(first.getFullName()).compareToIgnoreCase(String.valueOf(second.getFullName())))
                .toList();
        Patient patient = patients.isEmpty() ? null : patients.get(0);
        model.addAttribute("patient", patient);
        model.addAttribute("consultations", patient == null ? List.of() : consultationRepository.findByPatientId(patient.getId()));
        model.addAttribute("prescriptions", patient == null ? List.of() : prescriptionRepository.findByPatientId(patient.getId()));
        model.addAttribute("investigations", patient == null ? List.of() : investigationRequestRepository.findByPatientId(patient.getId()));
        return "doctor/patientMedicalHistory";
    }

    @GetMapping("/schedule")
    public String schedule(Model model) {
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
        return queueEntryRepository.findAll(Sort.by(Sort.Direction.ASC, "queuedAt"))
                .stream()
                .filter(entry -> !"SERVED".equals(entry.getStatus()))
                .filter(entry -> "READY_FOR_DOCTOR".equals(entry.getStatus()) || "CALLED".equals(entry.getStatus()))
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

        model.addAttribute("queueEntries", queueEntries);
        model.addAttribute("patientsById", patientsById);
        model.addAttribute("appointmentsById", appointmentsById);
        model.addAttribute("waitingCount", queueEntries.stream().filter(entry -> "READY_FOR_DOCTOR".equals(entry.getStatus())).count());
        model.addAttribute("calledCount", queueEntries.stream().filter(entry -> "CALLED".equals(entry.getStatus())).count());
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
        return appointmentRepository.findAll()
                .stream()
                .filter(appointment -> Objects.equals(appointment.getProviderUsername(), doctorUsername)
                        || Objects.equals(appointment.getProvider(), doctorName))
                .toList();
    }

    private void requireCurrentDoctorQueue(QueueEntry queueEntry) {
        if (!appointmentBelongsToCurrentDoctor(queueEntry, currentDoctorUsername())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This patient is booked with another doctor");
        }
        if (!"READY_FOR_DOCTOR".equals(queueEntry.getStatus()) && !"CALLED".equals(queueEntry.getStatus())) {
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

    private List<String> scheduleDays() {
        return Arrays.asList("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday");
    }
}
