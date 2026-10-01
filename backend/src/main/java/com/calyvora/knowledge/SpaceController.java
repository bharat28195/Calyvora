package com.calyvora.knowledge;

import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.CurrentUser;
import com.calyvora.knowledge.dto.CreateSpaceRequest;
import com.calyvora.knowledge.dto.SpaceResponse;
import com.calyvora.knowledge.dto.UpdateSpaceRequest;
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

/** Knowledge OS — spaces (slice K1). Base {@code /api/v1/knowledge/spaces}. */
@RestController
@RequestMapping("/api/v1/knowledge/spaces")
public class SpaceController {

    private final SpaceService spaceService;

    public SpaceController(SpaceService spaceService) {
        this.spaceService = spaceService;
    }

    @GetMapping
    public List<SpaceResponse> list() {
        return spaceService.list();
    }

    @GetMapping("/{id}")
    public SpaceResponse get(@PathVariable UUID id) {
        return spaceService.get(id);
    }

    @PostMapping
    public ResponseEntity<SpaceResponse> create(@Valid @RequestBody CreateSpaceRequest request,
                                                @CurrentUser AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(spaceService.create(request, principal));
    }

    @PatchMapping("/{id}")
    public SpaceResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateSpaceRequest request) {
        return spaceService.update(id, request);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public SpaceResponse archive(@PathVariable UUID id) {
        return spaceService.archive(id);
    }
}
