package com.calyvora.work;

import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.CurrentUser;
import com.calyvora.work.dto.CreateTaskRequest;
import com.calyvora.work.dto.TaskResponse;
import com.calyvora.work.dto.UpdateTaskRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Work OS — tasks (slice W2) + my work (W3). */
@RestController
@RequestMapping("/api/v1/work")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/projects/{projectId}/tasks")
    public List<TaskResponse> listForProject(@PathVariable UUID projectId) {
        return taskService.listForProject(projectId);
    }

    @PostMapping("/projects/{projectId}/tasks")
    public ResponseEntity<TaskResponse> create(@PathVariable UUID projectId,
                                               @Valid @RequestBody CreateTaskRequest request,
                                               @CurrentUser AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(taskService.create(projectId, request, principal));
    }

    @PatchMapping("/tasks/{id}")
    public TaskResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateTaskRequest request) {
        return taskService.update(id, request);
    }

    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        taskService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Tasks assigned to me across all projects (still open). */
    @GetMapping("/tasks/mine")
    public List<TaskResponse> mine(@CurrentUser AuthPrincipal principal) {
        return taskService.mine(principal);
    }
}
