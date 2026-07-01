package com.elegax.hms.patients.repository;

import com.elegax.hms.patients.entity.StaffMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StaffMemberRepository extends JpaRepository<StaffMember, Long> {
    Optional<StaffMember> findByStaffId(String staffId);
    long countByStatus(String status);
}
