package com.elegax.hms.controller;

import com.elegax.hms.authentication.AuthenticationManager;
import com.elegax.hms.patients.entity.HospitalDepartment;
import com.elegax.hms.patients.entity.StaffMember;
import com.elegax.hms.patients.entity.StaffShift;
import com.elegax.hms.patients.repository.HospitalDepartmentRepository;
import com.elegax.hms.patients.repository.StaffMemberRepository;
import com.elegax.hms.patients.repository.StaffShiftRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
@RequestMapping({"/department-management", "/dept-management"})
public class DepartmentManagementController {

    private final HospitalDepartmentRepository hospitalDepartmentRepository;
    private final StaffMemberRepository staffMemberRepository;
    private final StaffShiftRepository staffShiftRepository;
    private final AuthenticationManager authenticationManager;

    public DepartmentManagementController(HospitalDepartmentRepository hospitalDepartmentRepository,
                                          StaffMemberRepository staffMemberRepository,
                                          StaffShiftRepository staffShiftRepository,
                                          AuthenticationManager authenticationManager) {
        this.hospitalDepartmentRepository = hospitalDepartmentRepository;
        this.staffMemberRepository = staffMemberRepository;
        this.staffShiftRepository = staffShiftRepository;
        this.authenticationManager = authenticationManager;
    }

    @GetMapping({"", "/", "/home", "/dashboard"})
    public String dashboard(Model model, HttpSession session) {
        addBaseModel(model, session);
        return "deptManagement/deptManagementDashboard";
    }

    @GetMapping("/staff")
    public String staff(Model model, HttpSession session) {
        addBaseModel(model, session);
        return "deptManagement/staffManagement";
    }

    @GetMapping("/schedules")
    public String schedules(Model model, HttpSession session) {
        addBaseModel(model, session);
        model.addAttribute("today", LocalDate.now());
        return "deptManagement/schedules";
    }

    @PostMapping("/schedules")
    public String updateSchedule(@RequestParam Long staffMemberId,
                                 @RequestParam String shift,
                                 @RequestParam(required = false) String shiftDate,
                                 @RequestParam(required = false) String startTime,
                                 @RequestParam(required = false) String endTime,
                                 @RequestParam(required = false) String note,
                                 RedirectAttributes redirectAttributes) {
        StaffMember staffMember = managedStaffMember(staffMemberId);
        staffMember.setShift(valueOr(shift, "Unassigned"));
        if (note != null && !note.isBlank()) {
            staffMember.setNotes(appendLine(staffMember.getNotes(), "Schedule update: " + note.trim() + " at " + OffsetDateTime.now()));
        }
        staffMemberRepository.save(staffMember);
        LocalDate date = shiftDate == null || shiftDate.isBlank() ? LocalDate.now().plusDays(1) : LocalDate.parse(shiftDate);
        StaffShift plannedShift = StaffShift.builder()
                .staffMemberId(staffMember.getId())
                .departmentId(resolveDepartmentId(staffMember))
                .shiftDate(date)
                .startTime(parseTime(startTime, defaultStartTime(shift)))
                .endTime(parseTime(endTime, defaultEndTime(shift)))
                .shiftType(valueOr(shift, "Unassigned"))
                .status("ASSIGNED")
                .createdBy(authenticationManager.getUsername())
                .notes(note)
                .build();
        staffShiftRepository.save(plannedShift);
        redirectAttributes.addFlashAttribute("successMessage", "Schedule updated for " + staffMember.getFullName() + ".");
        return "redirect:/department-management/schedules";
    }

    @GetMapping("/workload")
    public String workload(Model model, HttpSession session) {
        addBaseModel(model, session);
        return "deptManagement/workload";
    }

    @GetMapping("/reports")
    public String reports(Model model, HttpSession session) {
        addBaseModel(model, session);
        return "deptManagement/reports";
    }

    @GetMapping({"/resources", "/config"})
    public String resources(Model model, HttpSession session) {
        addBaseModel(model, session);
        return "deptManagement/deptConfig";
    }

    @PostMapping("/resources")
    public String recordResourceUpdate(@RequestParam Long departmentId,
                                       @RequestParam String resourceName,
                                       @RequestParam String status,
                                       @RequestParam(required = false) String note,
                                       RedirectAttributes redirectAttributes) {
        HospitalDepartment department = managedDepartment(departmentId);
        String update = "Resource update: " + valueOr(resourceName, "Department resource")
                + " - " + valueOr(status, "REVIEW")
                + optionalSuffix(note)
                + " at " + OffsetDateTime.now();
        department.setNotes(appendLine(department.getNotes(), update));
        hospitalDepartmentRepository.save(department);
        redirectAttributes.addFlashAttribute("successMessage", "Resource update recorded.");
        return "redirect:/department-management/resources";
    }

