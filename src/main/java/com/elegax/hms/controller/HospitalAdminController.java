package com.elegax.hms.controller;

import com.elegax.hms.keycloak.KeycloakService;
import com.elegax.hms.patients.entity.HospitalDepartment;
import com.elegax.hms.patients.entity.StaffMember;
import com.elegax.hms.patients.repository.HospitalDepartmentRepository;
import com.elegax.hms.patients.repository.StaffMemberRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/hospital-admin")
public class HospitalAdminController {

    private final StaffMemberRepository staffMemberRepository;
    private final HospitalDepartmentRepository hospitalDepartmentRepository;
    private final KeycloakService keycloakService;

    public HospitalAdminController(StaffMemberRepository staffMemberRepository,
                                   HospitalDepartmentRepository hospitalDepartmentRepository,
                                   KeycloakService keycloakService) {
        this.staffMemberRepository = staffMemberRepository;
        this.hospitalDepartmentRepository = hospitalDepartmentRepository;
        this.keycloakService = keycloakService;
    }

    @GetMapping({"/home", "/dashboard"})
    public String dashboard(Model model, HttpSession session) {
        session.setAttribute("userRole", "HOSPITAL_ADMIN");
        List<StaffMember> staff = staff();
        Map<String, Long> departmentCounts = staff.stream()
                .collect(Collectors.groupingBy(member -> valueOr(member.getDepartment(), "Unassigned"), Collectors.counting()));
        model.addAttribute("staffMembers", staff);
        model.addAttribute("departmentCounts", departmentCounts);
        model.addAttribute("managedDepartments", hospitalDepartmentRepository.findAll(Sort.by(Sort.Direction.ASC, "name")));
        model.addAttribute("totalStaff", staff.size());
        model.addAttribute("activeStaff", staff.stream().filter(member -> "ACTIVE".equalsIgnoreCase(valueOr(member.getStatus(), ""))).count());
        model.addAttribute("pendingUsers", staff.stream().filter(member -> member.getEmail() == null || member.getEmail().isBlank()).count());
        model.addAttribute("departments", departmentCounts.keySet().stream().sorted().toList());
        return "hospitalAdmin/hospitalAdminDashboard";
    }

    @GetMapping({"/staff", "/users"})
    public String userAccess(Model model, HttpSession session) {
        session.setAttribute("userRole", "HOSPITAL_ADMIN");
        List<StaffMember> staff = staff();
        Map<Long, String> accountStates = staff.stream()
                .collect(Collectors.toMap(StaffMember::getId, this::keycloakAccountState));
        model.addAttribute("staffMembers", staff);
        model.addAttribute("totalStaff", staff.size());
        model.addAttribute("activeStaff", staff.stream().filter(member -> "ACTIVE".equalsIgnoreCase(valueOr(member.getStatus(), ""))).count());
        model.addAttribute("pendingUsers", accountStates.values().stream().filter("MISSING"::equals).count());
        model.addAttribute("accountStates", accountStates);
        model.addAttribute("departments", staff.stream().map(StaffMember::getDepartment).filter(department -> department != null && !department.isBlank()).distinct().sorted().toList());
        return "hospitalAdmin/userAccess";
    }

    @GetMapping({"/staff/{id}", "/users/{id}"})
    public String employeeDetail(@PathVariable Long id, Model model, HttpSession session) {
        session.setAttribute("userRole", "HOSPITAL_ADMIN");
        StaffMember staffMember = staffMemberRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Staff record not found"));
        model.addAttribute("staff", staffMember);
        model.addAttribute("accountState", keycloakAccountState(staffMember));
        return "hospitalAdmin/employeeDetail";
    }

