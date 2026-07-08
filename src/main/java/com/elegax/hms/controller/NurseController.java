package com.elegax.hms.controller;

import com.elegax.hms.authentication.AuthenticationManager;
import com.elegax.hms.patients.entity.Appointment;
import com.elegax.hms.patients.entity.Patient;
import com.elegax.hms.patients.entity.QueueEntry;
import com.elegax.hms.patients.entity.StaffMember;
import com.elegax.hms.patients.repository.AppointmentRepository;
import com.elegax.hms.patients.repository.PatientRepository;
import com.elegax.hms.patients.repository.QueueEntryRepository;
import com.elegax.hms.patients.repository.StaffMemberRepository;
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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/nurse")
public class NurseController {

    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final QueueEntryRepository queueEntryRepository;
    private final StaffMemberRepository staffMemberRepository;
    private final AuthenticationManager authenticationManager;

    public NurseController(PatientRepository patientRepository,
                           AppointmentRepository appointmentRepository,
                           QueueEntryRepository queueEntryRepository,
                           StaffMemberRepository staffMemberRepository,
                           AuthenticationManager authenticationManager) {
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
        this.queueEntryRepository = queueEntryRepository;
        this.staffMemberRepository = staffMemberRepository;
        this.authenticationManager = authenticationManager;
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
        requireNurseCanAccess(queueEntry);
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
        requireNurseCanAccess(queueEntry);
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
        Optional<StaffMember> nurse = currentNurseStaffMember();
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
        model.addAttribute("readyForDoctorCount", nurseQueueEntriesByStatus("READY_FOR_DOCTOR").size());
        model.addAttribute("completedVitalsToday", scopedQueueEntries().stream()
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
        model.addAttribute("shiftLabel", nurse.map(StaffMember::getShift).filter(shift -> !shift.isBlank()).orElse("Unassigned Shift"));
        model.addAttribute("stationName", nurse.map(StaffMember::getDepartment).filter(department -> !department.isBlank()).orElse("Clinical Intake"));
        model.addAttribute("nurseUnit", nurse.map(StaffMember::getDepartment).orElse("No nurse unit assigned"));
        model.addAttribute("nurseScopeNotice", nurse.isPresent()
                ? nurseScopeNotice(nurse.get().getDepartment())
                : "No staff profile was found for this nurse account, so the patient queue is hidden.");
    }

    private List<QueueEntry> nurseQueueEntries() {
        return nurseQueueEntriesByStatus("WAITING_FOR_NURSE");
    }

    private List<QueueEntry> nurseQueueEntriesByStatus(String status) {
        Optional<StaffMember> nurse = currentNurseStaffMember();
        if (nurse.isEmpty()) {
            return List.of();
        }
        return queueEntryRepository.findAll(Sort.by(Sort.Direction.DESC, "queuedAt"))
                .stream()
                .filter(entry -> status.equals(entry.getStatus()))
                .filter(entry -> nurseCanAccess(nurse.get(), entry))
                .toList();
    }

    private List<QueueEntry> scopedQueueEntries() {
        Optional<StaffMember> nurse = currentNurseStaffMember();
        if (nurse.isEmpty()) {
            return List.of();
        }
        return queueEntryRepository.findAll(Sort.by(Sort.Direction.DESC, "queuedAt"))
                .stream()
                .filter(entry -> nurseCanAccess(nurse.get(), entry))
                .toList();
    }

    private Optional<StaffMember> currentNurseStaffMember() {
        String username = valueOr(authenticationManager.getUsername(), "").trim();
        String displayName = valueOr(authenticationManager.getDisplayName(), "").trim();
        return staffMemberRepository.findAll().stream()
                .filter(member -> "NURSE".equalsIgnoreCase(valueOr(member.getStaffRole(), "")))
                .filter(member -> "ACTIVE".equalsIgnoreCase(valueOr(member.getStatus(), "ACTIVE")))
                .filter(member -> valueOr(member.getEmail(), "").equalsIgnoreCase(username)
                        || valueOr(member.getStaffId(), "").equalsIgnoreCase(username)
                        || valueOr(member.getFullName(), "").equalsIgnoreCase(displayName))
                .findFirst();
    }

    private void requireNurseCanAccess(QueueEntry queueEntry) {
        StaffMember nurse = currentNurseStaffMember()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "No nurse staff profile is linked to this account"));
        if (!nurseCanAccess(nurse, queueEntry)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This patient is outside your assigned nursing unit");
        }
    }

    private boolean nurseCanAccess(StaffMember nurse, QueueEntry queueEntry) {
        Appointment appointment = queueEntry.getAppointmentId() == null
                ? null
                : appointmentRepository.findById(queueEntry.getAppointmentId()).orElse(null);
        return appointmentMatchesNurseUnit(nurse.getDepartment(), appointment);
    }

    private String nurseScopeNotice(String nurseDepartment) {
        return isAllDepartmentScope(nurseDepartment)
                ? "Showing patients across all clinical departments."
                : "Showing patients for " + valueOr(nurseDepartment, "your assigned nursing unit") + ".";
    }

    private boolean appointmentMatchesNurseUnit(String nurseDepartment, Appointment appointment) {
        if (isAllDepartmentScope(nurseDepartment)) {
            return true;
        }
        String unit = normalizeScope(nurseDepartment);
        if (unit.isBlank()) {
            return false;
        }
        if (appointment == null) {
            return unit.contains("general") || unit.contains("opd") || unit.contains("outpatient");
        }

        String appointmentScope = normalizeScope(String.join(" ",
                valueOr(appointment.getDepartment(), ""),
                valueOr(appointment.getVisitType(), ""),
                valueOr(appointment.getReason(), ""),
                valueOr(appointment.getPriority(), "")));

        if (isEmergencyScope(unit)) {
            return isEmergencyScope(appointmentScope);
        }
        if (isSurgeryScope(unit)) {
            return isSurgeryScope(appointmentScope);
        }
        if (isOpdScope(unit)) {
            return appointmentScope.isBlank() || isOpdScope(appointmentScope) || appointmentScope.contains("generalmedicine");
        }
        if (unit.contains("triage")) {
            return isOpdScope(appointmentScope) || isEmergencyScope(appointmentScope);
        }
        if (unit.contains("pediatric") || unit.contains("paediatric")) {
            return appointmentScope.contains("pediatric") || appointmentScope.contains("paediatric") || appointmentScope.contains("child");
        }
        if (unit.contains("maternity") || unit.contains("obstetric") || unit.contains("gynecology") || unit.contains("gynaecology")) {
            return appointmentScope.contains("maternity") || appointmentScope.contains("obstetric")
                    || appointmentScope.contains("gynecology") || appointmentScope.contains("gynaecology")
                    || appointmentScope.contains("antenatal");
        }
        if (unit.contains("ward") || unit.contains("inpatient")) {
            return appointmentScope.contains("ward") || appointmentScope.contains("inpatient");
        }
        if (unit.contains("immunization") || unit.contains("publichealth")) {
            return appointmentScope.contains("immunization") || appointmentScope.contains("publichealth");
        }
        return !appointmentScope.isBlank() && (appointmentScope.contains(unit) || unit.contains(appointmentScope));
    }

    private boolean isEmergencyScope(String value) {
        return value.contains("emergency") || value.contains("urgent");
    }

    private boolean isSurgeryScope(String value) {
        return value.contains("surgery") || value.contains("surgical") || value.contains("theatre") || value.contains("operatingroom");
    }

    private boolean isAllDepartmentScope(String value) {
        String scope = valueOr(value, "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
        return scope.contains("allclinicaldepartments")
                || scope.contains("alldepartments")
                || scope.contains("crossfunctional")
                || scope.contains("crosscoverage")
                || scope.contains("floatpool");
    }

    private boolean isOpdScope(String value) {
        return value.contains("opd") || value.contains("outpatient") || value.contains("general");
    }

    private String normalizeScope(String value) {
        return valueOr(value, "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "")
                .replace("nursing", "")
                .replace("nurse", "")
                .replace("department", "")
                .replace("clinic", "")
                .replace("clinical", "")
                .replace("unit", "");
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

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
