package com.calyvora.helpdesk;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HelpdeskTicketRepository extends JpaRepository<HelpdeskTicket, UUID> {

    List<HelpdeskTicket> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    List<HelpdeskTicket> findByCompanyIdAndRaisedByOrderByCreatedAtDesc(UUID companyId, UUID raisedBy);

    Optional<HelpdeskTicket> findByIdAndCompanyId(UUID id, UUID companyId);

    long countByCompanyIdAndStatus(UUID companyId, TicketStatus status);
}
