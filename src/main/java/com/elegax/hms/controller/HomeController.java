package com.elegax.hms.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String root() {
        // After successful login Keycloak will redirect to this path; forward users to the patient directory home
        return "redirect:/reception/home";
    }

}

