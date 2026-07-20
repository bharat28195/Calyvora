package com.calyvora.people;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OnboardingTaskRepository extends JpaRepository<OnboardingTask, UUID> {

    List<OnboardingTask> findByEmployeeIdOrderBySortOrderAscCreatedAtAsc(UUID employeeId);

    Optional<OnboardingTask> findByIdAndCompanyId(UUID id, UUID companyId);

    long countByEmployeeId(UUID employeeId);

    long countByEmployeeIdAndCompletedTrue(UUID employeeId);
}
