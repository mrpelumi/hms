package com.elegax.hms.controller;

import com.elegax.hms.patients.entity.Appointment;
import com.elegax.hms.patients.entity.Consultation;
import com.elegax.hms.patients.entity.DoctorSchedule;
import com.elegax.hms.patients.entity.InvestigationRequest;
import com.elegax.hms.patients.entity.LeaveRequest;
import com.elegax.hms.patients.entity.Patient;
import com.elegax.hms.patients.entity.QueueEntry;
import com.elegax.hms.patients.entity.StaffMember;
import com.elegax.hms.patients.repository.AppointmentRepository;
import com.elegax.hms.patients.repository.ConsultationRepository;
import com.elegax.hms.patients.repository.DoctorScheduleRepository;
import com.elegax.hms.patients.repository.InvestigationRequestRepository;
import com.elegax.hms.patients.repository.LeaveRequestRepository;
import com.elegax.hms.patients.repository.PatientRepository;
import com.elegax.hms.patients.repository.QueueEntryRepository;
import com.elegax.hms.patients.repository.StaffMemberRepository;
import com.elegax.hms.patients.service.AppointmentWorkflowService;
import com.elegax.hms.patients.service.SchedulingService;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
public class ReceptionController {

	private final PatientRepository patientRepository;
	private final AppointmentRepository appointmentRepository;
	private final QueueEntryRepository queueEntryRepository;
	private final ConsultationRepository consultationRepository;
	private final DoctorScheduleRepository doctorScheduleRepository;
	private final InvestigationRequestRepository investigationRequestRepository;
	private final StaffMemberRepository staffMemberRepository;
	private final LeaveRequestRepository leaveRequestRepository;
	private final SchedulingService schedulingService;
	private final AppointmentWorkflowService appointmentWorkflowService;

	public ReceptionController(PatientRepository patientRepository,
							   AppointmentRepository appointmentRepository,
							   QueueEntryRepository queueEntryRepository,
							   ConsultationRepository consultationRepository,
							   DoctorScheduleRepository doctorScheduleRepository,
							   InvestigationRequestRepository investigationRequestRepository,
							   StaffMemberRepository staffMemberRepository,
							   LeaveRequestRepository leaveRequestRepository,
							   SchedulingService schedulingService,
							   AppointmentWorkflowService appointmentWorkflowService) {
		this.patientRepository = patientRepository;
		this.appointmentRepository = appointmentRepository;
		this.queueEntryRepository = queueEntryRepository;
		this.consultationRepository = consultationRepository;
		this.doctorScheduleRepository = doctorScheduleRepository;
		this.investigationRequestRepository = investigationRequestRepository;
		this.staffMemberRepository = staffMemberRepository;
		this.leaveRequestRepository = leaveRequestRepository;
		this.schedulingService = schedulingService;
		this.appointmentWorkflowService = appointmentWorkflowService;
	}

	@GetMapping("/reception/home")
	public String home(Model model) {
		List<Patient> patients = patientRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
		List<QueueEntry> activeQueueEntries = queueEntryRepository.findAll(Sort.by(Sort.Direction.DESC, "queuedAt"))
				.stream()
				.filter(entry -> !"SERVED".equals(entry.getStatus()))
				.toList();
		Map<Long, Patient> patientsById = patientsById(activeQueueEntries.stream().map(QueueEntry::getPatientId).toList());
		Map<Long, Appointment> appointmentsById = appointmentRepository.findAllById(activeQueueEntries.stream()
						.map(QueueEntry::getAppointmentId)
						.filter(Objects::nonNull)
						.toList())
				.stream()
				.collect(Collectors.toMap(Appointment::getId, Function.identity()));
		Map<String, Long> activeDoctorsByDepartment = appointmentsById.values()
				.stream()
				.filter(appointment -> appointment.getDepartment() != null && appointment.getProvider() != null)
				.collect(Collectors.groupingBy(Appointment::getDepartment,
						Collectors.mapping(Appointment::getProvider, Collectors.collectingAndThen(Collectors.toSet(), set -> (long) set.size()))));
		List<InvestigationRequest> pendingResultUpdates = investigationRequestRepository.findAll(Sort.by(Sort.Direction.ASC, "expectedResultAt"))
				.stream()
				.filter(request -> "RESULT_PENDING".equals(request.getStatus()))
				.toList();
		Map<Long, Patient> resultPatientsById = patientsById(pendingResultUpdates.stream().map(InvestigationRequest::getPatientId).toList());

		model.addAttribute("pageTitle", "Patient Management");
		model.addAttribute("todaysRegistrations", countPatientsCreatedToday(patients));
		model.addAttribute("pendingAppointments", appointmentRepository.countByStatus("BOOKED"));
		model.addAttribute("patientsInQueue", activeQueueEntries.size());
		model.addAttribute("totalCollected", 0);
		model.addAttribute("queueEntries", activeQueueEntries);
		model.addAttribute("patientsById", patientsById);
		model.addAttribute("appointmentsById", appointmentsById);
		model.addAttribute("activeDoctorsByDepartment", activeDoctorsByDepartment);
		model.addAttribute("pendingResultUpdates", pendingResultUpdates);
		model.addAttribute("resultPatientsById", resultPatientsById);
		model.addAttribute("queueVolumeLabel", activeQueueEntries.size() >= 8 ? "High Volume" : "Normal Flow");
		return "reception/home";
	}

