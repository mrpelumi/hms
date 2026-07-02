package com.elegax.hms.patients.repository;

import com.elegax.hms.patients.entity.AppointmentSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface AppointmentSlotRepository extends JpaRepository<AppointmentSlot, Long> {
    Optional<AppointmentSlot> findByProviderUsernameAndSlotStart(String providerUsername, OffsetDateTime slotStart);
    List<AppointmentSlot> findByProviderUsernameAndSlotStartBetween(String providerUsername, OffsetDateTime from, OffsetDateTime to);
}
