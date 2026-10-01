package com.calyvora.client;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientRequestRepository extends JpaRepository<ClientRequest, UUID> {

    List<ClientRequest> findByClientIdOrderByCreatedAtDesc(UUID clientId);

    Optional<ClientRequest> findByIdAndCompanyId(UUID id, UUID companyId);
}
