package com.calyvora.recruit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CandidateRepository extends JpaRepository<Candidate, UUID> {

    List<Candidate> findByJobIdOrderByCreatedAtAsc(UUID jobId);

    List<Candidate> findByCompanyId(UUID companyId);

    Optional<Candidate> findByIdAndCompanyId(UUID id, UUID companyId);

    long countByJobId(UUID jobId);

    long countByJobIdAndStage(UUID jobId, CandidateStage stage);
}
