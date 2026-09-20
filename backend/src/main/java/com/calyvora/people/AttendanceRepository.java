package com.calyvora.people;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceRepository extends JpaRepository<AttendanceRecord, UUID> {

    Optional<AttendanceRecord> findByEmployeeIdAndDate(UUID employeeId, LocalDate date);

    List<AttendanceRecord> findByCompanyIdAndDate(UUID companyId, LocalDate date);

    List<AttendanceRecord> findByEmployeeIdAndDateBetweenOrderByDateAsc(UUID employeeId, LocalDate from, LocalDate to);

    List<AttendanceRecord> findByCompanyIdAndDateBetween(UUID companyId, LocalDate from, LocalDate to);

    /**
     * One roster's month, rather than the company's.
     *
     * <p>The team screens want a hundred people out of a thousand. Reading the company's month and
     * discarding nine rows in ten means twenty thousand rows crossing the wire to draw one table —
     * which is exactly what the team summary used to do, and what made it the slowest page in the app.
     */
    List<AttendanceRecord> findByCompanyIdAndEmployeeIdInAndDateBetween(
            UUID companyId, Collection<UUID> employeeIds, LocalDate from, LocalDate to);

    List<AttendanceRecord> findByCompanyIdAndEmployeeIdInAndDate(
            UUID companyId, Collection<UUID> employeeIds, LocalDate date);
}
