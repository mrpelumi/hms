package com.elegax.hms.patients.repository;

import com.elegax.hms.patients.entity.DoctorSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DoctorScheduleRepository extends JpaRepository<DoctorSchedule, Long> {
    List<DoctorSchedule> findByDoctorUsernameOrderByIdAsc(String doctorUsername);
    List<DoctorSchedule> findByAvailableTrueOrderByDoctorNameAscDayOfWeekAscStartTimeAsc();
    Optional<DoctorSchedule> findByDoctorUsernameAndDayOfWeek(String doctorUsername, String dayOfWeek);
}
