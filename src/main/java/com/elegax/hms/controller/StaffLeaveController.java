package com.elegax.hms.controller;

import com.elegax.hms.patients.entity.StaffMember;
import com.elegax.hms.patients.service.LeaveRequestService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class StaffLeaveController {

    private final LeaveRequestService leaveRequestService;

    public StaffLeaveController(LeaveRequestService leaveRequestService) {
        this.leaveRequestService = leaveRequestService;
    }

    @GetMapping("/doctor/leave")
    public String doctorLeave(Model model, HttpSession session) {
        return leavePage(model, session, "DOCTOR", "Doctor", "/doctor/dashboard", "/doctor/leave");
    }

    @PostMapping("/doctor/leave")
    public String submitDoctorLeave(@RequestParam String leaveType,
                                    @RequestParam String startDate,
                                    @RequestParam String endDate,
                                    @RequestParam(required = false) String reason,
                                    RedirectAttributes redirectAttributes) {
        return submitLeave(leaveType, startDate, endDate, reason, "/doctor/leave", redirectAttributes);
    }

    @GetMapping("/nurse/leave")
    public String nurseLeave(Model model, HttpSession session) {
        return leavePage(model, session, "NURSE", "Nurse", "/nurse/dashboard", "/nurse/leave");
    }

    @PostMapping("/nurse/leave")
    public String submitNurseLeave(@RequestParam String leaveType,
                                   @RequestParam String startDate,
                                   @RequestParam String endDate,
                                   @RequestParam(required = false) String reason,
                                   RedirectAttributes redirectAttributes) {
        return submitLeave(leaveType, startDate, endDate, reason, "/nurse/leave", redirectAttributes);
    }

    @GetMapping("/laboratory/leave")
    public String laboratoryLeave(Model model, HttpSession session) {
        return leavePage(model, session, "LABORATORY", "Laboratory", "/laboratory/dashboard", "/laboratory/leave");
    }

    @PostMapping("/laboratory/leave")
    public String submitLaboratoryLeave(@RequestParam String leaveType,
                                        @RequestParam String startDate,
                                        @RequestParam String endDate,
                                        @RequestParam(required = false) String reason,
                                        RedirectAttributes redirectAttributes) {
        return submitLeave(leaveType, startDate, endDate, reason, "/laboratory/leave", redirectAttributes);
    }

    @GetMapping("/radiology/leave")
    public String radiologyLeave(Model model, HttpSession session) {
        return leavePage(model, session, "RADIOLOGY", "Radiology", "/radiology/dashboard", "/radiology/leave");
    }

    @PostMapping("/radiology/leave")
    public String submitRadiologyLeave(@RequestParam String leaveType,
                                       @RequestParam String startDate,
                                       @RequestParam String endDate,
                                       @RequestParam(required = false) String reason,
                                       RedirectAttributes redirectAttributes) {
        return submitLeave(leaveType, startDate, endDate, reason, "/radiology/leave", redirectAttributes);
    }

    @GetMapping("/pharmacy/leave")
    public String pharmacyLeave(Model model, HttpSession session) {
        return leavePage(model, session, "PHARMACY", "Pharmacy", "/pharmacy/dispense", "/pharmacy/leave");
    }

    @PostMapping("/pharmacy/leave")
    public String submitPharmacyLeave(@RequestParam String leaveType,
                                      @RequestParam String startDate,
                                      @RequestParam String endDate,
                                      @RequestParam(required = false) String reason,
                                      RedirectAttributes redirectAttributes) {
        return submitLeave(leaveType, startDate, endDate, reason, "/pharmacy/leave", redirectAttributes);
    }

    private String leavePage(Model model,
                             HttpSession session,
                             String userRole,
                             String moduleName,
                             String moduleHome,
                             String formAction) {
        session.setAttribute("userRole", userRole);
        StaffMember staffMember = leaveRequestService.currentStaffMember().orElse(null);
        var leaveRequests = leaveRequestService.currentStaffRequests();
        model.addAttribute("staffMember", staffMember);
        model.addAttribute("moduleName", moduleName);
        model.addAttribute("moduleHome", moduleHome);
        model.addAttribute("formAction", formAction);
        model.addAttribute("leaveRequests", leaveRequests);
        model.addAttribute("pendingCount", leaveRequests.stream().filter(leave -> "PENDING".equals(leave.getStatus())).count());
        model.addAttribute("approvedCount", leaveRequests.stream().filter(leave -> "APPROVED".equals(leave.getStatus())).count());
        model.addAttribute("rejectedCount", leaveRequests.stream().filter(leave -> "REJECTED".equals(leave.getStatus())).count());
        return "staff/leaveRequests";
    }

    private String submitLeave(String leaveType,
                               String startDate,
                               String endDate,
                               String reason,
                               String redirectPath,
                               RedirectAttributes redirectAttributes) {
        try {
            leaveRequestService.submitCurrentStaffRequest(leaveType, startDate, endDate, reason);
            redirectAttributes.addFlashAttribute("successMessage", "Leave request submitted to HR.");
        } catch (ResponseStatusException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getReason());
        }
        return "redirect:" + redirectPath;
    }
}
