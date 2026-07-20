package com.calyvora.work;

import com.calyvora.work.dto.CreateProjectRequest;
import com.calyvora.work.dto.ProjectResponse;
import com.calyvora.work.dto.UpdateProjectRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Work OS — projects (slice W1). Base {@code /api/v1/work/projects}. */
@RestController
@RequestMapping("/api/v1/work/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public List<ProjectResponse> list() {
        return projectService.list();
    }

    @GetMapping("/{id}")
    public ProjectResponse get(@PathVariable UUID id) {
        return projectService.get(id);
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody CreateProjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectService.create(request));
    }

    @PatchMapping("/{id}")
    public ProjectResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateProjectRequest request) {
        return projectService.update(id, request);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ProjectResponse archive(@PathVariable UUID id) {
        return projectService.archive(id);
    }
}
