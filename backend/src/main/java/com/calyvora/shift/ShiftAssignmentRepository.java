package com.calyvora.shift;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShiftAssignmentRepository extends JpaRepository<ShiftAssignment, UUID> {

    List<ShiftAssignment> findByCompanyIdAndOnDateBetween(UUID companyId, LocalDate from, LocalDate to);

    Optional<ShiftAssignment> findByCompanyIdAndEmployeeIdAndOnDate(UUID companyId, UUID employeeId, LocalDate onDate);

    Optional<ShiftAssignment> findByIdAndCompanyId(UUID id, UUID companyId);

    List<ShiftAssignment> findByEmployeeIdAndOnDateBetweenOrderByOnDateAsc(UUID employeeId, LocalDate from, LocalDate to);
}
