package com.calyvora.work;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.TenantContext;
import com.calyvora.identity.User;
import com.calyvora.identity.UserRepository;
import com.calyvora.people.Employee;
import com.calyvora.people.EmployeeRepository;
import com.calyvora.work.dto.BoardResponse;
import com.calyvora.work.dto.CreateTaskRequest;
import com.calyvora.work.dto.SprintResponse;
import com.calyvora.work.dto.TaskResponse;
import com.calyvora.work.dto.UpdateTaskRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tasks + board (Work OS slice W2) and "My work" (W3). Assignees are People OS {@link Employee}s —
 * the cross-app link. All tenant-scoped.
 */
@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final SprintRepository sprintRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;

    public TaskService(TaskRepository taskRepository, ProjectRepository projectRepository,
                       SprintRepository sprintRepository, EmployeeRepository employeeRepository,
                       UserRepository userRepository) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.sprintRepository = sprintRepository;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> listForProject(UUID projectId) {
        Project project = requireProject(projectId);
        Map<UUID, String> names = new HashMap<>();
        return taskRepository.findByProjectIdOrderBySortOrderAscNumberAsc(projectId).stream()
                .map(t -> TaskResponse.of(t, project.getKey(), assigneeName(names, t.getAssigneeId())))
                .toList();
    }

    /** The backlog: tasks in the project not assigned to any sprint. */
    @Transactional(readOnly = true)
    public List<TaskResponse> backlog(UUID projectId) {
        Project project = requireProject(projectId);
        Map<UUID, String> names = new HashMap<>();
        return taskRepository.findByProjectIdAndSprintIdIsNullOrderBySortOrderAscNumberAsc(projectId).stream()
                .map(t -> TaskResponse.of(t, project.getKey(), assigneeName(names, t.getAssigneeId())))
                .toList();
    }

    /**
     * The board: the active sprint's tasks, or — if the project has no active sprint — the un-sprinted
     * tasks so Work is usable before any sprint exists (SD-21).
     */
    @Transactional(readOnly = true)
    public BoardResponse board(UUID projectId) {
        Project project = requireProject(projectId);
        Sprint active = sprintRepository.findByProjectIdAndStatus(projectId, SprintStatus.ACTIVE).orElse(null);
        List<Task> tasks = active != null
                ? taskRepository.findBySprintIdOrderBySortOrderAscNumberAsc(active.getId())
                : taskRepository.findByProjectIdAndSprintIdIsNullOrderBySortOrderAscNumberAsc(projectId);
        Map<UUID, String> names = new HashMap<>();
        List<TaskResponse> taskResponses = tasks.stream()
                .map(t -> TaskResponse.of(t, project.getKey(), assigneeName(names, t.getAssigneeId())))
                .toList();
        SprintResponse activeSprint = active == null ? null : SprintResponse.of(active,
                taskRepository.countBySprintId(active.getId()),
                taskRepository.countBySprintIdAndStatus(active.getId(), TaskStatus.DONE));
        return new BoardResponse(activeSprint, taskResponses);
    }

    @Transactional
    public TaskResponse create(UUID projectId, CreateTaskRequest request, AuthPrincipal principal) {
        Project project = requireProject(projectId);
        int number = taskRepository.maxNumberForProject(projectId) + 1;
        Task task = new Task(UUID.randomUUID(), project.getCompanyId(), projectId, number,
                request.title().trim(), principal.userId());
        if (request.description() != null) task.setDescription(blankToNull(request.description()));
        if (request.priority() != null) task.setPriority(TaskPriority.valueOf(request.priority()));
        if (request.assigneeId() != null) task.setAssigneeId(resolveAssignee(project.getCompanyId(), request.assigneeId()));
        if (request.dueDate() != null && !request.dueDate().isBlank()) task.setDueDate(LocalDate.parse(request.dueDate()));
        task.setSortOrder(number);
        taskRepository.save(task);
        return TaskResponse.of(task, project.getKey(), assigneeName(new HashMap<>(), task.getAssigneeId()));
    }

    @Transactional
    public TaskResponse update(UUID taskId, UpdateTaskRequest request) {
        UUID companyId = TenantContext.getCompanyId();
        Task task = taskRepository.findByIdAndCompanyId(taskId, companyId)
                .orElseThrow(() -> new NotFoundException("Task not found"));

        if (request.title() != null && !request.title().isBlank()) task.setTitle(request.title().trim());
        if (request.description() != null) task.setDescription(blankToNull(request.description()));
        if (request.status() != null) task.setStatus(TaskStatus.valueOf(request.status()));
        if (request.priority() != null) task.setPriority(TaskPriority.valueOf(request.priority()));
        if (request.assigneeId() != null) task.setAssigneeId(resolveAssignee(companyId, request.assigneeId()));
        if (request.sprintId() != null) task.setSprintId(resolveSprint(companyId, task.getProjectId(), request.sprintId()));
        if (request.dueDate() != null) {
            task.setDueDate(request.dueDate().isBlank() ? null : LocalDate.parse(request.dueDate()));
        }
        Project project = projectRepository.findById(task.getProjectId()).orElseThrow();
        return TaskResponse.of(task, project.getKey(), assigneeName(new HashMap<>(), task.getAssigneeId()));
    }

    @Transactional
    public void delete(UUID taskId) {
        UUID companyId = TenantContext.getCompanyId();
        Task task = taskRepository.findByIdAndCompanyId(taskId, companyId)
                .orElseThrow(() -> new NotFoundException("Task not found"));
        taskRepository.delete(task);
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> mine(AuthPrincipal principal) {
        Employee me = employeeRepository.findByUserId(principal.userId()).orElse(null);
        if (me == null) {
            return List.of();
        }
        Map<UUID, String> keys = new HashMap<>();
        String myName = userRepository.findById(principal.userId()).map(User::fullName).orElse(null);
        return taskRepository
                .findByAssigneeIdAndStatusNotOrderByDueDateAscCreatedAtAsc(me.getId(), TaskStatus.DONE).stream()
                .map(t -> TaskResponse.of(t,
                        keys.computeIfAbsent(t.getProjectId(), this::projectKey), myName))
                .toList();
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

    /** Resolve an optional sprint for a task; blank clears it (moves the task to the backlog). */
    private UUID resolveSprint(UUID companyId, UUID projectId, String sprintId) {
        if (sprintId.isBlank()) {
            return null;
        }
        UUID id;
        try {
            id = UUID.fromString(sprintId);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Invalid sprint id");
        }
        Sprint sprint = sprintRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("Sprint not found"));
        if (!sprint.getProjectId().equals(projectId)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Sprint belongs to a different project");
        }
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

    private String projectKey(UUID projectId) {
        return projectRepository.findById(projectId).map(Project::getKey).orElse("");
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
