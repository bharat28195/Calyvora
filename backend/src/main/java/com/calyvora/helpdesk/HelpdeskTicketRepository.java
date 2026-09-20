package com.calyvora.helpdesk;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HelpdeskTicketRepository extends JpaRepository<HelpdeskTicket, UUID> {

    List<HelpdeskTicket> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    /**
     * One page of the queue, newest first, from a cursor.
     *
     * <p>Sorted and compared on {@code (createdAt, id)} together. Tickets are raised in bursts — an
     * outage, a payroll question the whole office has at once — so rows sharing a creation instant
     * are normal here, and a boundary inside such a group would drop some and repeat others without
     * the tiebreaker.
     *
     * <p>Statuses are a collection so the predicate is always present: "any status" is the full set
     * rather than a null each query has to special-case.
     */
    @Query("select t from HelpdeskTicket t where t.companyId = :companyId "
            + "and t.status in :statuses "
            + "and (t.createdAt < :ts or (t.createdAt = :ts and t.id < :id)) "
            + "order by t.createdAt desc, t.id desc")
    List<HelpdeskTicket> pageForCompany(UUID companyId, Collection<TicketStatus> statuses,
                                        Instant ts, UUID id, Pageable limit);

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
