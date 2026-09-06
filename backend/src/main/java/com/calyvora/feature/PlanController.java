package com.calyvora.feature;

import com.calyvora.feature.dto.PlanPayload;
import com.calyvora.feature.dto.PlanResponse;
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

/**
 * The plan catalogue — what you sell and what each package includes.
 *
 * <p>Under /platform because it is the vendor's, not a tenant's: a customer sees the effect of their
 * plan on their own features endpoint, never the catalogue or anybody else's package.
 *
 * <p>There is no delete. Companies point at plan codes, and a deleted plan would leave them pointing
 * at nothing — silently reverting each to feature defaults, which means silently giving them the
 * whole product. Retiring a plan (active = false) keeps existing customers on it and stops it being
 * assigned to anybody new.
 */
@RestController
@RequestMapping("/api/v1/platform/plans")
@PreAuthorize("hasRole('OWNER') and @platformAccess.granted()")
public class PlanController {

    private final PlanService service;

    public PlanController(PlanService service) {
        this.service = service;
    }

    @GetMapping
    public List<PlanResponse> list() {
        return service.list();
    }

    @PostMapping
    public ResponseEntity<PlanResponse> create(@Valid @RequestBody PlanPayload payload) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(payload));
    }

    @PatchMapping("/{code}")
    public PlanResponse update(@PathVariable String code, @Valid @RequestBody PlanPayload payload) {
        return service.update(code, payload);
    }
}
