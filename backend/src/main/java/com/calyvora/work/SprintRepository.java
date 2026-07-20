package com.calyvora.work;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SprintRepository extends JpaRepository<Sprint, UUID> {

    List<Sprint> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    Optional<Sprint> findByIdAndCompanyId(UUID id, UUID companyId);

    Optional<Sprint> findByProjectIdAndStatus(UUID projectId, SprintStatus status);
}
