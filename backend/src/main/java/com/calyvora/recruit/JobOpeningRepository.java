package com.calyvora.recruit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobOpeningRepository extends JpaRepository<JobOpening, UUID> {

    List<JobOpening> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    Optional<JobOpening> findByIdAndCompanyId(UUID id, UUID companyId);

    /** For the assistant: roles actively being hired for. */
    long countByCompanyIdAndStatus(UUID companyId, JobStatus status);
}
