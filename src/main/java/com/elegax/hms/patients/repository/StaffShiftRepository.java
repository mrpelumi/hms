package com.elegax.hms.patients.repository;

import com.elegax.hms.patients.entity.StaffShift;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StaffShiftRepository extends JpaRepository<StaffShift, Long> {
    List<StaffShift> findByStaffMemberIdOrderByShiftDateDescStartTimeDesc(Long staffMemberId);
    List<StaffShift> findByShiftDate(LocalDate shiftDate);
    Optional<StaffShift> findFirstByStaffMemberIdAndShiftDateOrderByStartTimeAsc(Long staffMemberId, LocalDate shiftDate);
}
