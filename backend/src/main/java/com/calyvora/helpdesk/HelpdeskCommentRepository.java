package com.calyvora.helpdesk;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HelpdeskCommentRepository extends JpaRepository<HelpdeskComment, UUID> {

    List<HelpdeskComment> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);

    long countByTicketId(UUID ticketId);
}
