package com.calyvora.performance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PerformanceReviewRepository extends JpaRepository<PerformanceReview, UUID> {

    Optional<PerformanceReview> findByIdAndCompanyId(UUID id, UUID companyId);

    List<PerformanceReview> findByCycleIdOrderByCreatedAtAsc(UUID cycleId);

    List<PerformanceReview> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);

    /**
     * Reviews for a whole set of people at once — what the team screens ask for, since they cover
     * everybody below the caller. One query rather than one per report: a lead with thirty people
     * under them would otherwise cost thirty round trips to render a single page.
     */
    List<PerformanceReview> findByEmployeeIdInOrderByCreatedAtDesc(Collection<UUID> employeeIds);

    List<PerformanceReview> findByManagerIdOrderByCreatedAtDesc(UUID managerId);

    boolean existsByCycleIdAndEmployeeId(UUID cycleId, UUID employeeId);

    long countByCycleId(UUID cycleId);

    long countByCycleIdAndStatus(UUID cycleId, ReviewStatus status);
}