	@GetMapping("/reception/registration")
	public String registration(Model model) {
		model.addAttribute("pageTitle", "Patient Management");
		// return only the Thymeleaf fragment named 'registration' from the reception template
		return "reception/registration";
	}

	@GetMapping("/reception/patients")
	public String patients(Model model) {
		List<Patient> patients = patientRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
		model.addAttribute("pageTitle", "Patient Management");
		model.addAttribute("patients", patients);
		model.addAttribute("totalPatients", patients.size());
		model.addAttribute("newPatientsThisYear", countPatientsCreatedThisYear(patients));
		model.addAttribute("unprocessedForms", 0);
		return "reception/patientHome";
	}

	@GetMapping("/reception/patients/{patientId}")
	public String patientProfile(@PathVariable String patientId, Model model) {
		Patient patient = getPatient(patientId);
		model.addAttribute("pageTitle", "Patient Profile");
		model.addAttribute("patient", patient);
		return "reception/patientProfile";
	}

	@GetMapping("/reception/patients/{patientId}/edit")
	public String updateProfile(@PathVariable String patientId, Model model) {
		Patient patient = getPatient(patientId);
		model.addAttribute("pageTitle", "Update Patient");
		model.addAttribute("patient", patient);
		return "reception/updateProfile";
	}

	@GetMapping("/reception/appointment")
	public String appointment(Model model) {
		List<Appointment> appointments = appointmentRepository.findAll(Sort.by(Sort.Direction.DESC, "scheduledAt"));
		Map<Long, Patient> patientsById = patientsById(appointments.stream().map(Appointment::getPatientId).toList());
		model.addAttribute("pageTitle", "Appointment Booking");
		model.addAttribute("appointments", appointments);
		model.addAttribute("patientsById", patientsById);
		model.addAttribute("bookedCount", appointmentRepository.countByStatus("BOOKED"));
		model.addAttribute("checkedInCount", appointmentRepository.countByStatus("CHECKED_IN"));
		model.addAttribute("completedCount", appointmentRepository.countByStatus("COMPLETED"));
		return "reception/appointment";
	}