    @GetMapping("/escalations")
    public String escalations(Model model, HttpSession session) {
        addBaseModel(model, session);
        return "deptManagement/escalations";
    }

    @PostMapping("/escalations")
    public String escalateIssue(@RequestParam Long departmentId,
                                @RequestParam String priority,
                                @RequestParam String issue,
                                @RequestParam(required = false) String requestedAction,
                                RedirectAttributes redirectAttributes) {
        HospitalDepartment department = managedDepartment(departmentId);
        String escalation = "Escalation: " + valueOr(priority, "MEDIUM")
                + " - " + issue.trim()
                + optionalSuffix(requestedAction)
                + " at " + OffsetDateTime.now();
        department.setNotes(appendLine(department.getNotes(), escalation));
        hospitalDepartmentRepository.save(department);
        redirectAttributes.addFlashAttribute("successMessage", "Operational issue escalated.");
        return "redirect:/department-management/escalations";
    }

    private void addBaseModel(Model model, HttpSession session) {
        session.setAttribute("userRole", "DEPARTMENT_MANAGER");
        List<HospitalDepartment> managedDepartments = managedDepartments();
        List<StaffMember> departmentStaff = departmentStaff(managedDepartments);
        Map<String, Long> roleCounts = departmentStaff.stream()
                .collect(Collectors.groupingBy(member -> valueOr(member.getStaffRole(), "UNASSIGNED"), Collectors.counting()));
        Map<String, Long> shiftCounts = departmentStaff.stream()
                .collect(Collectors.groupingBy(member -> valueOr(member.getShift(), "Unassigned"), Collectors.counting()));

        model.addAttribute("managedDepartments", managedDepartments);
        model.addAttribute("departmentStaff", departmentStaff);
        model.addAttribute("managedNurses", departmentStaff.stream()
                .filter(member -> "NURSE".equalsIgnoreCase(valueOr(member.getStaffRole(), "")))
                .toList());
        model.addAttribute("roleCounts", roleCounts);
        model.addAttribute("shiftCounts", shiftCounts);
        Set<Long> managedStaffIds = departmentStaff.stream().map(StaffMember::getId).collect(Collectors.toSet());
        model.addAttribute("plannedShifts", staffShiftRepository.findAll(Sort.by(Sort.Direction.DESC, "shiftDate"))
                .stream()
                .filter(shift -> managedStaffIds.contains(shift.getStaffMemberId()))
                .toList());
        model.addAttribute("departmentById", managedDepartments.stream().collect(Collectors.toMap(HospitalDepartment::getId, Function.identity())));
        model.addAttribute("managerName", authenticationManager.getDisplayName());
        model.addAttribute("totalStaff", departmentStaff.size());
        model.addAttribute("nurseStaffCount", departmentStaff.stream()
                .filter(member -> "NURSE".equalsIgnoreCase(valueOr(member.getStaffRole(), "")))
                .count());
        model.addAttribute("activeStaff", departmentStaff.stream().filter(member -> "ACTIVE".equalsIgnoreCase(valueOr(member.getStatus(), ""))).count());
        model.addAttribute("unassignedSchedules", departmentStaff.stream().filter(member -> valueOr(member.getShift(), "Unassigned").equalsIgnoreCase("Unassigned")).count());
        model.addAttribute("openEscalations", managedDepartments.stream().filter(department -> valueOr(department.getNotes(), "").contains("Escalation:")).count());
    }

