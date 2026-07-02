package com.elegax.hms.controller;

import com.elegax.hms.authentication.AuthenticationManager;
import com.elegax.hms.patients.service.AttendanceService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final AuthenticationManager authenticationManager;
    private final AttendanceService attendanceService;

    public HomeController(AuthenticationManager authenticationManager,
                          AttendanceService attendanceService) {
        this.authenticationManager = authenticationManager;
        this.attendanceService = attendanceService;
    }

    @GetMapping("/")
    public String landing() {
        return "landingPage";
    }

    @GetMapping("/home")
    public String home(HttpSession session) {
        if (authenticationManager.isDoctor()) {
            recordStaffLoginAttendance();
            session.setAttribute("userRole", "DOCTOR");
            return "redirect:/doctor/dashboard";
        }

        if (authenticationManager.isNurse()) {
            recordStaffLoginAttendance();
            session.setAttribute("userRole", "NURSE");
            return "redirect:/nurse/dashboard";
        }

        if (authenticationManager.isLaboratory()) {
            recordStaffLoginAttendance();
            session.setAttribute("userRole", "LABORATORY");
            return "redirect:/laboratory/dashboard";
        }

        if (authenticationManager.isRadiology()) {
            recordStaffLoginAttendance();
            session.setAttribute("userRole", "RADIOLOGY");
            return "redirect:/radiology/dashboard";
        }

        if (authenticationManager.isPharmacy()) {
            recordStaffLoginAttendance();
            session.setAttribute("userRole", "PHARMACY");
            return "redirect:/pharmacy/dispense";
        }

        if (authenticationManager.isBilling()) {
            recordStaffLoginAttendance();
            session.setAttribute("userRole", "BILLING");
            return "redirect:/billing/home";
        }

        if (authenticationManager.isPatient()) {
            session.setAttribute("userRole", "PATIENT");
            return "redirect:/patient/dashboard";
        }

        if (authenticationManager.isHr()) {
            recordStaffLoginAttendance();
            session.setAttribute("userRole", "HR");
            return "redirect:/hr/dashboard";
        }

        if (authenticationManager.isDepartmentManager()) {
            recordStaffLoginAttendance();
            session.setAttribute("userRole", "DEPARTMENT_MANAGER");
            return "redirect:/department-management/dashboard";
        }

        if (authenticationManager.isHospitalAdmin()) {
            recordStaffLoginAttendance();
            session.setAttribute("userRole", "HOSPITAL_ADMIN");
            return "redirect:/hospital-admin/dashboard";
        }

        if (authenticationManager.isReceptionist()) {
            recordStaffLoginAttendance();
            session.setAttribute("userRole", "RECEPTIONIST");
            return "redirect:/reception/home";
        }

        session.removeAttribute("userRole");
        return "redirect:/?error=missing-role";
    }

    private void recordStaffLoginAttendance() {
        attendanceService.recordLoginAttendance(authenticationManager.getUsername(), authenticationManager.getDisplayName());
    }

}
