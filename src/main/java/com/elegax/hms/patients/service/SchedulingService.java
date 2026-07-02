package com.elegax.hms.patients.service;

import com.elegax.hms.patients.entity.Appointment;
import com.elegax.hms.patients.entity.AppointmentSlot;
import com.elegax.hms.patients.entity.DoctorSchedule;
import com.elegax.hms.patients.entity.StaffMember;
import com.elegax.hms.patients.repository.AppointmentRepository;
import com.elegax.hms.patients.repository.AppointmentSlotRepository;
import com.elegax.hms.patients.repository.DoctorScheduleRepository;
import com.elegax.hms.patients.repository.LeaveRequestRepository;
import com.elegax.hms.patients.repository.PatientRepository;
import com.elegax.hms.patients.repository.StaffMemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Objects;

@Service
public class SchedulingService {

    public static final int DEFAULT_SLOT_MINUTES = 30;

    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final DoctorScheduleRepository doctorScheduleRepository;
    private final AppointmentSlotRepository appointmentSlotRepository;
    private final StaffMemberRepository staffMemberRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    public SchedulingService(PatientRepository patientRepository,
                             AppointmentRepository appointmentRepository,
                             DoctorScheduleRepository doctorScheduleRepository,
                             AppointmentSlotRepository appointmentSlotRepository,
                             StaffMemberRepository staffMemberRepository,
                             LeaveRequestRepository leaveRequestRepository) {
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
        this.doctorScheduleRepository = doctorScheduleRepository;
        this.appointmentSlotRepository = appointmentSlotRepository;
        this.staffMemberRepository = staffMemberRepository;
        this.leaveRequestRepository = leaveRequestRepository;
    }

    public Appointment bookAppointment(Long patientId,
                                       String providerUsername,
                                       OffsetDateTime scheduledAt,
                                       String department,
                                       String visitType,
                                       String priority,
                                       String reason) {
        if (!patientRepository.existsById(patientId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found");
        }
        DoctorSchedule selectedDoctor = requireAvailableProvider(providerUsername, scheduledAt, department);
        AppointmentSlot slot = reserveSlot(selectedDoctor, scheduledAt);
        Appointment appointment = Appointment.builder()
                .patientId(patientId)
                .scheduledAt(scheduledAt)
                .provider(selectedDoctor.getDoctorName())
                .providerUsername(providerUsername)
                .department(valueOr(department, "Outpatient Department"))
                .visitType(valueOr(visitType, "Consultation"))
                .priority(valueOr(priority, "Routine"))
                .reason(reason)
                .status("BOOKED")
                .build();
        appointment = appointmentRepository.save(appointment);
        slot.setAppointmentId(appointment.getId());
        slot.setStatus("BOOKED");
        appointmentSlotRepository.save(slot);
        return appointment;
    }

    private DoctorSchedule requireAvailableProvider(String providerUsername, OffsetDateTime scheduledAt, String department) {
        if (scheduledAt == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Appointment date and time is required");
        }
        if (scheduledAt.toLocalDate().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Appointment date cannot be in the past");
        }
        StaffMember doctor = doctorStaff(providerUsername);
        if (!departmentMatches(doctor.getDepartment(), department)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected doctor does not belong to the selected department");
        }
        if (doctorOnLeave(doctor, scheduledAt.toLocalDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected doctor is on approved leave for this date");
        }
        String selectedDay = scheduledAt.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        LocalTime selectedTime = scheduledAt.toLocalTime();
        return doctorScheduleRepository.findByDoctorUsernameOrderByIdAsc(providerUsername)
                .stream()
                .filter(DoctorSchedule::isAvailable)
                .filter(schedule -> selectedDay.equalsIgnoreCase(schedule.getDayOfWeek()))
                .filter(schedule -> schedule.getStartTime() != null && schedule.getEndTime() != null)
                .filter(schedule -> !selectedTime.isBefore(schedule.getStartTime()) && selectedTime.isBefore(schedule.getEndTime()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected doctor is not available for the selected date and time"));
    }

    private StaffMember doctorStaff(String providerUsername) {
        String username = valueOr(providerUsername, "").trim();
        if (username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Doctor selection is required");
        }
        return staffMemberRepository.findAll()
                .stream()
                .filter(staff -> "DOCTOR".equalsIgnoreCase(valueOr(staff.getStaffRole(), "")))
                .filter(staff -> "ACTIVE".equalsIgnoreCase(valueOr(staff.getStatus(), "ACTIVE")))
                .filter(staff -> valueOr(staff.getEmail(), "").equalsIgnoreCase(username))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected doctor is not an active staff member"));
    }

    private boolean doctorOnLeave(StaffMember doctor, LocalDate date) {
        return leaveRequestRepository.findAll()
                .stream()
                .filter(leave -> Objects.equals(leave.getStaffMemberId(), doctor.getId()))
                .filter(leave -> "APPROVED".equalsIgnoreCase(valueOr(leave.getStatus(), "")))
                .filter(leave -> leave.getStartDate() != null && leave.getEndDate() != null)
                .anyMatch(leave -> !date.isBefore(leave.getStartDate()) && !date.isAfter(leave.getEndDate()));
    }

    private boolean departmentMatches(String doctorDepartment, String selectedDepartment) {
        String doctor = normalize(doctorDepartment);
        String selected = normalize(selectedDepartment);
        return !doctor.isBlank() && !selected.isBlank() && doctor.equals(selected);
    }

    private String normalize(String value) {
        return valueOr(value, "").trim().toLowerCase(Locale.ROOT);
    }

    private AppointmentSlot reserveSlot(DoctorSchedule selectedDoctor, OffsetDateTime scheduledAt) {
        OffsetDateTime slotEnd = scheduledAt.plusMinutes(DEFAULT_SLOT_MINUTES);
        boolean slotBooked = appointmentRepository.findAll()
                .stream()
                .filter(appointment -> appointment.getScheduledAt() != null)
                .filter(appointment -> Objects.equals(appointment.getProviderUsername(), selectedDoctor.getDoctorUsername()))
                .filter(appointment -> !"CANCELLED".equals(appointment.getStatus()) && !"COMPLETED".equals(appointment.getStatus()))
                .anyMatch(appointment -> scheduledAt.isBefore(appointment.getScheduledAt().plusMinutes(DEFAULT_SLOT_MINUTES))
                        && slotEnd.isAfter(appointment.getScheduledAt()));
        if (slotBooked) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected doctor is already booked for this time");
        }
        AppointmentSlot slot = appointmentSlotRepository.findByProviderUsernameAndSlotStart(selectedDoctor.getDoctorUsername(), scheduledAt)
                .orElseGet(() -> AppointmentSlot.builder()
                        .providerUsername(selectedDoctor.getDoctorUsername())
                        .providerName(selectedDoctor.getDoctorName())
                        .slotStart(scheduledAt)
                        .slotEnd(slotEnd)
                        .build());
        if ("BOOKED".equalsIgnoreCase(valueOr(slot.getStatus(), ""))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected doctor is already booked for this time");
        }
        slot.setStatus("HELD");
        return appointmentSlotRepository.save(slot);
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