    private List<HospitalDepartment> managedDepartments() {
        String username = valueOr(authenticationManager.getUsername(), "").toLowerCase();
        List<HospitalDepartment> allDepartments = hospitalDepartmentRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));
        List<HospitalDepartment> byAdministrator = allDepartments.stream()
                .filter(department -> department.getAdministrator() != null)
                .filter(department -> valueOr(department.getAdministrator().getEmail(), "").equalsIgnoreCase(username)
                        || valueOr(department.getAdministrator().getFullName(), "").equalsIgnoreCase(authenticationManager.getDisplayName()))
                .toList();
        if (!byAdministrator.isEmpty()) {
            return byAdministrator;
        }

        return staffMemberRepository.findAll().stream()
                .filter(member -> valueOr(member.getEmail(), "").equalsIgnoreCase(username))
                .findFirst()
                .map(member -> allDepartments.stream()
                        .filter(department -> departmentMatches(department.getName(), member.getDepartment()))
                        .toList())
                .filter(departments -> !departments.isEmpty())
                .orElse(List.of());
    }

    private List<StaffMember> departmentStaff(List<HospitalDepartment> departments) {
        if (departments.isEmpty()) {
            return List.of();
        }
        return staffMemberRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .filter(member -> staffBelongsToManagedDepartment(member, departments))
                .toList();
    }

    private boolean staffBelongsToManagedDepartment(StaffMember staffMember, List<HospitalDepartment> managedDepartments) {
        return managedDepartments.stream()
                .anyMatch(department -> departmentMatches(department.getName(), staffMember.getDepartment()));
    }

    private boolean departmentMatches(String managedDepartment, String staffDepartment) {
        String managed = normalizeDepartmentScope(managedDepartment);
        String staff = normalizeDepartmentScope(staffDepartment);
        if (managed.isBlank() || staff.isBlank()) {
            return false;
        }
        if (managed.equals(staff) || staff.contains(managed) || managed.contains(staff)) {
            return true;
        }
        if (isOpdScope(managed)) {
            return isOpdScope(staff);
        }
        if (managed.contains("pediatric") || managed.contains("paediatric")) {
            return staff.contains("pediatric") || staff.contains("paediatric") || staff.contains("child");
        }
        if (managed.contains("maternity") || managed.contains("obstetric") || managed.contains("gynecology") || managed.contains("gynaecology")) {
            return staff.contains("maternity") || staff.contains("obstetric")
                    || staff.contains("gynecology") || staff.contains("gynaecology") || staff.contains("antenatal");
        }
        if (managed.contains("emergency")) {
            return staff.contains("emergency") || staff.contains("triage");
        }
        if (isSurgeryScope(managed)) {
            return isSurgeryScope(staff);
        }
        return false;
    }

    private StaffMember managedStaffMember(Long staffMemberId) {
        StaffMember staffMember = staffMemberRepository.findById(staffMemberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Staff member not found"));
        boolean allowed = departmentStaff(managedDepartments()).stream()
                .anyMatch(member -> member.getId().equals(staffMember.getId()));
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Staff member is outside your department");
        }
        return staffMember;
    }

    private Long resolveDepartmentId(StaffMember staffMember) {
        return managedDepartments().stream()
                .filter(department -> departmentMatches(department.getName(), staffMember.getDepartment()))
                .map(HospitalDepartment::getId)
                .findFirst()
                .orElse(null);
    }

    private LocalTime parseTime(String value, LocalTime fallback) {
        return value == null || value.isBlank() ? fallback : LocalTime.parse(value);
    }

    private LocalTime defaultStartTime(String shift) {
        return switch (valueOr(shift, "").toUpperCase()) {
            case "AFTERNOON" -> LocalTime.of(14, 0);
            case "NIGHT" -> LocalTime.of(20, 0);
            case "ADMINISTRATIVE" -> LocalTime.of(8, 0);
            default -> LocalTime.of(8, 0);
        };
    }

    private LocalTime defaultEndTime(String shift) {
        return switch (valueOr(shift, "").toUpperCase()) {
            case "AFTERNOON" -> LocalTime.of(20, 0);
            case "NIGHT" -> LocalTime.of(8, 0);
            case "ADMINISTRATIVE" -> LocalTime.of(16, 0);
            default -> LocalTime.of(14, 0);
        };
    }

    private HospitalDepartment managedDepartment(Long departmentId) {
        return managedDepartments().stream()
                .filter(department -> department.getId().equals(departmentId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Department is outside your scope"));
    }

    private String appendLine(String currentValue, String line) {
        if (currentValue == null || currentValue.isBlank()) {
            return line;
        }
        return currentValue + System.lineSeparator() + line;
    }

    private String optionalSuffix(String value) {
        return value == null || value.isBlank() ? "" : " - " + value.trim();
    }

    private boolean isOpdScope(String value) {
        return value.contains("opd") || value.contains("outpatient") || value.contains("generalmedicine");
    }

    private boolean isSurgeryScope(String value) {
        return value.contains("surgery") || value.contains("surgical") || value.contains("theatre") || value.contains("operatingroom");
    }

    private String normalizeDepartmentScope(String value) {
        return valueOr(value, "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "")
                .replace("nursing", "")
                .replace("nurse", "")
                .replace("department", "")
                .replace("clinical", "")
                .replace("clinic", "")
                .replace("unit", "");
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
