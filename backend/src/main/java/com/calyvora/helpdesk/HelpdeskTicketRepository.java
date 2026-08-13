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

    /**
     * For the assistant: everything still unresolved. A ticket someone has picked up is still a
     * ticket the employee is waiting on, so "open" here has to mean OPEN <em>and</em> IN_PROGRESS —
     * counting only OPEN understates the queue and makes HR think they are on top of it.
     */
    long countByCompanyIdAndStatusIn(UUID companyId, List<TicketStatus> statuses);
}
