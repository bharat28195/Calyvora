package com.calyvora.performance;

import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.CurrentUser;
import com.calyvora.performance.dto.CreateCycleRequest;
import com.calyvora.performance.dto.ManagerReviewRequest;
import com.calyvora.performance.dto.PerformanceReviewResponse;
import com.calyvora.performance.dto.ReviewCycleResponse;
import com.calyvora.performance.dto.SelfAssessmentRequest;
import jakarta.validation.Valid;
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

/**
 * Performance reviews (founder request C.7). Base {@code /api/v1/performance}.
 *
 * <p>Cycle administration is Owner/Admin-only; the self and manager endpoints are open to any
 * authenticated user, with the service enforcing that you may only touch your own review (self side)
 * or your reports' reviews (manager side).
 */
@RestController
@RequestMapping("/api/v1/performance")
public class PerformanceController {

    private final PerformanceReviewService service;

    public PerformanceController(PerformanceReviewService service) {
        this.service = service;
    }

    // ---- cycles (admin) ----

    @PostMapping("/cycles")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','HR')")
    public ReviewCycleResponse createCycle(@Valid @RequestBody CreateCycleRequest req,
                                           @CurrentUser AuthPrincipal principal) {
        return service.createCycle(req, principal);
    }

    @GetMapping("/cycles")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','HR')")
    public List<ReviewCycleResponse> cycles() {
        return service.cycles();
    }

    @GetMapping("/cycles/{id}/reviews")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','HR')")
    public List<PerformanceReviewResponse> cycleReviews(@PathVariable UUID id) {
        return service.cycleReviews(id);
    }

    @PostMapping("/cycles/{id}/close")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','HR')")
    public ReviewCycleResponse closeCycle(@PathVariable UUID id) {
        return service.closeCycle(id);
    }

    // ---- my reviews (self) ----

    @GetMapping("/me/reviews")
    public List<PerformanceReviewResponse> myReviews(@CurrentUser AuthPrincipal principal) {
        return service.myReviews(principal);
    }

    // ---- team reviews (manager) ----

    @GetMapping("/team/reviews")
    public List<PerformanceReviewResponse> teamReviews(@CurrentUser AuthPrincipal principal) {
        return service.teamReviews(principal);
    }

    // ---- a single review ----

    @GetMapping("/reviews/{id}")
    public PerformanceReviewResponse get(@PathVariable UUID id, @CurrentUser AuthPrincipal principal) {
        return service.get(id, principal);
    }

    @PatchMapping("/reviews/{id}/self")
    public PerformanceReviewResponse saveSelf(@PathVariable UUID id,
                                              @Valid @RequestBody SelfAssessmentRequest req,
                                              @CurrentUser AuthPrincipal principal) {
        return service.saveSelf(id, req, principal);
    }

    @PatchMapping("/reviews/{id}/manager")
    public PerformanceReviewResponse saveManager(@PathVariable UUID id,
                                                 @Valid @RequestBody ManagerReviewRequest req,
                                                 @CurrentUser AuthPrincipal principal) {
        return service.saveManager(id, req, principal);
    }

    @PostMapping("/reviews/{id}/approve")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','HR')")
    public PerformanceReviewResponse approve(@PathVariable UUID id, @CurrentUser AuthPrincipal principal) {
        return service.approve(id, principal);
    }
}
