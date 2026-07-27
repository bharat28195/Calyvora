package com.calyvora.platform;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeatRequestRepository extends JpaRepository<SeatRequest, UUID> {

    List<SeatRequest> findByStatusOrderByCreatedAtAsc(SeatRequestStatus status);

    Optional<SeatRequest> findByCompanyIdAndStatus(UUID companyId, SeatRequestStatus status);

    List<SeatRequest> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);
}
