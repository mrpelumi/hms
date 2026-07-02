package com.elegax.hms.patients.service;

import com.elegax.hms.patients.entity.StaffAttendance;
import com.elegax.hms.patients.entity.DoctorSchedule;
import com.elegax.hms.patients.entity.StaffMember;
import com.elegax.hms.patients.entity.StaffShift;
import com.elegax.hms.patients.repository.DoctorScheduleRepository;
import com.elegax.hms.patients.repository.StaffAttendanceRepository;
import com.elegax.hms.patients.repository.StaffMemberRepository;
import com.elegax.hms.patients.repository.StaffShiftRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Optional;

@Service
public class AttendanceService {

    private static final int GRACE_MINUTES = 15;

    private final StaffMemberRepository staffMemberRepository;
    private final StaffAttendanceRepository staffAttendanceRepository;
    private final StaffShiftRepository staffShiftRepository;
    private final DoctorScheduleRepository doctorScheduleRepository;

    public AttendanceService(StaffMemberRepository staffMemberRepository,
                             StaffAttendanceRepository staffAttendanceRepository,
                             StaffShiftRepository staffShiftRepository,
                             DoctorScheduleRepository doctorScheduleRepository) {
        this.staffMemberRepository = staffMemberRepository;
        this.staffAttendanceRepository = staffAttendanceRepository;
        this.staffShiftRepository = staffShiftRepository;
        this.doctorScheduleRepository = doctorScheduleRepository;
    }

    public Optional<StaffAttendance> recordLoginAttendance(String username, String displayName) {
        return findStaff(username, displayName)
                .filter(staff -> !"DISABLED".equalsIgnoreCase(valueOr(staff.getStatus(), "")))
                .map(staff -> recordAttendance(staff, LocalDate.now(), LocalTime.now(), null, null, "LOGIN", "First login attendance capture"));
    }

    public StaffAttendance recordManualAttendance(Long staffMemberId,
                                                  LocalDate attendanceDate,
                                                  String scheduledShift,
                                                  LocalTime clockIn,
                                                  LocalTime clockOut,
                                                  String status,
                                                  String notes) {
        StaffMember staff = staffMemberRepository.findById(staffMemberId).orElseThrow();
        return recordAttendance(staff, attendanceDate, clockIn, clockOut, status, "MANUAL", notes, scheduledShift);
    }

    private StaffAttendance recordAttendance(StaffMember staff,
                                             LocalDate attendanceDate,
                                             LocalTime clockIn,
                                             LocalTime clockOut,
                                             String explicitStatus,
                                             String source,
                                             String notes) {
        return recordAttendance(staff, attendanceDate, clockIn, clockOut, explicitStatus, source, notes, null);
    }

    private StaffAttendance recordAttendance(StaffMember staff,
                                             LocalDate attendanceDate,
                                             LocalTime clockIn,
                                             LocalTime clockOut,
                                             String explicitStatus,
                                             String source,
                                             String notes,
                                             String scheduledShiftOverride) {
        LocalDate selectedDate = attendanceDate == null ? LocalDate.now() : attendanceDate;
        Optional<StaffShift> shift = staffShiftRepository.findFirstByStaffMemberIdAndShiftDateOrderByStartTimeAsc(staff.getId(), selectedDate);
        Optional<DoctorSchedule> doctorSchedule = shift.isPresent() ? Optional.empty() : doctorScheduleFor(staff, selectedDate);
        StaffAttendance attendance = staffAttendanceRepository.findByStaffMemberIdAndAttendanceDate(staff.getId(), selectedDate)
                .orElseGet(() -> StaffAttendance.builder()
                        .staffMemberId(staff.getId())
                        .attendanceDate(selectedDate)
                        .build());

        attendance.setShiftId(shift.map(StaffShift::getId).orElse(null));
        attendance.setScheduledShift(valueOr(scheduledShiftOverride,
                shift.map(StaffShift::getShiftType)
                        .orElse(doctorSchedule.map(this::doctorScheduleLabel).orElse(valueOr(staff.getShift(), "Unassigned")))));
        if (attendance.getClockIn() == null && clockIn != null) {
            attendance.setClockIn(clockIn);
        } else if (attendance.getClockIn() == null && "LOGIN".equals(source)) {
            attendance.setClockIn(LocalTime.now());
        }
        if (clockOut != null) {
            attendance.setClockOut(clockOut);
        }
        attendance.setSource(source);
        attendance.setStatus(explicitStatus == null || explicitStatus.isBlank()
                ? deriveStatus(attendance.getClockIn(), shift, doctorSchedule)
                : explicitStatus);
        attendance.setNotes(appendLine(attendance.getNotes(), notes));
        return staffAttendanceRepository.save(attendance);
    }

    private String deriveStatus(LocalTime clockIn, Optional<StaffShift> shift, Optional<DoctorSchedule> doctorSchedule) {
        if (clockIn == null) {
            return "ABSENT";
        }
        LocalTime startTime = shift.map(StaffShift::getStartTime)
                .orElseGet(() -> doctorSchedule.map(DoctorSchedule::getStartTime).orElse(null));
        if (startTime == null) {
            return "UNSCHEDULED_PRESENT";
        }
        return clockIn.isAfter(startTime.plusMinutes(GRACE_MINUTES)) ? "LATE" : "PRESENT";
    }

    private Optional<DoctorSchedule> doctorScheduleFor(StaffMember staff, LocalDate selectedDate) {
        if (!"DOCTOR".equalsIgnoreCase(valueOr(staff.getStaffRole(), "")) || staff.getEmail() == null || staff.getEmail().isBlank()) {
            return Optional.empty();
        }
        String day = selectedDate.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        return doctorScheduleRepository.findByDoctorUsernameAndDayOfWeek(staff.getEmail(), day)
                .filter(DoctorSchedule::isAvailable);
    }

    private String doctorScheduleLabel(DoctorSchedule schedule) {
        if (schedule.getStartTime() == null || schedule.getEndTime() == null) {
            return "Doctor Schedule";
        }
        return "Doctor Schedule " + schedule.getStartTime() + "-" + schedule.getEndTime();
    }

    private Optional<StaffMember> findStaff(String username, String displayName) {
        return staffMemberRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .filter(staff -> valueOr(staff.getEmail(), "").equalsIgnoreCase(valueOr(username, ""))
                        || valueOr(staff.getFullName(), "").equalsIgnoreCase(valueOr(displayName, "")))
                .findFirst();
    }

    private String appendLine(String currentValue, String line) {
        if (line == null || line.isBlank()) {
            return currentValue;
        }
        if (currentValue == null || currentValue.isBlank()) {
            return line;
        }
        return currentValue + System.lineSeparator() + line;
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