    @GetMapping("/departments")
    public String departments(Model model, HttpSession session) {
        session.setAttribute("userRole", "HOSPITAL_ADMIN");
        List<HospitalDepartment> departments = hospitalDepartmentRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));
        model.addAttribute("departments", departments);
        model.addAttribute("totalDepartments", departments.size());
        model.addAttribute("activeDepartments", departments.stream()
                .filter(department -> "ACTIVE".equalsIgnoreCase(valueOr(department.getStatus(), "")))
                .count());
        model.addAttribute("unassignedDepartments", departments.stream()
                .filter(department -> department.getAdministrator() == null)
                .count());
        return "hospitalAdmin/departments";
    }

    @GetMapping("/departments/new")
    public String newDepartment(Model model, HttpSession session) {
        session.setAttribute("userRole", "HOSPITAL_ADMIN");
        model.addAttribute("department", HospitalDepartment.builder().status("ACTIVE").departmentType("Clinical").build());
        model.addAttribute("administratorCandidates", administratorCandidates());
        model.addAttribute("formAction", "/hospital-admin/departments");
        model.addAttribute("formTitle", "Create Department");
        model.addAttribute("submitLabel", "Create Department");
        return "hospitalAdmin/departmentForm";
    }

    @GetMapping("/departments/{id}/edit")
    public String editDepartment(@PathVariable Long id, Model model, HttpSession session) {
        session.setAttribute("userRole", "HOSPITAL_ADMIN");
        HospitalDepartment department = hospitalDepartmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Department not found"));
        model.addAttribute("department", department);
        model.addAttribute("administratorCandidates", administratorCandidates());
        model.addAttribute("formAction", "/hospital-admin/departments/" + id);
        model.addAttribute("formTitle", "Update Department");
        model.addAttribute("submitLabel", "Save Changes");
        return "hospitalAdmin/departmentForm";
    }

    @PostMapping("/departments")
    public String createDepartment(@RequestParam String name,
                                   @RequestParam(required = false) String departmentType,
                                   @RequestParam(required = false) String location,
                                   @RequestParam(required = false) Long administratorId,
                                   @RequestParam(required = false) String notes,
                                   RedirectAttributes redirectAttributes) {
        if (name == null || name.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Department name is required.");
            return "redirect:/hospital-admin/departments";
        }
        if (hospitalDepartmentRepository.findByNameIgnoreCase(name.trim()).isPresent()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Department already exists.");
            return "redirect:/hospital-admin/departments";
        }
        try {
            HospitalDepartment department = HospitalDepartment.builder()
                    .name(name.trim())
                    .departmentType(valueOr(departmentType, "Clinical"))
                    .location(valueOr(location, "Unassigned"))
                    .administrator(resolveAdministrator(administratorId))
                    .notes(notes)
                    .status("ACTIVE")
                    .build();
            hospitalDepartmentRepository.save(department);
            redirectAttributes.addFlashAttribute("successMessage", "Department created.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/hospital-admin/departments";
    }

    @PostMapping("/departments/{id}")
    public String updateDepartment(@PathVariable Long id,
                                   @RequestParam String name,
                                   @RequestParam(required = false) String departmentType,
                                   @RequestParam(required = false) String location,
                                   @RequestParam(required = false) Long administratorId,
                                   @RequestParam(required = false) String status,
                                   @RequestParam(required = false) String notes,
                                   RedirectAttributes redirectAttributes) {
        HospitalDepartment department = hospitalDepartmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Department not found"));
        String normalizedName = name.trim();
        boolean duplicateName = hospitalDepartmentRepository.findByNameIgnoreCase(normalizedName)
                .filter(existing -> !existing.getId().equals(id))
                .isPresent();
        if (duplicateName) {
            redirectAttributes.addFlashAttribute("errorMessage", "Another department already uses that name.");
            return "redirect:/hospital-admin/departments";
        }
        try {
            department.setName(normalizedName);
            department.setDepartmentType(valueOr(departmentType, department.getDepartmentType()));
            department.setLocation(valueOr(location, department.getLocation()));
            department.setAdministrator(resolveAdministrator(administratorId));
            department.setStatus(valueOr(status, "ACTIVE"));
            department.setNotes(notes);
            hospitalDepartmentRepository.save(department);
            redirectAttributes.addFlashAttribute("successMessage", "Department updated.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/hospital-admin/departments";
    }

    @PostMapping("/departments/{id}/disable")
    public String disableDepartment(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        HospitalDepartment department = hospitalDepartmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Department not found"));
        department.setStatus("INACTIVE");
        hospitalDepartmentRepository.save(department);
        redirectAttributes.addFlashAttribute("successMessage", "Department disabled.");
        return "redirect:/hospital-admin/departments";
    }

    @PostMapping("/departments/{id}/enable")
    public String enableDepartment(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        HospitalDepartment department = hospitalDepartmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Department not found"));
        department.setStatus("ACTIVE");
        hospitalDepartmentRepository.save(department);
        redirectAttributes.addFlashAttribute("successMessage", "Department enabled.");
        return "redirect:/hospital-admin/departments";
    }

    @PostMapping("/staff/{id}/keycloak-user")
    public String createKeycloakUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        StaffMember staffMember = staffMemberRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Staff record not found"));
        try {
            KeycloakService.ProvisionedUser provisionedUser = keycloakService.provisionStaffUser(staffMember);
            staffMember.setNotes(appendLine(staffMember.getNotes(), "Keycloak account "
                    + (provisionedUser.existingAccount() ? "refreshed" : "created")
                    + " for " + provisionedUser.username()
                    + " at " + OffsetDateTime.now()
                    + ". Temporary password: 1234"));
            staffMemberRepository.save(staffMember);
            redirectAttributes.addFlashAttribute("successMessage", "Keycloak account "
                    + (provisionedUser.existingAccount() ? "refreshed" : "created")
                    + " for " + provisionedUser.username() + ". Temporary password: 1234");
        } catch (Exception exception) {
            redirectAttributes.addFlashAttribute("errorMessage", "Unable to create Keycloak account: " + exception.getMessage());
        }
        return "redirect:/hospital-admin/staff";
    }

    @PostMapping("/staff/{id}/keycloak-user/disable")
    public String disableKeycloakUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return updateKeycloakUserState(id, false, redirectAttributes);
    }

    @PostMapping("/staff/{id}/keycloak-user/enable")
    public String enableKeycloakUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return updateKeycloakUserState(id, true, redirectAttributes);
    }

    @GetMapping({"/permissions", "/operations"})
    public String permissions(Model model, HttpSession session) {
        session.setAttribute("userRole", "HOSPITAL_ADMIN");
        List<StaffMember> staff = staff();
        model.addAttribute("staffMembers", staff);
        model.addAttribute("roleCounts", staff.stream().collect(Collectors.groupingBy(member -> valueOr(member.getStaffRole(), "UNASSIGNED"), Collectors.counting())));
        model.addAttribute("departmentCounts", staff.stream().collect(Collectors.groupingBy(member -> valueOr(member.getDepartment(), "Unassigned"), Collectors.counting())));
        return "hospitalAdmin/permissions";
    }

    private List<StaffMember> staff() {
        return staffMemberRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private List<StaffMember> administratorCandidates() {
        return staff().stream()
                .filter(member -> !"DISABLED".equalsIgnoreCase(valueOr(member.getStatus(), "")))
                .filter(member -> {
                    String role = valueOr(member.getStaffRole(), "").toUpperCase();
                    String department = valueOr(member.getDepartment(), "").toUpperCase();
                    String jobTitle = valueOr(member.getJobTitle(), "").toUpperCase();
                    return role.contains("HR")
                            || role.contains("ADMIN")
                            || role.contains("DEPARTMENT_MANAGER")
                            || department.contains("ADMIN")
                            || department.contains("HUMAN RESOURCES")
                            || jobTitle.contains("ADMIN")
                            || jobTitle.contains("MANAGER");
                })
                .toList();
    }

    private StaffMember resolveAdministrator(Long administratorId) {
        if (administratorId == null) {
            return null;
        }
        StaffMember administrator = staffMemberRepository.findById(administratorId)
                .orElseThrow(() -> new IllegalArgumentException("Administrator staff record not found"));
        boolean allowed = administratorCandidates().stream().anyMatch(candidate -> candidate.getId().equals(administrator.getId()));
        if (!allowed) {
            throw new IllegalArgumentException("Selected staff member is not eligible to administer a department");
        }
        return administrator;
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String keycloakAccountState(StaffMember staffMember) {
        String notes = valueOr(staffMember.getNotes(), "");
        int createdAt = Math.max(notes.lastIndexOf("Keycloak account created"), notes.lastIndexOf("Keycloak account refreshed"));
        if (createdAt < 0) {
            return "MISSING";
        }
        int disabledAt = notes.lastIndexOf("Keycloak account disabled");
        int enabledAt = notes.lastIndexOf("Keycloak account enabled");
        return disabledAt > enabledAt ? "DISABLED" : "ENABLED";
    }

    private String updateKeycloakUserState(Long id, boolean enabled, RedirectAttributes redirectAttributes) {
        StaffMember staffMember = staffMemberRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Staff record not found"));
        try {
            KeycloakService.ProvisionedUser provisionedUser = keycloakService.setStaffUserEnabled(staffMember, enabled);
            staffMember.setNotes(appendLine(staffMember.getNotes(), "Keycloak account "
                    + (enabled ? "enabled" : "disabled")
                    + " for " + provisionedUser.username()
                    + " at " + OffsetDateTime.now()));
            staffMemberRepository.save(staffMember);
            redirectAttributes.addFlashAttribute("successMessage", "Keycloak account "
                    + (enabled ? "enabled" : "disabled")
                    + " for " + provisionedUser.username() + ".");
        } catch (Exception exception) {
            redirectAttributes.addFlashAttribute("errorMessage", "Unable to "
                    + (enabled ? "enable" : "disable")
                    + " Keycloak account: " + exception.getMessage());
        }
        return "redirect:/hospital-admin/staff";
    }

    private String appendLine(String currentValue, String line) {
        if (currentValue == null || currentValue.isBlank()) {
            return line;
        }
        return currentValue + System.lineSeparator() + line;
    }
}
