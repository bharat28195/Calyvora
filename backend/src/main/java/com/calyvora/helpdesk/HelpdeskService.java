package com.calyvora.helpdesk;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.error.ForbiddenException;
import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.dto.CursorPage;
import com.calyvora.common.security.TenantContext;
import com.calyvora.common.web.Cursors;
import com.calyvora.helpdesk.dto.CommentPayload;
import com.calyvora.helpdesk.dto.CommentResponse;
import com.calyvora.helpdesk.dto.RaiseTicketRequest;
import com.calyvora.helpdesk.dto.TicketResponse;
import com.calyvora.helpdesk.dto.UpdateTicketRequest;
import com.calyvora.identity.Role;
import com.calyvora.identity.User;
import com.calyvora.identity.UserRepository;
import com.calyvora.notification.NotificationService;
import com.calyvora.notification.NotificationType;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HR Helpdesk (roadmap #2): employees raise HR/payroll/IT queries and track them to resolution; HR
 * agents (ADMIN/HR) triage a queue, reply, assign and move status. Tenant-scoped throughout.
 */
@Service
public class HelpdeskService {

    private final HelpdeskTicketRepository ticketRepository;
    private final HelpdeskCommentRepository commentRepository;
    private final UserRepository userRepository;
    private final NotificationService notifications;

    public HelpdeskService(HelpdeskTicketRepository ticketRepository, HelpdeskCommentRepository commentRepository,
                           UserRepository userRepository, NotificationService notifications) {
        this.ticketRepository = ticketRepository;
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.notifications = notifications;
    }

    // ---- employee ----

    @Transactional
    public TicketResponse raise(RaiseTicketRequest req, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        HelpdeskTicket t = new HelpdeskTicket(UUID.randomUUID(), companyId, principal.userId(),
                parseCategory(req.category()), req.subject().trim(), blankToNull(req.description()),
                parsePriority(req.priority()));
        ticketRepository.save(t);

        // Tell the HR agents there's a new ticket to action.
        notifications.sendAll(companyId, agentIds(companyId), principal.userId(),
                NotificationType.HELPDESK_RAISED, "New helpdesk ticket: " + t.getSubject(),
                t.getCategory().name().toLowerCase() + " · " + t.getPriority().name().toLowerCase(),
                "/helpdesk/" + t.getId(), "HELPDESK", t.getId());
        return toResponse(t, names(companyId));
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> myTickets(AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        Map<UUID, String> names = names(companyId);
        return ticketRepository.findByCompanyIdAndRaisedByOrderByCreatedAtDesc(companyId, principal.userId())
                .stream().map(t -> toResponse(t, names)).toList();
    }

    // ---- HR queue ----

    /**
     * The agent queue, newest first, one page at a time.
     *
     * <p>This read had no ceiling: every ticket the company had ever raised, which grows forever and
     * is mostly closed. The status filter existed but was applied to the rows after they arrived, so
     * asking for the open ones still read the whole history to find them — and under paging that is
     * not merely slower, it is wrong: a page of the fifty newest tickets can be entirely closed, and
     * the screen would show an empty queue with the open ones stranded on a later page.
     */
    @Transactional(readOnly = true)
    public CursorPage<TicketResponse> queue(String status, String cursor, Integer size) {
        UUID companyId = TenantContext.getCompanyId();
        int limit = Cursors.limit(size);
        Cursors.Position from = Cursors.decode(cursor);
        Collection<TicketStatus> statuses = status == null || status.isBlank()
                ? java.util.EnumSet.allOf(TicketStatus.class)
                : java.util.EnumSet.of(parseStatus(status));

        // One extra row, which is how the page knows whether to offer a cursor without counting the
        // whole table to find out.
        List<HelpdeskTicket> rows = ticketRepository.pageForCompany(
                companyId, statuses, from.createdAt(), from.id(), PageRequest.of(0, limit + 1));

        Map<UUID, String> names = names(companyId);
        return Cursors.of(rows, limit, t -> toResponse(t, names),
                HelpdeskTicket::getCreatedAt, HelpdeskTicket::getId);
    }

    // ---- one ticket + thread ----

    @Transactional(readOnly = true)
    public TicketResponse ticket(UUID id, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        HelpdeskTicket t = requireVisible(id, companyId, principal);
        return toResponse(t, names(companyId));
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> comments(UUID id, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        requireVisible(id, companyId, principal);
        Map<UUID, String> names = names(companyId);
        return commentRepository.findByTicketIdOrderByCreatedAtAsc(id).stream()
                .map(c -> CommentResponse.of(c, names)).toList();
    }

    @Transactional
    public CommentResponse addComment(UUID id, CommentPayload payload, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        HelpdeskTicket t = requireVisible(id, companyId, principal);
        HelpdeskComment c = new HelpdeskComment(UUID.randomUUID(), companyId, id, principal.userId(),
                payload.body().trim());
        commentRepository.save(c);

        // Notify the other side of the conversation.
        boolean byAgent = isAgent(principal);
        UUID notifyUser = byAgent ? t.getRaisedBy() : (t.getAssigneeId() != null ? t.getAssigneeId() : null);
        if (byAgent) {
            notifications.send(companyId, t.getRaisedBy(), principal.userId(), NotificationType.HELPDESK_UPDATED,
                    "Reply on: " + t.getSubject(), preview(payload.body()), "/helpdesk/" + id, "HELPDESK", id);
        } else if (notifyUser != null) {
            notifications.send(companyId, notifyUser, principal.userId(), NotificationType.HELPDESK_UPDATED,
                    "Reply on: " + t.getSubject(), preview(payload.body()), "/helpdesk/" + id, "HELPDESK", id);
        } else {
            notifications.sendAll(companyId, agentIds(companyId), principal.userId(),
                    NotificationType.HELPDESK_UPDATED, "Reply on: " + t.getSubject(), preview(payload.body()),
                    "/helpdesk/" + id, "HELPDESK", id);
        }
        return CommentResponse.of(c, names(companyId));
    }

    @Transactional
    public TicketResponse update(UUID id, UpdateTicketRequest req, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        HelpdeskTicket t = ticketRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("Ticket not found"));
        if (req.category() != null) t.setCategory(parseCategory(req.category()));
        if (req.priority() != null) t.setPriority(parsePriority(req.priority()));
        if (req.assigneeId() != null) {
            t.setAssigneeId(req.assigneeId().isBlank() ? null : resolveUser(companyId, req.assigneeId()));
        }
        if (req.status() != null) {
            TicketStatus next = parseStatus(req.status());
            t.setStatus(next);
            t.setResolvedAt(next == TicketStatus.RESOLVED || next == TicketStatus.CLOSED ? Instant.now() : null);
            notifications.send(companyId, t.getRaisedBy(), principal.userId(), NotificationType.HELPDESK_UPDATED,
                    "Ticket " + next.name().toLowerCase().replace('_', ' ') + ": " + t.getSubject(),
                    null, "/helpdesk/" + id, "HELPDESK", id);
        }
        return toResponse(t, names(companyId));
    }

    // ---- helpers ----

    private TicketResponse toResponse(HelpdeskTicket t, Map<UUID, String> names) {
        return TicketResponse.of(t, names, commentRepository.countByTicketId(t.getId()));
    }

    /** A ticket is visible to the employee who raised it and to any HR agent. */
    private HelpdeskTicket requireVisible(UUID id, UUID companyId, AuthPrincipal principal) {
        HelpdeskTicket t = ticketRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("Ticket not found"));
        if (!t.getRaisedBy().equals(principal.userId()) && !isAgent(principal)) {
            throw new ForbiddenException("You can't view this ticket");
        }
        return t;
    }

    private boolean isAgent(AuthPrincipal principal) {
        String r = principal.role();
        return "ADMIN".equals(r) || "HR".equals(r) || "OWNER".equals(r);
    }

    private Map<UUID, String> names(UUID companyId) {
        Map<UUID, String> map = new HashMap<>();
        for (User u : userRepository.findByCompanyIdOrderByCreatedAtAsc(companyId)) {
            map.put(u.getId(), (u.getFirstName() + " " + u.getLastName()).trim());
        }
        return map;
    }

    private List<UUID> agentIds(UUID companyId) {
        return userRepository.findByCompanyIdOrderByCreatedAtAsc(companyId).stream()
                .filter(u -> u.getRole() == Role.ADMIN || u.getRole() == Role.HR)
                .map(User::getId).toList();
    }

    private UUID resolveUser(UUID companyId, String userId) {
        UUID id;
        try {
            id = UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Invalid assignee");
        }
        userRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("Assignee not found"));
        return id;
    }

    private static String preview(String body) {
        String s = body.trim();
        return s.length() > 120 ? s.substring(0, 117) + "…" : s;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static TicketCategory parseCategory(String s) {
        try { return TicketCategory.valueOf(s.trim().toUpperCase()); }
        catch (RuntimeException e) { throw new ApiException(ErrorCode.VALIDATION_ERROR, "Invalid category"); }
    }

    private static TicketPriority parsePriority(String s) {
        if (s == null || s.isBlank()) return TicketPriority.MEDIUM;
        try { return TicketPriority.valueOf(s.trim().toUpperCase()); }
        catch (RuntimeException e) { throw new ApiException(ErrorCode.VALIDATION_ERROR, "Invalid priority"); }
    }

    private static TicketStatus parseStatus(String s) {
        try { return TicketStatus.valueOf(s.trim().toUpperCase()); }
        catch (RuntimeException e) { throw new ApiException(ErrorCode.VALIDATION_ERROR, "Invalid status"); }
    }
}
