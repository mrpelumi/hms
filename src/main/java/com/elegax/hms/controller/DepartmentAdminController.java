package com.elegax.hms.controller;

import com.elegax.hms.patients.entity.StaffMember;
import com.elegax.hms.patients.repository.StaffMemberRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/department-admin")
public class DepartmentAdminController {

    private final StaffMemberRepository staffMemberRepository;

    public DepartmentAdminController(StaffMemberRepository staffMemberRepository) {
        this.staffMemberRepository = staffMemberRepository;
    }

    @GetMapping({"/home", "/dashboard"})
    public String dashboard(Model model, HttpSession session) {
        session.setAttribute("userRole", "DEPARTMENT_ADMIN");
        List<StaffMember> staff = staff();
        Map<String, Long> departmentCounts = staff.stream()
                .collect(Collectors.groupingBy(member -> valueOr(member.getDepartment(), "Unassigned"), Collectors.counting()));
        model.addAttribute("staffMembers", staff);
        model.addAttribute("departmentCounts", departmentCounts);
        model.addAttribute("totalStaff", staff.size());
        model.addAttribute("activeStaff", staff.stream().filter(member -> "ACTIVE".equalsIgnoreCase(valueOr(member.getStatus(), ""))).count());
        model.addAttribute("pendingUsers", staff.stream().filter(member -> member.getEmail() == null || member.getEmail().isBlank()).count());
        model.addAttribute("departments", departmentCounts.keySet().stream().sorted().toList());
        return "departmentAdmin/deptAdminDashboard";
    }

    @GetMapping({"/staff", "/users"})
    public String userAccess(Model model, HttpSession session) {
        session.setAttribute("userRole", "DEPARTMENT_ADMIN");
        List<StaffMember> staff = staff();
        model.addAttribute("staffMembers", staff);
        model.addAttribute("totalStaff", staff.size());
        model.addAttribute("activeStaff", staff.stream().filter(member -> "ACTIVE".equalsIgnoreCase(valueOr(member.getStatus(), ""))).count());
        model.addAttribute("pendingUsers", staff.stream().filter(member -> member.getEmail() == null || member.getEmail().isBlank()).count());
        model.addAttribute("departments", staff.stream().map(StaffMember::getDepartment).filter(department -> department != null && !department.isBlank()).distinct().sorted().toList());
        return "departmentAdmin/userAccess";
    }

    @GetMapping({"/permissions", "/operations"})
    public String permissions(Model model, HttpSession session) {
        session.setAttribute("userRole", "DEPARTMENT_ADMIN");
        List<StaffMember> staff = staff();
        model.addAttribute("staffMembers", staff);
        model.addAttribute("roleCounts", staff.stream().collect(Collectors.groupingBy(member -> valueOr(member.getStaffRole(), "UNASSIGNED"), Collectors.counting())));
        model.addAttribute("departmentCounts", staff.stream().collect(Collectors.groupingBy(member -> valueOr(member.getDepartment(), "Unassigned"), Collectors.counting())));
        return "departmentAdmin/permissions";
    }

    private List<StaffMember> staff() {
        return staffMemberRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
