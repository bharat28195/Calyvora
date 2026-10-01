package com.calyvora.people;

import com.calyvora.people.dto.DesignationRequest;
import com.calyvora.people.dto.DesignationResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The company's own ladder. Readable by everyone, editable by ADMIN and HR.
 *
 * <p>Readable by everyone because it is a label that appears next to names in the directory, and a
 * picker that 403s for the person filling in a profile is not a picker. Editing is people-ops work.
 */
@RestController
@RequestMapping("/api/v1/designations")
public class DesignationController {

    private final DesignationService designationService;

    public DesignationController(DesignationService designationService) {
        this.designationService = designationService;
    }

    @GetMapping
    public List<DesignationResponse> list(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return designationService.list(includeArchived);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','HR')")
    public DesignationResponse create(@Valid @RequestBody DesignationRequest request) {
        return designationService.create(request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','HR')")
    public DesignationResponse update(@PathVariable UUID id, @Valid @RequestBody DesignationRequest request) {
        return designationService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','HR')")
    public void delete(@PathVariable UUID id) {
        designationService.delete(id);
    }
}
