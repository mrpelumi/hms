package com.elegax.hms.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ReceptionController {

	@GetMapping("/reception/home")
	public String home(Model model) {
		model.addAttribute("pageTitle", "Patient Management");
		// return the Thymeleaf template under templates/patient_registration/home.html
		return "reception/home";
	}

	@GetMapping("/reception/registration")
	public String registration(Model model) {
		model.addAttribute("pageTitle", "Patient Management");
		// return only the Thymeleaf fragment named 'registration' from the reception template
		return "reception/registration";
	}

	@GetMapping("/reception/appointment")
	public String appointment(Model model) {
		model.addAttribute("pageTitle", "Appointment Booking");
		return "reception/appointment";
	}

	@GetMapping("/reception/queue")
	public String queue(Model model) {
		model.addAttribute("pageTitle", "Queue Management");
		return "reception/queue";
	}

}


