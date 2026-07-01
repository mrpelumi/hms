package com.elegax.hms.controller;

import com.elegax.hms.patients.entity.LeaveRequest;
import com.elegax.hms.patients.entity.StaffAttendance;
import com.elegax.hms.patients.entity.StaffMember;
import com.elegax.hms.patients.repository.LeaveRequestRepository;
import com.elegax.hms.patients.repository.StaffAttendanceRepository;
import com.elegax.hms.patients.repository.StaffMemberRepository;
import jakarta.servlet.http.HttpSession;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/hr")
public class HrController {

    private final StaffMemberRepository staffMemberRepository;
    private final StaffAttendanceRepository staffAttendanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    public HrController(StaffMemberRepository staffMemberRepository,
                        StaffAttendanceRepository staffAttendanceRepository,
                        LeaveRequestRepository leaveRequestRepository) {
        this.staffMemberRepository = staffMemberRepository;
        this.staffAttendanceRepository = staffAttendanceRepository;
        this.leaveRequestRepository = leaveRequestRepository;
    }

    @GetMapping({"/home", "/dashboard"})
    public String dashboard(Model model, HttpSession session) {
        session.setAttribute("userRole", "HR");
        List<StaffMember> staff = staff();
        List<StaffAttendance> todayAttendance = staffAttendanceRepository.findByAttendanceDate(LocalDate.now());
        List<LeaveRequest> leaves = leaves();
        model.addAttribute("totalStaff", staff.size());
        model.addAttribute("activeStaff", staff.stream().filter(member -> "ACTIVE".equals(member.getStatus())).count());
        model.addAttribute("onDutyCount", todayAttendance.stream().filter(attendance -> "PRESENT".equals(attendance.getStatus()) || "LATE".equals(attendance.getStatus())).count());
        model.addAttribute("pendingLeaveCount", leaves.stream().filter(leave -> "PENDING".equals(leave.getStatus())).count());
        model.addAttribute("credentialDueCount", staff.stream().filter(this::credentialDueSoon).count());
        model.addAttribute("departmentCounts", staff.stream().collect(Collectors.groupingBy(member -> valueOr(member.getDepartment(), "Unassigned"), Collectors.counting())));
        model.addAttribute("recentStaff", staff.stream().limit(6).toList());
        model.addAttribute("recentLeaves", leaves.stream().limit(5).toList());
        model.addAttribute("staffById", staffById());
        return "hr/hrDashboard";
    }

    @GetMapping("/staff")
    public String staffDirectory(Model model, HttpSession session) {
        session.setAttribute("userRole", "HR");
        List<StaffMember> staff = staff();
        model.addAttribute("staffMembers", staff);
        model.addAttribute("totalStaff", staff.size());
        model.addAttribute("activeStaff", staff.stream().filter(member -> "ACTIVE".equals(member.getStatus())).count());
        model.addAttribute("clinicalStaff", staff.stream().filter(member -> List.of("DOCTOR", "NURSE", "LABORATORY", "RADIOLOGY", "PHARMACY").contains(valueOr(member.getStaffRole(), "").toUpperCase())).count());
        model.addAttribute("disabledStaff", staff.stream().filter(member -> "DISABLED".equals(member.getStatus())).count());
        return "hr/staffDirectory";
    }

    @GetMapping({"/staff/new", "/createStaff"})
    public String newStaff(Model model, HttpSession session) {
        session.setAttribute("userRole", "HR");
        model.addAttribute("staffMember", new StaffMember());
        model.addAttribute("formAction", "/hr/staff");
        model.addAttribute("formTitle", "Create Staff Record");
        return "hr/createStaff";
    }

    @PostMapping("/staff")
    public String createStaff(@ModelAttribute StaffMember staffMember,
                              @RequestParam(required = false) MultipartFile profilePicture,
                              @RequestParam(required = false) List<MultipartFile> registrationDocuments) {
        validateStaffCategories(staffMember);
        staffMember.setId(null);
        staffMember.setStaffId(nextStaffId());
        if (staffMember.getStatus() == null || staffMember.getStatus().isBlank()) {
            staffMember.setStatus("ACTIVE");
        }
        if (staffMember.getShift() == null || staffMember.getShift().isBlank()) {
            staffMember.setShift("Unassigned");
        }
        staffMember.setNotes(appendUploadSummary(staffMember.getNotes(), profilePicture, registrationDocuments));
        staffMemberRepository.save(staffMember);
        return "redirect:/hr/staff?created";
    }

    @GetMapping("/staff/{id}/edit")
    public String editStaff(@PathVariable Long id, Model model, HttpSession session) {
        session.setAttribute("userRole", "HR");
        model.addAttribute("staffMember", staffMember(id));
        model.addAttribute("formAction", "/hr/staff/" + id);
        model.addAttribute("formTitle", "Update Staff Record");
        return "hr/staffForm";
    }

