package com.calyvora.performance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PerformanceReviewRepository extends JpaRepository<PerformanceReview, UUID> {

    Optional<PerformanceReview> findByIdAndCompanyId(UUID id, UUID companyId);

    List<PerformanceReview> findByCycleIdOrderByCreatedAtAsc(UUID cycleId);

    List<PerformanceReview> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);

    List<PerformanceReview> findByManagerIdOrderByCreatedAtDesc(UUID managerId);

    boolean existsByCycleIdAndEmployeeId(UUID cycleId, UUID employeeId);

    long countByCycleId(UUID cycleId);

    long countByCycleIdAndStatus(UUID cycleId, ReviewStatus status);
}
