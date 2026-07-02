package com.elegax.hms.patients.service;

import com.elegax.hms.authentication.AuthenticationManager;
import com.elegax.hms.patients.entity.LeaveRequest;
import com.elegax.hms.patients.entity.StaffMember;
import com.elegax.hms.patients.repository.LeaveRequestRepository;
import com.elegax.hms.patients.repository.StaffMemberRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class LeaveRequestService {

    private final StaffMemberRepository staffMemberRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final AuthenticationManager authenticationManager;

    public LeaveRequestService(StaffMemberRepository staffMemberRepository,
                               LeaveRequestRepository leaveRequestRepository,
                               AuthenticationManager authenticationManager) {
        this.staffMemberRepository = staffMemberRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.authenticationManager = authenticationManager;
    }

    public Optional<StaffMember> currentStaffMember() {
        String username = valueOr(authenticationManager.getUsername(), "");
        String displayName = valueOr(authenticationManager.getDisplayName(), "");
        return staffMemberRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .filter(staff -> valueOr(staff.getEmail(), "").equalsIgnoreCase(username)
                        || valueOr(staff.getFullName(), "").equalsIgnoreCase(displayName))
                .findFirst();
    }

    public List<LeaveRequest> currentStaffRequests() {
        return currentStaffMember()
                .map(staff -> leaveRequestRepository.findByStaffMemberIdOrderByRequestedAtDesc(staff.getId()))
                .orElse(List.of());
    }

    public LeaveRequest submitCurrentStaffRequest(String leaveType,
                                                  String startDate,
                                                  String endDate,
                                                  String reason) {
        StaffMember staff = currentStaffMember()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Your staff record was not found. Contact HR before submitting leave."));
        LocalDate start = parseDate(startDate, "Leave start date is required");
        LocalDate end = parseDate(endDate, "Leave end date is required");
        if (end.isBefore(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Leave end date cannot be before start date");
        }
        return leaveRequestRepository.save(LeaveRequest.builder()
                .staffMemberId(staff.getId())
                .leaveType(valueOr(leaveType, "Annual"))
                .startDate(start)
                .endDate(end)
                .reason(reason)
                .status("PENDING")
                .build());
    }

    private LocalDate parseDate(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return LocalDate.parse(value);
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
