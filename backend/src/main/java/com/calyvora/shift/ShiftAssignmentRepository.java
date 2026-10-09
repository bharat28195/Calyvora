package com.calyvora.shift;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShiftAssignmentRepository extends JpaRepository<ShiftAssignment, UUID> {

    List<ShiftAssignment> findByCompanyIdAndOnDateBetween(UUID companyId, LocalDate from, LocalDate to);

    /** Everyone's slots over a window, as plain values (see {@link ShiftSlot}). */
    @org.springframework.data.jpa.repository.Query("select new com.calyvora.shift.ShiftSlot(a.employeeId, a.onDate, a.shiftId) "
            + "from ShiftAssignment a where a.companyId = :companyId and a.onDate between :from and :to")
    List<ShiftSlot> slots(UUID companyId, LocalDate from, LocalDate to);

    /** One group's slots over a window — a team's month, not the company's. */
    @org.springframework.data.jpa.repository.Query("select new com.calyvora.shift.ShiftSlot(a.employeeId, a.onDate, a.shiftId) "
            + "from ShiftAssignment a where a.companyId = :companyId and a.employeeId in :employeeIds "
            + "and a.onDate between :from and :to")
    List<ShiftSlot> slotsFor(UUID companyId, java.util.Collection<UUID> employeeIds, LocalDate from, LocalDate to);

    /** One person's slots over a window. */
    @org.springframework.data.jpa.repository.Query("select new com.calyvora.shift.ShiftSlot(a.employeeId, a.onDate, a.shiftId) "
            + "from ShiftAssignment a where a.employeeId = :employeeId and a.onDate between :from and :to")
    List<ShiftSlot> slotsOf(UUID employeeId, LocalDate from, LocalDate to);

    Optional<ShiftAssignment> findByCompanyIdAndEmployeeIdAndOnDate(UUID companyId, UUID employeeId, LocalDate onDate);

    Optional<ShiftAssignment> findByIdAndCompanyId(UUID id, UUID companyId);

    List<ShiftAssignment> findByEmployeeIdAndOnDateBetweenOrderByOnDateAsc(UUID employeeId, LocalDate from, LocalDate to);
}
