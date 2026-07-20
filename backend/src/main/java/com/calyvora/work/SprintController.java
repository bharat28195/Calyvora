package com.calyvora.work;

import com.calyvora.work.dto.CreateSprintRequest;
import com.calyvora.work.dto.SprintResponse;
import com.calyvora.work.dto.UpdateSprintRequest;
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

/** Work OS — sprints (slice S1). */
@RestController
@RequestMapping("/api/v1/work")
public class SprintController {

    private final SprintService sprintService;

    public SprintController(SprintService sprintService) {
        this.sprintService = sprintService;
    }

    @GetMapping("/projects/{projectId}/sprints")
    public List<SprintResponse> list(@PathVariable UUID projectId) {
        return sprintService.list(projectId);
    }

    @PostMapping("/projects/{projectId}/sprints")
    public ResponseEntity<SprintResponse> create(@PathVariable UUID projectId,
                                                 @Valid @RequestBody CreateSprintRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sprintService.create(projectId, request));
    }

    @PatchMapping("/sprints/{id}")
    public SprintResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateSprintRequest request) {
        return sprintService.update(id, request);
    }

    @PostMapping("/sprints/{id}/start")
    public SprintResponse start(@PathVariable UUID id) {
        return sprintService.start(id);
    }

    @PostMapping("/sprints/{id}/complete")
    public SprintResponse complete(@PathVariable UUID id) {
        return sprintService.complete(id);
    }

    @DeleteMapping("/sprints/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        sprintService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
