package com.calyvora.work;

import com.calyvora.common.error.ConflictException;
import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.TenantContext;
import com.calyvora.work.dto.CreateSprintRequest;
import com.calyvora.work.dto.SprintResponse;
import com.calyvora.work.dto.UpdateSprintRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Sprints (Work OS slice S1). At most one ACTIVE per project; completing carries unfinished tasks to the backlog. */
@Service
public class SprintService {

    private final SprintRepository sprintRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;

    public SprintService(SprintRepository sprintRepository, ProjectRepository projectRepository,
                         TaskRepository taskRepository) {
        this.sprintRepository = sprintRepository;
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
    }

    @Transactional(readOnly = true)
    public List<SprintResponse> list(UUID projectId) {
        requireProject(projectId);
        return sprintRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public SprintResponse create(UUID projectId, CreateSprintRequest request) {
        Project project = requireProject(projectId);
        Sprint sprint = new Sprint(UUID.randomUUID(), project.getCompanyId(), projectId,
                request.name().trim(), blankToNull(request.goal()),
                parseDate(request.startDate()), parseDate(request.endDate()));
        sprintRepository.save(sprint);
        return toResponse(sprint);
    }

    @Transactional
    public SprintResponse update(UUID id, UpdateSprintRequest request) {
        Sprint sprint = require(id);
        if (request.name() != null && !request.name().isBlank()) {
            sprint.setName(request.name().trim());
        }
        if (request.goal() != null) {
            sprint.setGoal(blankToNull(request.goal()));
        }
        if (request.startDate() != null) {
            sprint.setStartDate(parseDate(request.startDate()));
        }
        if (request.endDate() != null) {
            sprint.setEndDate(parseDate(request.endDate()));
        }
        return toResponse(sprint);
    }

    @Transactional
    public SprintResponse start(UUID id) {
        Sprint sprint = require(id);
        if (sprint.getStatus() == SprintStatus.COMPLETED) {
            throw new ConflictException("A completed sprint cannot be started");
        }
        sprintRepository.findByProjectIdAndStatus(sprint.getProjectId(), SprintStatus.ACTIVE)
                .filter(active -> !active.getId().equals(sprint.getId()))
                .ifPresent(active -> {
                    throw new ConflictException("This project already has an active sprint");
                });
        sprint.setStatus(SprintStatus.ACTIVE);
        return toResponse(sprint);
    }

    @Transactional
    public SprintResponse complete(UUID id) {
        Sprint sprint = require(id);
        if (sprint.getStatus() != SprintStatus.ACTIVE) {
            throw new ConflictException("Only an active sprint can be completed");
        }
        // Carry unfinished work back to the backlog (SD-20).
        for (Task task : taskRepository.findBySprintId(sprint.getId())) {
            if (task.getStatus() != TaskStatus.DONE) {
                task.setSprintId(null);
            }
        }
        sprint.setStatus(SprintStatus.COMPLETED);
        return toResponse(sprint);
    }

    @Transactional
    public void delete(UUID id) {
        Sprint sprint = require(id);
        // Detach tasks so they return to the backlog, then delete the sprint.
        for (Task task : taskRepository.findBySprintId(sprint.getId())) {
            task.setSprintId(null);
        }
        sprintRepository.delete(sprint);
    }

    // ---- helpers ----

    private Project requireProject(UUID projectId) {
        UUID companyId = TenantContext.getCompanyId();
        return projectRepository.findByIdAndCompanyId(projectId, companyId)
                .orElseThrow(() -> new NotFoundException("Project not found"));
    }

    private Sprint require(UUID id) {
        UUID companyId = TenantContext.getCompanyId();
        return sprintRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("Sprint not found"));
    }

    private SprintResponse toResponse(Sprint s) {
        long total = taskRepository.countBySprintId(s.getId());
        long done = taskRepository.countBySprintIdAndStatus(s.getId(), TaskStatus.DONE);
        return SprintResponse.of(s, total, done);
    }

    private static LocalDate parseDate(String s) {
        return (s == null || s.isBlank()) ? null : LocalDate.parse(s);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