    @PostMapping("/staff/{id}")
    public String updateStaff(@PathVariable Long id, @ModelAttribute StaffMember form) {
        validateStaffCategories(form);
        StaffMember staffMember = staffMember(id);
        staffMember.setFullName(form.getFullName());
        staffMember.setEmail(form.getEmail());
        staffMember.setPhoneNumber(form.getPhoneNumber());
        staffMember.setDepartment(form.getDepartment());
        staffMember.setJobTitle(form.getJobTitle());
        staffMember.setStaffRole(form.getStaffRole());
        staffMember.setEmploymentType(form.getEmploymentType());
        staffMember.setShift(form.getShift());
        staffMember.setHireDate(form.getHireDate());
        staffMember.setCredentialExpiryDate(form.getCredentialExpiryDate());
        staffMember.setBaseSalary(form.getBaseSalary());
        staffMember.setAllowances(form.getAllowances());
        staffMember.setDeductions(form.getDeductions());
        staffMember.setStatus(form.getStatus());
        staffMember.setNotes(form.getNotes());
        staffMemberRepository.save(staffMember);
        return "redirect:/hr/staff?updated";
    }

    @PostMapping("/staff/{id}/disable")
    public String disableStaff(@PathVariable Long id) {
        StaffMember staffMember = staffMember(id);
        staffMember.setStatus("DISABLED");
        staffMemberRepository.save(staffMember);
        return "redirect:/hr/staff?disabled";
    }

    @GetMapping("/attendance")
    public String attendance(@RequestParam(required = false) String date, Model model, HttpSession session) {
        session.setAttribute("userRole", "HR");
        LocalDate selectedDate = date == null || date.isBlank() ? LocalDate.now() : LocalDate.parse(date);
        List<StaffAttendance> attendance = staffAttendanceRepository.findByAttendanceDate(selectedDate);
        model.addAttribute("selectedDate", selectedDate);
        model.addAttribute("attendanceRecords", attendance);
        model.addAttribute("staffMembers", staff());
        model.addAttribute("staffById", staffById());
        model.addAttribute("onDutyCount", attendance.stream().filter(record -> "PRESENT".equals(record.getStatus()) || "LATE".equals(record.getStatus())).count());
        model.addAttribute("lateCount", attendance.stream().filter(record -> "LATE".equals(record.getStatus())).count());
        model.addAttribute("absentCount", attendance.stream().filter(record -> "ABSENT".equals(record.getStatus())).count());
        model.addAttribute("leaveCount", leaveRequestRepository.findAll().stream().filter(leave -> "APPROVED".equals(leave.getStatus()) && !selectedDate.isBefore(leave.getStartDate()) && !selectedDate.isAfter(leave.getEndDate())).count());
        return "hr/attendance";
    }

    @PostMapping("/attendance")
    public String markAttendance(@RequestParam Long staffMemberId,
                                 @RequestParam String attendanceDate,
                                 @RequestParam String scheduledShift,
                                 @RequestParam(required = false) String clockIn,
                                 @RequestParam(required = false) String clockOut,
                                 @RequestParam String status,
                                 @RequestParam(required = false) String notes) {
        StaffAttendance attendance = StaffAttendance.builder()
                .staffMemberId(staffMemberId)
                .attendanceDate(LocalDate.parse(attendanceDate))
                .scheduledShift(scheduledShift)
                .clockIn(parseTime(clockIn))
                .clockOut(parseTime(clockOut))
                .status(status)
                .notes(notes)
                .build();
        staffAttendanceRepository.save(attendance);
        return "redirect:/hr/attendance?date=" + attendanceDate + "&recorded";
    }

    @GetMapping("/leave")
    public String leave(Model model, HttpSession session) {
        session.setAttribute("userRole", "HR");
        List<LeaveRequest> leaves = leaves();
        model.addAttribute("leaveRequests", leaves);
        model.addAttribute("staffMembers", staff());
        model.addAttribute("staffById", staffById());
        model.addAttribute("pendingCount", leaves.stream().filter(leave -> "PENDING".equals(leave.getStatus())).count());
        model.addAttribute("approvedCount", leaves.stream().filter(leave -> "APPROVED".equals(leave.getStatus())).count());
        model.addAttribute("activeLeaveCount", leaves.stream().filter(leave -> "APPROVED".equals(leave.getStatus()) && !LocalDate.now().isBefore(leave.getStartDate()) && !LocalDate.now().isAfter(leave.getEndDate())).count());
        return "hr/leaveManagement";
    }

