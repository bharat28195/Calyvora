package com.calyvora.performance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewCycleRepository extends JpaRepository<ReviewCycle, UUID> {

    List<ReviewCycle> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    Optional<ReviewCycle> findByIdAndCompanyId(UUID id, UUID companyId);
}
