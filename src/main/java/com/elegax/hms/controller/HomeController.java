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
    public String root(HttpSession session) {
        if (authenticationManager.isDoctor()) {
            session.setAttribute("userRole", "DOCTOR");
            return "redirect:/doctor/dashboard";
        }

        if (authenticationManager.isNurse()) {
            session.setAttribute("userRole", "NURSE");
            return "redirect:/nurse/dashboard";
        }

        if (authenticationManager.isReceptionist()) {
            session.setAttribute("userRole", "RECEPTIONIST");
            return "redirect:/reception/home";
        }

        session.removeAttribute("userRole");
        return "redirect:/reception/home";
    }

}