    @PostMapping("/leave")
    public String createLeave(@RequestParam Long staffMemberId,
                              @RequestParam String leaveType,
                              @RequestParam String startDate,
                              @RequestParam String endDate,
                              @RequestParam(required = false) String reason) {
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        if (end.isBefore(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Leave end date cannot be before start date");
        }
        leaveRequestRepository.save(LeaveRequest.builder()
                .staffMemberId(staffMemberId)
                .leaveType(leaveType)
                .startDate(start)
                .endDate(end)
                .reason(reason)
                .status("PENDING")
                .build());
        return "redirect:/hr/leave?submitted";
    }

    @PostMapping("/leave/{id}/decision")
    public String decideLeave(@PathVariable Long id,
                              @RequestParam String status,
                              @RequestParam(required = false) String hrComment) {
        LeaveRequest leave = leaveRequestRepository.findById(id).orElseThrow();
        leave.setStatus(status);
        leave.setHrComment(hrComment);
        leave.setDecidedAt(OffsetDateTime.now());
        leaveRequestRepository.save(leave);
        return "redirect:/hr/leave?updated";
    }

    @GetMapping("/payroll")
    public String payroll(Model model, HttpSession session) {
        session.setAttribute("userRole", "HR");
        List<StaffMember> staff = staff().stream().filter(member -> !"DISABLED".equals(member.getStatus())).toList();
        model.addAttribute("staffMembers", staff);
        model.addAttribute("grossTotal", staff.stream().map(this::grossPay).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("deductionTotal", staff.stream().map(member -> amount(member.getDeductions())).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("netTotal", staff.stream().map(this::netPay).reduce(BigDecimal.ZERO, BigDecimal::add));
        return "hr/payroll";
    }

    @GetMapping("/payroll/export")
    public ResponseEntity<String> exportPayroll() {
        StringBuilder csv = new StringBuilder("Staff ID,Name,Department,Role,Base Salary,Allowances,Deductions,Net Pay\n");
        staff().stream()
                .filter(member -> !"DISABLED".equals(member.getStatus()))
                .forEach(member -> csv.append(csv(member.getStaffId())).append(',')
                        .append(csv(member.getFullName())).append(',')
                        .append(csv(member.getDepartment())).append(',')
                        .append(csv(member.getJobTitle())).append(',')
                        .append(amount(member.getBaseSalary())).append(',')
                        .append(amount(member.getAllowances())).append(',')
                        .append(amount(member.getDeductions())).append(',')
                        .append(netPay(member)).append('\n'));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=payroll-export.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv.toString());
    }

    private List<StaffMember> staff() {
        return staffMemberRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private List<LeaveRequest> leaves() {
        return leaveRequestRepository.findAll(Sort.by(Sort.Direction.DESC, "requestedAt"));
    }

    private Map<Long, StaffMember> staffById() {
        return staffMemberRepository.findAll().stream().collect(Collectors.toMap(StaffMember::getId, Function.identity()));
    }

    private StaffMember staffMember(Long id) {
        return staffMemberRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Staff member not found"));
    }

    private String nextStaffId() {
        return "STF-" + String.format("%04d", staffMemberRepository.count() + 1);
    }

    private boolean credentialDueSoon(StaffMember staffMember) {
        return staffMember.getCredentialExpiryDate() != null
                && !staffMember.getCredentialExpiryDate().isBefore(LocalDate.now())
                && ChronoUnit.DAYS.between(LocalDate.now(), staffMember.getCredentialExpiryDate()) <= 30;
    }

    private LocalTime parseTime(String time) {
        return time == null || time.isBlank() ? null : LocalTime.parse(time);
    }

    private BigDecimal grossPay(StaffMember staffMember) {
        return amount(staffMember.getBaseSalary()).add(amount(staffMember.getAllowances()));
    }

    private BigDecimal netPay(StaffMember staffMember) {
        return grossPay(staffMember).subtract(amount(staffMember.getDeductions()));
    }

    private BigDecimal amount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String csv(String value) {
        String safe = Objects.toString(value, "").replace("\"", "\"\"");
        return "\"" + safe + "\"";
    }

    private void validateStaffCategories(StaffMember staffMember) {
        if (staffMember.getStaffRole() == null || staffMember.getStaffRole().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Application access role is required");
        }
        if (staffMember.getDepartment() == null || staffMember.getDepartment().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Department or clinical unit is required");
        }
    }

    private String appendUploadSummary(String notes, MultipartFile profilePicture, List<MultipartFile> registrationDocuments) {
        StringBuilder summary = new StringBuilder(notes == null ? "" : notes.trim());
        if (profilePicture != null && !profilePicture.isEmpty()) {
            appendLine(summary, "Profile picture submitted: " + profilePicture.getOriginalFilename());
        }
        if (registrationDocuments != null) {
            registrationDocuments.stream()
                    .filter(file -> file != null && !file.isEmpty())
                    .map(MultipartFile::getOriginalFilename)
                    .filter(Objects::nonNull)
                    .forEach(fileName -> appendLine(summary, "Registration document submitted: " + fileName));
        }
        return summary.toString();
    }

    private void appendLine(StringBuilder builder, String line) {
        if (!builder.isEmpty()) {
            builder.append(System.lineSeparator());
        }
        builder.append(line);
    }
}
