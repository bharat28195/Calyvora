package com.calyvora.people;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceRepository extends JpaRepository<AttendanceRecord, UUID> {

    Optional<AttendanceRecord> findByEmployeeIdAndDate(UUID employeeId, LocalDate date);

    List<AttendanceRecord> findByCompanyIdAndDate(UUID companyId, LocalDate date);

    List<AttendanceRecord> findByEmployeeIdAndDateBetweenOrderByDateAsc(UUID employeeId, LocalDate from, LocalDate to);

    List<AttendanceRecord> findByCompanyIdAndDateBetween(UUID companyId, LocalDate from, LocalDate to);
}
