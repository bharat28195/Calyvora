package com.calyvora.people;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID> {

    List<LeaveRequest> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);

    List<LeaveRequest> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    /**
     * One page of the company's requests, newest first, starting strictly below a cursor.
     *
     * <p>Ordered by {@code (createdAt, id)} and compared the same way, so a boundary that falls
     * inside a group of rows created in the same instant neither repeats nor drops any of them.
     *
     * <p>Statuses are a collection rather than a nullable single value so the predicate is always
     * present: "any status" is the full set, not a null that each query has to special-case. One
     * query shape, and no null-typing to get wrong.
     */
    @Query("select l from LeaveRequest l where l.companyId = :companyId "
            + "and l.status in :statuses "
            + "and (l.createdAt < :ts or (l.createdAt = :ts and l.id < :id)) "
            + "order by l.createdAt desc, l.id desc")
    List<LeaveRequest> pageForCompany(UUID companyId, Collection<LeaveStatus> statuses,
                                      Instant ts, UUID id, Pageable limit);

    /** The same page, narrowed to one approver's downline. */
    @Query("select l from LeaveRequest l where l.companyId = :companyId "
            + "and l.employeeId in :employeeIds "
            + "and l.status in :statuses "
            + "and (l.createdAt < :ts or (l.createdAt = :ts and l.id < :id)) "
            + "order by l.createdAt desc, l.id desc")
    List<LeaveRequest> pageForRoster(UUID companyId, Collection<UUID> employeeIds,
                                     Collection<LeaveStatus> statuses,
                                     Instant ts, UUID id, Pageable limit);

    /** One roster's requests — the team screens, which must not read the company's. */
    List<LeaveRequest> findByCompanyIdAndEmployeeIdInOrderByCreatedAtDesc(
            UUID companyId, Collection<UUID> employeeIds);

    /** The pending count per person, without loading anything that is already decided. */
    List<LeaveRequest> findByCompanyIdAndEmployeeIdInAndStatus(
            UUID companyId, Collection<UUID> employeeIds, LeaveStatus status);

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
