package com.calyvora.work;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ConflictException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.TenantContext;
import com.calyvora.identity.User;
import com.calyvora.identity.UserRepository;
import com.calyvora.work.dto.CreateProjectRequest;
import com.calyvora.work.dto.ProjectResponse;
import com.calyvora.work.dto.UpdateProjectRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Projects (Work OS slice W1). Any member can create; archiving is gated at the controller. */
@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final com.calyvora.client.ClientRepository clientRepository;

    public ProjectService(ProjectRepository projectRepository, TaskRepository taskRepository,
                          UserRepository userRepository,
                          com.calyvora.client.ClientRepository clientRepository) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.clientRepository = clientRepository;
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> list() {
        UUID companyId = TenantContext.getCompanyId();
        return projectRepository.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(UUID id) {
        return toResponse(require(id));
    }

    @Transactional
    public ProjectResponse create(CreateProjectRequest request) {
        UUID companyId = TenantContext.getCompanyId();
        String key = request.key().trim().toUpperCase(Locale.ROOT);
        if (projectRepository.existsByCompanyIdAndKeyIgnoreCase(companyId, key)) {
            throw new ConflictException("A project with that key already exists");
        }
        Project project = new Project(UUID.randomUUID(), companyId, request.name().trim(), key,
                blankToNull(request.description()), resolveLead(companyId, request.leadUserId()));
        projectRepository.save(project);
        return toResponse(project);
    }

    @Transactional
    public ProjectResponse update(UUID id, UpdateProjectRequest request) {
        Project project = require(id);
        if (request.name() != null && !request.name().isBlank()) {
            project.setName(request.name().trim());
        }
        if (request.description() != null) {
            project.setDescription(blankToNull(request.description()));
        }
        if (request.leadUserId() != null) {
            project.setLeadUserId(resolveLead(project.getCompanyId(), request.leadUserId()));
        }
        if (request.clientId() != null) {
            project.setClientId(resolveClient(project.getCompanyId(), request.clientId()));
        }
        if (request.billable() != null) {
            project.setBillable(request.billable());
        }
        if (request.contractValue() != null) {
            // Negative clears the agreed fee; anything else is the fee itself.
            project.setContractValue(request.contractValue().signum() < 0 ? null : request.contractValue());
        }
        return toResponse(project);
    }

    @Transactional
    public ProjectResponse archive(UUID id) {
        Project project = require(id);
        project.setStatus(ProjectStatus.ARCHIVED);
        return toResponse(project);
    }

    // ---- helpers ----

    private Project require(UUID id) {
        UUID companyId = TenantContext.getCompanyId();
        return projectRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("Project not found"));
    }

    private ProjectResponse toResponse(Project p) {
        String leadName = p.getLeadUserId() == null ? null
                : userRepository.findById(p.getLeadUserId()).map(User::fullName).orElse(null);
        long total = taskRepository.countByProjectId(p.getId());
        long open = taskRepository.countByProjectIdAndStatusNot(p.getId(), TaskStatus.DONE);
        String clientName = p.getClientId() == null ? null
                : clientRepository.findById(p.getClientId()).map(com.calyvora.client.Client::getName).orElse(null);
        return ProjectResponse.of(p, leadName, total, open, clientName);
    }

    private UUID resolveLead(UUID companyId, String leadUserId) {
        if (leadUserId == null || leadUserId.isBlank()) {
            return null;
        }
        UUID lead;
        try {
            lead = UUID.fromString(leadUserId);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Invalid lead id");
        }
        userRepository.findByIdAndCompanyId(lead, companyId)
                .orElseThrow(() -> new NotFoundException("Lead user not found"));
        return lead;
    }

    /** Blank detaches the project from its client; anything else must be a client in this tenant. */
    private UUID resolveClient(UUID companyId, String clientId) {
        if (clientId.isBlank()) {
            return null;
        }
        UUID id;
        try {
            id = UUID.fromString(clientId);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Invalid client id");
        }
        clientRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("Client not found"));
        return id;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
