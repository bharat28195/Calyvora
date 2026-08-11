package com.calyvora.people;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OnboardingTaskRepository extends JpaRepository<OnboardingTask, UUID> {

    // Every read is scoped by kind: an exit clearance item has no business appearing on a joiner's
    // checklist, and the two lists are shown on different screens to different people.
    List<OnboardingTask> findByEmployeeIdAndKindOrderBySortOrderAscCreatedAtAsc(UUID employeeId, ChecklistKind kind);

    Optional<OnboardingTask> findByIdAndCompanyId(UUID id, UUID companyId);

    long countByEmployeeIdAndKind(UUID employeeId, ChecklistKind kind);

    long countByEmployeeIdAndKindAndCompletedTrue(UUID employeeId, ChecklistKind kind);
}
