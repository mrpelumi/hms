package com.elegax.hms.patients.repository;

import com.elegax.hms.patients.entity.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    long countByStatus(String status);
    List<LeaveRequest> findByStaffMemberIdOrderByRequestedAtDesc(Long staffMemberId);
}
