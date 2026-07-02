package com.elegax.hms.controller;

import com.elegax.hms.authentication.AuthenticationManager;
import com.elegax.hms.patients.entity.InvestigationRequest;
import com.elegax.hms.patients.entity.Patient;
import com.elegax.hms.patients.entity.Consultation;
import com.elegax.hms.patients.repository.ConsultationRepository;
import com.elegax.hms.patients.repository.InvestigationRequestRepository;
import com.elegax.hms.patients.repository.PatientRepository;
import com.elegax.hms.patients.repository.QueueEntryRepository;
import com.elegax.hms.patients.service.BillingWorkflowService;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
    private final AuthenticationManager authenticationManager;

    public LaboratoryController(InvestigationRequestRepository investigationRequestRepository,
                                PatientRepository patientRepository,
                                ConsultationRepository consultationRepository,
                                QueueEntryRepository queueEntryRepository,
                                BillingWorkflowService billingWorkflowService,
                                AuthenticationManager authenticationManager) {
        this.investigationRequestRepository = investigationRequestRepository;
        this.patientRepository = patientRepository;
        this.consultationRepository = consultationRepository;
        this.queueEntryRepository = queueEntryRepository;
        this.billingWorkflowService = billingWorkflowService;
        this.authenticationManager = authenticationManager;
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
    public String collectSample(@PathVariable Long id,
                                @RequestParam String sampleType,
                                @RequestParam(required = false) String collectionLocation,
                                @RequestParam(required = false) String containerType,
                                @RequestParam(required = false) String sampleCondition,
                                @RequestParam(required = false) String sampleNotes) {
        InvestigationRequest request = labRequest(id);
        request.setSampleType(sampleType);
        request.setCollectionLocation(collectionLocation);
        request.setContainerType(containerType);
        request.setSampleCondition(valueOr(sampleCondition, "Acceptable"));
        request.setSampleNotes(sampleNotes);
        request.setCollectedBy(authenticationManager.getDisplayName());
        request.setCollectedAt(OffsetDateTime.now());
        request.setStatus("SAMPLE_COLLECTED");
        investigationRequestRepository.save(request);
        return "redirect:/laboratory/dashboard?sampleCollected";
    }

    @GetMapping("/requests/{id}/sample")
    public String samplePage(@PathVariable Long id, Model model, HttpSession session) {
        session.setAttribute("userRole", "LABORATORY");
        addRequestModel(model, labRequest(id));
        return "laboratory/sampleCollection";
    }

    @PostMapping("/requests/{id}/result")
    public String enterResult(@PathVariable Long id,
                              @RequestParam(required = false) List<String> parameterName,
                              @RequestParam(required = false) List<String> parameterValue,
                              @RequestParam(required = false) List<String> parameterUnit,
                              @RequestParam(required = false) List<String> referenceRange,
                              @RequestParam(required = false) List<String> resultFlag,
                              @RequestParam(required = false) String resultSummary) {
        InvestigationRequest request = labRequest(id);
        if (request.getCollectedAt() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Collect sample before entering results");
        }
        request.setStatus("RESULT_ENTERED");
        request.setPerformedBy(authenticationManager.getDisplayName());
        request.setPerformedAt(OffsetDateTime.now());
        request.setResultSummary(resultSummary);
        request.setResultParameters(buildResultParameters(parameterName, parameterValue, parameterUnit, referenceRange, resultFlag));
        investigationRequestRepository.save(request);
        return "redirect:/laboratory/dashboard?resultEntered";
    }

    @GetMapping("/requests/{id}/result")
    public String resultPage(@PathVariable Long id, Model model, HttpSession session) {
        session.setAttribute("userRole", "LABORATORY");
        addRequestModel(model, labRequest(id));
        return "laboratory/resultEntry";
    }

    @PostMapping("/requests/{id}/verify")
    public String verify(@PathVariable Long id) {
        InvestigationRequest request = labRequest(id);
        if (!"RESULT_ENTERED".equals(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter results before verification");
        }
        request.setStatus("RESULT_READY");
        request.setResultReadyAt(OffsetDateTime.now());
        request.setVerifiedBy(authenticationManager.getDisplayName());
        request.setVerifiedAt(OffsetDateTime.now());
        investigationRequestRepository.save(request);
        billingWorkflowService.createForInvestigation(request);
        releaseDoctorReviewIfReady(request);
        return "redirect:/laboratory/dashboard?completed";
    }

    @GetMapping("/requests/{id}/expected")
    public String expectedPage(@PathVariable Long id, Model model, HttpSession session) {
        session.setAttribute("userRole", "LABORATORY");
        addRequestModel(model, labRequest(id));
        return "laboratory/expectedResult";
    }

    @PostMapping("/requests/{id}/expected")
    public String expected(@PathVariable Long id,
                           @RequestParam String expectedResultAt,
                           @RequestParam(required = false) String patientUpdateNote) {
        InvestigationRequest request = labRequest(id);
        if (request.getStatus() == null || "REQUESTED".equals(request.getStatus())) {
            request.setStatus("RESULT_PENDING");
        }
        request.setExpectedResultAt(LocalDateTime.parse(expectedResultAt).atZone(ZoneId.systemDefault()).toOffsetDateTime());
        request.setPatientUpdateNote(patientUpdateNote);
        investigationRequestRepository.save(request);
        return "redirect:/laboratory/dashboard?expected";
    }

    @GetMapping("/requests/{id}/report")
    public String reportPage(@PathVariable Long id, Model model, HttpSession session) {
        session.setAttribute("userRole", "LABORATORY");
        InvestigationRequest request = labRequest(id);
        if (!"RESULT_READY".equals(request.getStatus()) && !"COMPLETED".equals(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Report is available only after result verification");
        }
        addRequestModel(model, request);
        model.addAttribute("resultRows", resultRows(request));
        return "laboratory/labReport";
    }

    @GetMapping("/requests/{id}/report/download")
    public ResponseEntity<ByteArrayResource> reportDownload(@PathVariable Long id) {
        InvestigationRequest request = labRequest(id);
        if (!"RESULT_READY".equals(request.getStatus()) && !"COMPLETED".equals(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Report is available only after result verification");
        }
        Patient patient = patientRepository.findById(request.getPatientId()).orElse(null);
        String report = buildReport(request, patient);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=lab-report-" + request.getId() + ".txt")
                .contentType(MediaType.TEXT_PLAIN)
                .body(new ByteArrayResource(report.getBytes(StandardCharsets.UTF_8)));
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
        model.addAttribute("enteredCount", requests.stream().filter(request -> "RESULT_ENTERED".equals(request.getStatus())).count());
        model.addAttribute("completedCount", requests.stream().filter(request -> "RESULT_READY".equals(request.getStatus()) || "COMPLETED".equals(request.getStatus())).count());
        model.addAttribute("urgentCount", requests.stream().filter(request -> "Urgent".equalsIgnoreCase(request.getPriority()) || "Emergency".equalsIgnoreCase(request.getPriority())).count());
    }

    private InvestigationRequest labRequest(Long id) {
        InvestigationRequest request = investigationRequestRepository.findById(id).orElseThrow();
        if (isRadiology(request)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This is a radiology request");
        }
        return request;
    }

    private void addRequestModel(Model model, InvestigationRequest request) {
        Patient patient = request.getPatientId() == null ? null : patientRepository.findById(request.getPatientId()).orElse(null);
        model.addAttribute("request", request);
        model.addAttribute("patient", patient);
        model.addAttribute("resultRows", resultRows(request));
    }

    private List<String[]> resultRows(InvestigationRequest request) {
        if (request.getResultParameters() == null || request.getResultParameters().isBlank()) {
            return List.of();
        }
        return request.getResultParameters()
                .lines()
                .map(line -> line.split("\\|", -1))
                .toList();
    }

    private String buildResultParameters(List<String> names,
                                         List<String> values,
                                         List<String> units,
                                         List<String> ranges,
                                         List<String> flags) {
        if (names == null || names.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < names.size(); index++) {
            String name = valueAt(names, index);
            String value = valueAt(values, index);
            if (name.isBlank() && value.isBlank()) {
                continue;
            }
            builder.append(name).append("|")
                    .append(value).append("|")
                    .append(valueAt(units, index)).append("|")
                    .append(valueAt(ranges, index)).append("|")
                    .append(valueAt(flags, index))
                    .append(System.lineSeparator());
        }
        return builder.toString().trim();
    }

    private String buildReport(InvestigationRequest request, Patient patient) {
        return """
                Tel Health Laboratory Report

                Patient: %s
                Patient ID: %s
                Test: %s
                Priority: %s
                Sample: %s
                Collected: %s by %s
                Performed: %s by %s
                Verified: %s by %s

                Result Summary:
                %s

                Result Parameters:
                %s

                Notes:
                %s
                """.formatted(
                patient == null ? "Unknown Patient" : valueOr(patient.getFullName(), "Unknown Patient"),
                patient == null ? "-" : valueOr(patient.getPatientId(), "-"),
                valueOr(request.getTestName(), "-"),
                valueOr(request.getPriority(), "Routine"),
                valueOr(request.getSampleType(), "-"),
                request.getCollectedAt() == null ? "-" : request.getCollectedAt(),
                valueOr(request.getCollectedBy(), "-"),
                request.getPerformedAt() == null ? "-" : request.getPerformedAt(),
                valueOr(request.getPerformedBy(), "-"),
                request.getVerifiedAt() == null ? "-" : request.getVerifiedAt(),
                valueOr(request.getVerifiedBy(), "-"),
                valueOr(request.getResultSummary(), "-"),
                valueOr(request.getResultParameters(), "-"),
                valueOr(request.getNotes(), "-"));
    }

    private String valueAt(List<String> values, int index) {
        if (values == null || index >= values.size() || values.get(index) == null) {
            return "";
        }
        return values.get(index).trim();
    }

    private boolean isRadiology(InvestigationRequest request) {
        String type = request.getRequestType() == null ? "" : request.getRequestType();
        return type.equalsIgnoreCase("Radiology") || type.equalsIgnoreCase("Imaging");
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
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
