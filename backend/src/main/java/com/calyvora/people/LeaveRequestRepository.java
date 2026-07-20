package com.calyvora.people;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID> {

    List<LeaveRequest> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);

    List<LeaveRequest> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    Optional<LeaveRequest> findByIdAndCompanyId(UUID id, UUID companyId);

    List<LeaveRequest> findByEmployeeIdAndTypeAndStatus(UUID employeeId, LeaveType type, LeaveStatus status);
}
