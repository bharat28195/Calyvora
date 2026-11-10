package com.calyvora.people;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AttendancePunchRepository extends JpaRepository<AttendancePunch, UUID> {

    List<AttendancePunch> findByEmployeeIdAndDateOrderByCheckInAsc(UUID employeeId, LocalDate date);

    List<AttendancePunch> findByEmployeeIdAndDateBetween(UUID employeeId, LocalDate from, LocalDate to);

    List<AttendancePunch> findByCompanyIdAndDateBetween(UUID companyId, LocalDate from, LocalDate to);

    List<AttendancePunch> findByCompanyIdAndEmployeeIdInAndDateBetween(
            UUID companyId, Collection<UUID> employeeIds, LocalDate from, LocalDate to);

    void deleteByEmployeeIdAndDate(UUID employeeId, LocalDate date);

    /** Sessions on a day that were never closed, and nobody has been reminded about yet (V77). */
    List<AttendancePunch> findByCompanyIdAndDateAndCheckOutIsNullAndRemindedAtIsNull(UUID companyId, LocalDate date);
}