	@GetMapping("/reception/appointment/new")
	public String scheduleAppointment(Model model) {
		Map<String, StaffMember> activeDoctorsByUsername = staffMemberRepository.findAll(Sort.by(Sort.Direction.ASC, "fullName"))
				.stream()
				.filter(staff -> "DOCTOR".equalsIgnoreCase(valueOr(staff.getStaffRole(), "")))
				.filter(staff -> "ACTIVE".equalsIgnoreCase(valueOr(staff.getStatus(), "ACTIVE")))
				.filter(staff -> staff.getEmail() != null && !staff.getEmail().isBlank())
				.collect(Collectors.toMap(staff -> staff.getEmail().toLowerCase(), Function.identity(), (first, ignored) -> first));
		List<DoctorSchedule> availableSchedules = doctorScheduleRepository.findByAvailableTrueOrderByDoctorNameAscDayOfWeekAscStartTimeAsc()
				.stream()
				.filter(schedule -> schedule.getDoctorUsername() != null)
				.filter(schedule -> activeDoctorsByUsername.containsKey(schedule.getDoctorUsername().toLowerCase()))
				.toList();
		List<Appointment> bookedAppointments = appointmentRepository.findAll(Sort.by(Sort.Direction.ASC, "scheduledAt"))
				.stream()
				.filter(appointment -> appointment.getScheduledAt() != null)
				.filter(appointment -> appointment.getProviderUsername() != null)
				.filter(appointment -> !"CANCELLED".equals(appointment.getStatus()) && !"COMPLETED".equals(appointment.getStatus()))
				.toList();
		List<LeaveRequest> approvedDoctorLeaves = leaveRequestRepository.findAll(Sort.by(Sort.Direction.ASC, "startDate"))
				.stream()
				.filter(leave -> "APPROVED".equalsIgnoreCase(valueOr(leave.getStatus(), "")))
				.filter(leave -> leave.getStartDate() != null && leave.getEndDate() != null)
				.filter(leave -> activeDoctorsByUsername.values().stream().anyMatch(doctor -> Objects.equals(doctor.getId(), leave.getStaffMemberId())))
				.toList();
		model.addAttribute("pageTitle", "Schedule Appointment");
		model.addAttribute("patients", patientRepository.findAll(Sort.by(Sort.Direction.ASC, "fullName")));
		model.addAttribute("doctorSchedules", availableSchedules);
		model.addAttribute("bookedAppointments", bookedAppointments);
		model.addAttribute("approvedDoctorLeaves", approvedDoctorLeaves);
		model.addAttribute("staffById", activeDoctorsByUsername.values().stream().collect(Collectors.toMap(StaffMember::getId, Function.identity())));
		model.addAttribute("doctorStaffByUsername", activeDoctorsByUsername);
		model.addAttribute("clinicalDepartments", activeDoctorsByUsername.values()
				.stream()
				.map(StaffMember::getDepartment)
				.filter(department -> department != null && !department.isBlank())
				.distinct()
				.sorted(String::compareToIgnoreCase)
				.toList());
		model.addAttribute("today", LocalDate.now());
		model.addAttribute("doctors", availableSchedules.stream()
				.collect(Collectors.toMap(DoctorSchedule::getDoctorUsername, Function.identity(), (first, ignored) -> first))
				.values()
				.stream()
				.sorted((first, second) -> String.valueOf(first.getDoctorName()).compareToIgnoreCase(String.valueOf(second.getDoctorName())))
				.toList());
		return "reception/scheduleAppointment";
	}

	@PostMapping("/reception/appointment")
	public String createAppointment(@RequestParam Long patientId,
									@RequestParam String appointmentDate,
									@RequestParam String appointmentTime,
									@RequestParam String providerUsername,
									@RequestParam String department,
									@RequestParam String visitType,
									@RequestParam String priority,
									@RequestParam(required = false) String reason) {
		OffsetDateTime scheduledAt = OffsetDateTime.of(
				LocalDate.parse(appointmentDate),
				LocalTime.parse(appointmentTime),
				ZoneId.systemDefault().getRules().getOffset(OffsetDateTime.now().toInstant())
		);
		schedulingService.bookAppointment(patientId, providerUsername, scheduledAt, department, visitType, priority, reason);
		return "redirect:/reception/appointment?created";
	}

	@GetMapping("/reception/appointment/{appointmentId}/check-in")
	public String patientCheckIn(@PathVariable Long appointmentId, Model model) {
		Appointment appointment = getAppointment(appointmentId);
		Patient patient = getPatientById(appointment.getPatientId());
		model.addAttribute("pageTitle", "Patient Check-In");
		model.addAttribute("appointment", appointment);
		model.addAttribute("patient", patient);
		model.addAttribute("queueToken", buildQueueToken(appointment));
		return "reception/patientCheckIn";
	}

	@PostMapping("/reception/appointment/{appointmentId}/check-in")
	public String completeCheckIn(@PathVariable Long appointmentId) {
		Appointment appointment = getAppointment(appointmentId);
		appointmentWorkflowService.checkIn(appointment, buildQueueToken(appointment));
		return "redirect:/reception/queue?checkedIn";
	}

