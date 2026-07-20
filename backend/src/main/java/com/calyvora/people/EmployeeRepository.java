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

    long countByDepartmentId(UUID departmentId);

    List<Employee> findByDepartmentId(UUID departmentId);

    long countByManagerId(UUID managerId);

    List<Employee> findByManagerId(UUID managerId);
}
