package com.calyvora.trial;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrialRequestRepository extends JpaRepository<TrialRequest, UUID> {

    Optional<TrialRequest> findByEmailAndStatus(String email, TrialRequestStatus status);

    List<TrialRequest> findByStatusOrderByCreatedAtAsc(TrialRequestStatus status);

    /** The whole queue, newest first — decided requests stay visible as the record of who asked. */
    List<TrialRequest> findAllByOrderByCreatedAtDesc();
}
