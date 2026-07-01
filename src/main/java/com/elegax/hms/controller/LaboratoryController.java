package com.elegax.hms.controller;

import com.elegax.hms.patients.entity.InvestigationRequest;
import com.elegax.hms.patients.entity.Patient;
import com.elegax.hms.patients.entity.Consultation;
import com.elegax.hms.patients.repository.ConsultationRepository;
import com.elegax.hms.patients.repository.InvestigationRequestRepository;
import com.elegax.hms.patients.repository.PatientRepository;
import com.elegax.hms.patients.repository.QueueEntryRepository;
import com.elegax.hms.patients.service.BillingWorkflowService;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/laboratory")
public class LaboratoryController {

    private final InvestigationRequestRepository investigationRequestRepository;
    private final PatientRepository patientRepository;
    private final ConsultationRepository consultationRepository;
    private final QueueEntryRepository queueEntryRepository;
    private final BillingWorkflowService billingWorkflowService;

    public LaboratoryController(InvestigationRequestRepository investigationRequestRepository,
                                PatientRepository patientRepository,
                                ConsultationRepository consultationRepository,
                                QueueEntryRepository queueEntryRepository,
                                BillingWorkflowService billingWorkflowService) {
        this.investigationRequestRepository = investigationRequestRepository;
        this.patientRepository = patientRepository;
        this.consultationRepository = consultationRepository;
        this.queueEntryRepository = queueEntryRepository;
        this.billingWorkflowService = billingWorkflowService;
    }

    @GetMapping({"/dashboard", "/home"})
    public String dashboard(Model model, HttpSession session) {
        session.setAttribute("userRole", "LABORATORY");
        var requests = investigationRequestRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .filter(request -> !isRadiology(request))
                .toList();
        addModel(model, requests);
        return "laboratory/labDashboard";
    }

    @PostMapping("/requests/{id}/sample")
    public String collectSample(@PathVariable Long id) {
        InvestigationRequest request = investigationRequestRepository.findById(id).orElseThrow();
        request.setStatus("SAMPLE_COLLECTED");
        investigationRequestRepository.save(request);
        return "redirect:/laboratory/dashboard?sampleCollected";
    }

    @PostMapping("/requests/{id}/complete")
    public String complete(@PathVariable Long id,
                           @RequestParam(required = false) String resultSummary) {
        InvestigationRequest request = investigationRequestRepository.findById(id).orElseThrow();
        request.setStatus("RESULT_READY");
        request.setResultReadyAt(OffsetDateTime.now());
        request.setResultSummary(resultSummary);
        investigationRequestRepository.save(request);
        billingWorkflowService.createForInvestigation(request);
        releaseDoctorReviewIfReady(request);
        return "redirect:/laboratory/dashboard?completed";
    }

    @PostMapping("/requests/{id}/expected")
    public String expected(@PathVariable Long id,
                           @RequestParam String expectedResultAt,
                           @RequestParam(required = false) String patientUpdateNote) {
        InvestigationRequest request = investigationRequestRepository.findById(id).orElseThrow();
        request.setStatus("RESULT_PENDING");
        request.setExpectedResultAt(LocalDateTime.parse(expectedResultAt).atZone(ZoneId.systemDefault()).toOffsetDateTime());
        request.setPatientUpdateNote(patientUpdateNote);
        investigationRequestRepository.save(request);
        return "redirect:/laboratory/dashboard?expected";
    }

    private void addModel(Model model, java.util.List<InvestigationRequest> requests) {
        Map<Long, Patient> patientsById = patientRepository.findAllById(requests.stream()
                        .map(InvestigationRequest::getPatientId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(Patient::getId, Function.identity()));
        model.addAttribute("requests", requests);
        model.addAttribute("patientsById", patientsById);
        model.addAttribute("pendingCount", requests.stream().filter(request -> "REQUESTED".equals(request.getStatus()) || "RESULT_PENDING".equals(request.getStatus())).count());
        model.addAttribute("collectedCount", requests.stream().filter(request -> "SAMPLE_COLLECTED".equals(request.getStatus())).count());
        model.addAttribute("completedCount", requests.stream().filter(request -> "RESULT_READY".equals(request.getStatus()) || "COMPLETED".equals(request.getStatus())).count());
        model.addAttribute("urgentCount", requests.stream().filter(request -> "Urgent".equalsIgnoreCase(request.getPriority()) || "Emergency".equalsIgnoreCase(request.getPriority())).count());
    }

    private boolean isRadiology(InvestigationRequest request) {
        String type = request.getRequestType() == null ? "" : request.getRequestType();
        return type.equalsIgnoreCase("Radiology") || type.equalsIgnoreCase("Imaging");
    }

    private void releaseDoctorReviewIfReady(InvestigationRequest request) {
        if (request.getConsultationId() == null) {
            return;
        }
        boolean allReady = investigationRequestRepository.findByConsultationId(request.getConsultationId())
                .stream()
                .allMatch(investigation -> "RESULT_READY".equals(investigation.getStatus()) || "COMPLETED".equals(investigation.getStatus()));
        if (!allReady) {
            return;
        }
        Consultation consultation = consultationRepository.findById(request.getConsultationId()).orElse(null);
        if (consultation == null) {
            return;
        }
        consultation.setStatus("RESULTS_READY");
        consultationRepository.save(consultation);
        queueEntryRepository.findByAppointmentId(consultation.getAppointmentId())
                .ifPresent(queueEntry -> {
                    queueEntry.setStatus("RESULTS_READY");
                    queueEntryRepository.save(queueEntry);
                });
    }
}
