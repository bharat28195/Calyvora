package com.calyvora.people;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID> {

    List<LeaveRequest> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);

    List<LeaveRequest> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    Optional<LeaveRequest> findByIdAndCompanyId(UUID id, UUID companyId);

    List<LeaveRequest> findByEmployeeIdAndTypeAndStatus(UUID employeeId, LeaveType type, LeaveStatus status);

    /** For the assistant: how many requests are sitting with an approver right now. */
    long countByCompanyIdAndStatus(UUID companyId, LeaveStatus status);

    /**
     * Approved leave that overlaps a window — the only leave a date range can possibly be affected by.
     *
     * <p>Replaces reading the company's entire leave history to answer a question about one day. A
     * company two years old has tens of thousands of requests and one day is touched by a handful of
     * them; the day sheet was loading all of them, every time, and filtering in Java.
     *
     * <p>Overlap, not containment: a request that starts before the window and ends inside it still
     * covers days in it. {@code startDate <= to AND endDate >= from}.
     */
    List<LeaveRequest> findByCompanyIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            UUID companyId, LeaveStatus status, LocalDate to, LocalDate from);
}
