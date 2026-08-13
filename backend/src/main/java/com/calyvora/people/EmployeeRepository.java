package com.calyvora.people;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    Optional<Employee> findByIdAndCompanyId(UUID id, UUID companyId);

    Optional<Employee> findByUserId(UUID userId);

    List<Employee> findByCompanyId(UUID companyId);

    List<Employee> findByCompanyIdAndUserIdIn(UUID companyId, List<UUID> userIds);

    /** Everyone in one employment state — the exits screen asks for NOTICE (PD-20). */
    List<Employee> findByCompanyIdAndEmploymentStatus(UUID companyId, EmploymentStatus employmentStatus);

    long countByDepartmentId(UUID departmentId);

    List<Employee> findByDepartmentId(UUID departmentId);

    long countByManagerId(UUID managerId);

    /** For the assistant: how many people are in a given employment state (notice, onboarding...). */
    long countByCompanyIdAndEmploymentStatus(UUID companyId, EmploymentStatus employmentStatus);

    List<Employee> findByManagerId(UUID managerId);
}
