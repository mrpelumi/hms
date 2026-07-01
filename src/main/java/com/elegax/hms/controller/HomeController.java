package com.elegax.hms.controller;

import com.elegax.hms.authentication.AuthenticationManager;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final AuthenticationManager authenticationManager;

    public HomeController(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @GetMapping("/")
    public String landing() {
        return "landingPage";
    }

    @GetMapping("/home")
    public String home(HttpSession session) {
        if (authenticationManager.isDoctor()) {
            session.setAttribute("userRole", "DOCTOR");
            return "redirect:/doctor/dashboard";
        }

        if (authenticationManager.isNurse()) {
            session.setAttribute("userRole", "NURSE");
            return "redirect:/nurse/dashboard";
        }

        if (authenticationManager.isLaboratory()) {
            session.setAttribute("userRole", "LABORATORY");
            return "redirect:/laboratory/dashboard";
        }

        if (authenticationManager.isRadiology()) {
            session.setAttribute("userRole", "RADIOLOGY");
            return "redirect:/radiology/dashboard";
        }

        if (authenticationManager.isPharmacy()) {
            session.setAttribute("userRole", "PHARMACY");
            return "redirect:/pharmacy/dispense";
        }

        if (authenticationManager.isBilling()) {
            session.setAttribute("userRole", "BILLING");
            return "redirect:/billing/home";
        }

        if (authenticationManager.isHr()) {
            session.setAttribute("userRole", "HR");
            return "redirect:/hr/dashboard";
        }

        if (authenticationManager.isDepartmentAdmin()) {
            session.setAttribute("userRole", "DEPARTMENT_ADMIN");
            return "redirect:/department-admin/dashboard";
        }

        if (authenticationManager.isReceptionist()) {
            session.setAttribute("userRole", "RECEPTIONIST");
            return "redirect:/reception/home";
        }

        session.removeAttribute("userRole");
        return "redirect:/login?error=missing-role";
    }

}