	@GetMapping("/reception/queue")
	public String queue(Model model) {
		List<QueueEntry> queueEntries = queueEntryRepository.findAll(Sort.by(Sort.Direction.DESC, "queuedAt"))
				.stream()
				.filter(entry -> !"SERVED".equals(entry.getStatus()))
				.toList();
		Map<Long, Patient> patientsById = patientsById(queueEntries.stream().map(QueueEntry::getPatientId).toList());
		Map<Long, Appointment> appointmentsById = appointmentRepository.findAllById(queueEntries.stream()
						.map(QueueEntry::getAppointmentId)
						.filter(Objects::nonNull)
						.toList())
				.stream()
				.collect(Collectors.toMap(Appointment::getId, Function.identity()));

		model.addAttribute("pageTitle", "Queue Management");
		model.addAttribute("queueEntries", queueEntries);
		model.addAttribute("patientsById", patientsById);
		model.addAttribute("appointmentsById", appointmentsById);
		model.addAttribute("waitingCount", queueEntries.stream().filter(entry -> "WAITING_FOR_NURSE".equals(entry.getStatus())).count());
		model.addAttribute("readyForDoctorCount", queueEntries.stream().filter(entry -> "READY_FOR_DOCTOR".equals(entry.getStatus())).count());
		model.addAttribute("calledCount", queueEntries.stream().filter(entry -> "CALLED".equals(entry.getStatus()) || "IN_PROGRESS".equals(entry.getStatus())).count());
		model.addAttribute("servedCount", queueEntryRepository.countByStatus("SERVED"));
		return "reception/queue";
	}

	@PostMapping("/reception/queue/{queueEntryId}/call")
	public String callQueueEntry(@PathVariable Long queueEntryId) {
		QueueEntry queueEntry = getQueueEntry(queueEntryId);
		if (!"READY_FOR_DOCTOR".equals(queueEntry.getStatus()) && !"CALLED".equals(queueEntry.getStatus()) && !"IN_PROGRESS".equals(queueEntry.getStatus())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Patient is not ready for doctor consultation");
		}
		if (!"IN_PROGRESS".equals(queueEntry.getStatus())) {
			queueEntry.setStatus("CALLED");
		}
		queueEntryRepository.save(queueEntry);
		return "redirect:/reception/consultation/" + queueEntry.getId();
	}

	@GetMapping("/reception/consultation/{queueEntryId}")
	public String doctorConsultation(@PathVariable Long queueEntryId) {
		return "redirect:/doctor/consultation/" + queueEntryId;
	}

	@PostMapping("/reception/consultation/{queueEntryId}/complete")
	public String completeConsultation(@PathVariable Long queueEntryId,
									   @RequestParam(required = false) String doctorNotes,
									   @RequestParam(required = false) String diagnosis,
									   @RequestParam(required = false) String prescriptions) {
		QueueEntry queueEntry = getQueueEntry(queueEntryId);
		Appointment appointment = getAppointment(queueEntry.getAppointmentId());

		Consultation consultation = Consultation.builder()
				.appointmentId(appointment.getId())
				.patientId(queueEntry.getPatientId())
				.consultationAt(OffsetDateTime.now())
				.doctorNotes(doctorNotes)
				.diagnosis(diagnosis)
				.prescriptions(prescriptions)
				.build();
		consultationRepository.save(consultation);

		appointmentWorkflowService.completeAppointment(appointment, queueEntry);
		return "redirect:/reception/queue?completed";
	}

	private Patient getPatient(String patientId) {
		return patientRepository.findByPatientId(patientId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));
	}

	private Patient getPatientById(Long id) {
		return patientRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));
	}

	private Appointment getAppointment(Long id) {
		return appointmentRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));
	}

	private QueueEntry getQueueEntry(Long id) {
		return queueEntryRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Queue entry not found"));
	}

	private Map<Long, Patient> patientsById(List<Long> patientIds) {
		return patientRepository.findAllById(patientIds.stream().filter(Objects::nonNull).distinct().toList())
				.stream()
				.collect(Collectors.toMap(Patient::getId, Function.identity()));
	}

	private String buildQueueToken(Appointment appointment) {
		String prefix = switch (appointment.getDepartment() == null ? "" : appointment.getDepartment()) {
			case "Laboratory" -> "LB";
			case "Pharmacy" -> "PH";
			default -> "OP";
		};
		return String.format("#%s-%03d", prefix, appointment.getId() == null ? 0 : appointment.getId());
	}

	private long countPatientsCreatedThisYear(List<Patient> patients) {
		int currentYear = LocalDate.now().getYear();
		return patients.stream()
				.filter(patient -> patient.getCreatedAt() != null && patient.getCreatedAt().getYear() == currentYear)
				.count();
	}

	private long countPatientsCreatedToday(List<Patient> patients) {
		LocalDate today = LocalDate.now();
		return patients.stream()
				.filter(patient -> patient.getCreatedAt() != null && patient.getCreatedAt().toLocalDate().equals(today))
				.count();
	}

	private String valueOr(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}

}


