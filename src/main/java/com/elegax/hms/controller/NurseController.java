package com.elegax.hms.controller;

import com.elegax.hms.patients.entity.Appointment;
import com.elegax.hms.patients.entity.Patient;
import com.elegax.hms.patients.entity.QueueEntry;
import com.elegax.hms.patients.repository.AppointmentRepository;
import com.elegax.hms.patients.repository.PatientRepository;
import com.elegax.hms.patients.repository.QueueEntryRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/nurse")
public class NurseController {

    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final QueueEntryRepository queueEntryRepository;

    public NurseController(PatientRepository patientRepository,
                           AppointmentRepository appointmentRepository,
                           QueueEntryRepository queueEntryRepository) {
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
        this.queueEntryRepository = queueEntryRepository;
    }

    @GetMapping({"/home", "/dashboard"})
    public String dashboard(Model model) {
        addQueueModel(model);
        return "nurse/nurseDashboard";
    }

    @GetMapping("/queue")
    public String queue(Model model) {
        addQueueModel(model);
        return "nurse/patientQueue";
    }

    @GetMapping("/vitals/{queueEntryId}")
    public String vitals(@PathVariable Long queueEntryId, Model model) {
        QueueEntry queueEntry = getQueueEntry(queueEntryId);
        if (!"WAITING_FOR_NURSE".equals(queueEntry.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This patient is not waiting for nurse intake");
        }

        Appointment appointment = getAppointment(queueEntry.getAppointmentId());
        Patient patient = getPatient(queueEntry.getPatientId());
        model.addAttribute("queueEntry", queueEntry);
        model.addAttribute("appointment", appointment);
        model.addAttribute("patient", patient);
        return "nurse/vitals";
    }

    @PostMapping("/vitals/{queueEntryId}")
    public String saveVitals(@PathVariable Long queueEntryId,
                             @RequestParam(required = false) String temperature,
                             @RequestParam(required = false) String systolic,
                             @RequestParam(required = false) String diastolic,
                             @RequestParam(required = false) String bloodPressure,
                             @RequestParam(required = false) String pulseRate,
                             @RequestParam(required = false) String respiratoryRate,
                             @RequestParam(required = false) String oxygenSaturation,
                             @RequestParam(required = false) String painScore,
                             @RequestParam(required = false) String weight,
                             @RequestParam(required = false) String height,
                             @RequestParam(required = false) String bmi,
                             @RequestParam(required = false) String chiefComplaint,
                             @RequestParam(required = false) String nurseNotes) {
        QueueEntry queueEntry = getQueueEntry(queueEntryId);
        if (!"WAITING_FOR_NURSE".equals(queueEntry.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This patient is not waiting for nurse intake");
        }
        queueEntry.setTemperature(temperature);
        queueEntry.setBloodPressure(resolveBloodPressure(bloodPressure, systolic, diastolic));
        queueEntry.setPulseRate(pulseRate);
        queueEntry.setRespiratoryRate(respiratoryRate);
        queueEntry.setOxygenSaturation(oxygenSaturation);
        queueEntry.setPainScore(painScore);
        queueEntry.setWeight(weight);
        queueEntry.setHeight(height);
        queueEntry.setBmi(bmi);
        queueEntry.setChiefComplaint(chiefComplaint);
        queueEntry.setNurseNotes(nurseNotes);
        queueEntry.setVitalsCapturedAt(OffsetDateTime.now());
        queueEntry.setStatus("READY_FOR_DOCTOR");
        queueEntryRepository.save(queueEntry);

        return "redirect:/nurse/queue?ready";
    }

    private void addQueueModel(Model model) {
        List<QueueEntry> queueEntries = nurseQueueEntries();
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
        model.addAttribute("waitingForNurseCount", queueEntries.stream().filter(entry -> "WAITING_FOR_NURSE".equals(entry.getStatus())).count());
        model.addAttribute("readyForDoctorCount", queueEntryRepository.countByStatus("READY_FOR_DOCTOR"));
        model.addAttribute("completedVitalsToday", queueEntryRepository.findAll().stream()
                .filter(entry -> entry.getVitalsCapturedAt() != null && entry.getVitalsCapturedAt().toLocalDate().equals(OffsetDateTime.now().toLocalDate()))
                .count());
        model.addAttribute("criticalCount", queueEntries.stream()
                .map(entry -> appointmentsById.get(entry.getAppointmentId()))
                .filter(appointment -> appointment != null && "Emergency".equalsIgnoreCase(appointment.getPriority()))
                .count());
        model.addAttribute("urgentQueueEntries", queueEntries.stream()
                .filter(entry -> {
                    Appointment appointment = appointmentsById.get(entry.getAppointmentId());
                    return appointment != null && ("Emergency".equalsIgnoreCase(appointment.getPriority()) || "Urgent".equalsIgnoreCase(appointment.getPriority()));
                })
                .toList());
        model.addAttribute("activeDoctorsCount", appointmentsById.values()
                .stream()
                .map(Appointment::getProvider)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet())
                .size());
        model.addAttribute("averageWaitMinutes", averageWaitMinutes(queueEntries));
        model.addAttribute("intakeLoadLabel", queueEntries.size() >= 8 ? "High Load" : "Normal Load");
        model.addAttribute("shiftLabel", "Morning Shift");
        model.addAttribute("stationName", "Clinical Intake");
    }

    private List<QueueEntry> nurseQueueEntries() {
        return queueEntryRepository.findAll(Sort.by(Sort.Direction.ASC, "queuedAt"))
                .stream()
                .filter(entry -> "WAITING_FOR_NURSE".equals(entry.getStatus()))
                .toList();
    }

    private String resolveBloodPressure(String bloodPressure, String systolic, String diastolic) {
        if (bloodPressure != null && !bloodPressure.isBlank()) {
            return bloodPressure;
        }
        if (systolic != null && !systolic.isBlank() && diastolic != null && !diastolic.isBlank()) {
            return systolic + "/" + diastolic;
        }
        return null;
    }

    private long averageWaitMinutes(List<QueueEntry> queueEntries) {
        List<Long> waits = queueEntries.stream()
                .filter(entry -> entry.getQueuedAt() != null)
                .map(entry -> Duration.between(entry.getQueuedAt(), OffsetDateTime.now()).toMinutes())
                .toList();
        if (waits.isEmpty()) {
            return 0;
        }
        return Math.round(waits.stream().mapToLong(Long::longValue).average().orElse(0));
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
}
