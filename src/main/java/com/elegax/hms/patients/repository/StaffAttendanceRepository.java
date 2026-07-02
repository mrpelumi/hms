package com.elegax.hms.patients.repository;

import com.elegax.hms.patients.entity.StaffAttendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StaffAttendanceRepository extends JpaRepository<StaffAttendance, Long> {
    List<StaffAttendance> findByAttendanceDate(LocalDate attendanceDate);
    Optional<StaffAttendance> findByStaffMemberIdAndAttendanceDate(Long staffMemberId, LocalDate attendanceDate);
    long countByAttendanceDateAndStatus(LocalDate attendanceDate, String status);
}
