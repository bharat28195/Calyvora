package com.calyvora.people;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceRegularizationRepository extends JpaRepository<AttendanceRegularization, UUID> {

    List<AttendanceRegularization> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);

    List<AttendanceRegularization> findByCompanyIdAndStatusOrderByCreatedAtAsc(UUID companyId, RegularizationStatus status);

    Optional<AttendanceRegularization> findByIdAndCompanyId(UUID id, UUID companyId);
}
