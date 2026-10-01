package com.calyvora.work;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.TenantContext;
import com.calyvora.identity.User;
import com.calyvora.identity.UserRepository;
import com.calyvora.people.EmployeeRepository;
import com.calyvora.work.dto.CreateTicketRequest;
import com.calyvora.work.dto.TicketResponse;
import com.calyvora.work.dto.UpdateTicketRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Support tickets (Work OS slice S3). Lightweight; assignee is a People OS employee (cross-app link).
 * Deliberate debt — graduates to Service OS (SD-22b). All tenant-scoped.
 */
@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final ProjectRepository projectRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;

    public TicketService(TicketRepository ticketRepository, ProjectRepository projectRepository,
                         EmployeeRepository employeeRepository, UserRepository userRepository) {
        this.ticketRepository = ticketRepository;
        this.projectRepository = projectRepository;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> listForProject(UUID projectId) {
        Project project = requireProject(projectId);
        Map<UUID, String> names = new HashMap<>();
        return ticketRepository.findByProjectIdOrderByNumberDesc(projectId).stream()
                .map(t -> TicketResponse.of(t, project.getKey(), assigneeName(names, t.getAssigneeId())))
                .toList();
    }

    @Transactional
    public TicketResponse create(UUID projectId, CreateTicketRequest request, AuthPrincipal principal) {
        Project project = requireProject(projectId);
        int number = ticketRepository.maxNumberForProject(projectId) + 1;
        Ticket ticket = new Ticket(UUID.randomUUID(), project.getCompanyId(), projectId, number,
                request.subject().trim(), principal.userId());
        if (request.description() != null) ticket.setDescription(blankToNull(request.description()));
        if (request.requesterName() != null) ticket.setRequesterName(blankToNull(request.requesterName()));
        if (request.requesterEmail() != null) ticket.setRequesterEmail(blankToNull(request.requesterEmail()));
        if (request.priority() != null) ticket.setPriority(TaskPriority.valueOf(request.priority()));
        if (request.assigneeId() != null) ticket.setAssigneeId(resolveAssignee(project.getCompanyId(), request.assigneeId()));
        ticketRepository.save(ticket);
        return TicketResponse.of(ticket, project.getKey(), assigneeName(new HashMap<>(), ticket.getAssigneeId()));
    }

    @Transactional
    public TicketResponse update(UUID id, UpdateTicketRequest request) {
        UUID companyId = TenantContext.getCompanyId();
        Ticket ticket = ticketRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("Ticket not found"));

        if (request.subject() != null && !request.subject().isBlank()) ticket.setSubject(request.subject().trim());
        if (request.description() != null) ticket.setDescription(blankToNull(request.description()));
        if (request.requesterName() != null) ticket.setRequesterName(blankToNull(request.requesterName()));
        if (request.requesterEmail() != null) ticket.setRequesterEmail(blankToNull(request.requesterEmail()));
        if (request.status() != null) ticket.setStatus(TicketStatus.valueOf(request.status()));
        if (request.priority() != null) ticket.setPriority(TaskPriority.valueOf(request.priority()));
        if (request.assigneeId() != null) ticket.setAssigneeId(resolveAssignee(companyId, request.assigneeId()));

        Project project = projectRepository.findById(ticket.getProjectId()).orElseThrow();
        return TicketResponse.of(ticket, project.getKey(), assigneeName(new HashMap<>(), ticket.getAssigneeId()));
    }

    @Transactional
    public void delete(UUID id) {
        UUID companyId = TenantContext.getCompanyId();
        Ticket ticket = ticketRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("Ticket not found"));
        ticketRepository.delete(ticket);
    }

    // ---- helpers ----

    private Project requireProject(UUID projectId) {
        UUID companyId = TenantContext.getCompanyId();
        return projectRepository.findByIdAndCompanyId(projectId, companyId)
                .orElseThrow(() -> new NotFoundException("Project not found"));
    }

    private UUID resolveAssignee(UUID companyId, String assigneeId) {
        if (assigneeId.isBlank()) {
            return null;
        }
        UUID id;
        try {
            id = UUID.fromString(assigneeId);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Invalid assignee id");
        }
        employeeRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("Assignee not found"));
        return id;
    }

    private String assigneeName(Map<UUID, String> cache, UUID assigneeId) {
        if (assigneeId == null) {
            return null;
        }
        return cache.computeIfAbsent(assigneeId, id -> employeeRepository.findById(id)
                .flatMap(e -> userRepository.findById(e.getUserId()))
                .map(User::fullName)
                .orElse(null));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
