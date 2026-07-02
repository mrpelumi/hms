package com.elegax.hms.patients.service;

import com.elegax.hms.patients.entity.Appointment;
import com.elegax.hms.patients.entity.QueueEntry;
import com.elegax.hms.patients.repository.AppointmentRepository;
import com.elegax.hms.patients.repository.QueueEntryRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class AppointmentWorkflowService {

    private final AppointmentRepository appointmentRepository;
    private final QueueEntryRepository queueEntryRepository;
    private final BillingWorkflowService billingWorkflowService;

    public AppointmentWorkflowService(AppointmentRepository appointmentRepository,
                                      QueueEntryRepository queueEntryRepository,
                                      BillingWorkflowService billingWorkflowService) {
        this.appointmentRepository = appointmentRepository;
        this.queueEntryRepository = queueEntryRepository;
        this.billingWorkflowService = billingWorkflowService;
    }

    public QueueEntry checkIn(Appointment appointment, String queueToken) {
        QueueEntry queueEntry = queueEntryRepository.findByAppointmentId(appointment.getId())
                .orElseGet(() -> QueueEntry.builder()
                        .appointmentId(appointment.getId())
                        .patientId(appointment.getPatientId())
                        .token(queueToken)
                        .queuedAt(OffsetDateTime.now())
                        .build());
        queueEntry.setStatus("WAITING_FOR_NURSE");
        queueEntryRepository.save(queueEntry);

        appointment.setStatus("CHECKED_IN");
        appointment.setCheckedInAt(OffsetDateTime.now());
        appointmentRepository.save(appointment);
        billingWorkflowService.createForAppointmentCheckIn(appointment.getPatientId(), appointment.getId());
        return queueEntry;
    }

    public void completeAppointment(Appointment appointment, QueueEntry queueEntry) {
        queueEntry.setStatus("SERVED");
        queueEntry.setServedAt(OffsetDateTime.now());
        queueEntryRepository.save(queueEntry);
        appointment.setStatus("COMPLETED");
        appointmentRepository.save(appointment);
    }
}
