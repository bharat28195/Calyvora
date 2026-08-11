package com.calyvora.people;

import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.CurrentUser;
import com.calyvora.people.dto.AddOnboardingTaskRequest;
import com.calyvora.people.dto.OnboardingTaskResponse;
import com.calyvora.people.dto.ToggleOnboardingTaskRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

/** People OS — onboarding checklists (slice P3). */
@RestController
@RequestMapping("/api/v1/people")
public class OnboardingController {

    private final OnboardingService onboardingService;

    public OnboardingController(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @GetMapping("/employees/{employeeId}/onboarding")
    public List<OnboardingTaskResponse> list(@PathVariable UUID employeeId, @CurrentUser AuthPrincipal principal) {
        return onboardingService.list(employeeId, ChecklistKind.ONBOARDING, principal);
    }

    @PostMapping("/employees/{employeeId}/onboarding")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','HR')")
    public ResponseEntity<OnboardingTaskResponse> add(@PathVariable UUID employeeId,
                                                      @Valid @RequestBody AddOnboardingTaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(onboardingService.add(employeeId, ChecklistKind.ONBOARDING, request.title()));
    }

    @PostMapping("/employees/{employeeId}/onboarding/seed-defaults")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','HR')")
    public List<OnboardingTaskResponse> seedDefaults(@PathVariable UUID employeeId) {
        return onboardingService.seedDefaults(employeeId, ChecklistKind.ONBOARDING);
    }

    // ---- exit checklist (PD-20) ----
    //
    // Separate paths rather than a ?kind= parameter on the onboarding ones: these are worked by a
    // different person, on a different screen, and the manager role gate below only makes sense for
    // the exit list. A shared endpoint would have to explain that in a conditional.

    /** The leaver's manager, HR or an admin — enforced in the service, which knows who manages whom. */
    @GetMapping("/employees/{employeeId}/exit-checklist")
    public List<OnboardingTaskResponse> exitChecklist(@PathVariable UUID employeeId,
                                                      @CurrentUser AuthPrincipal principal) {
        return onboardingService.list(employeeId, ChecklistKind.EXIT, principal);
    }

    @PostMapping("/employees/{employeeId}/exit-checklist")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','HR')")
    public ResponseEntity<OnboardingTaskResponse> addExitTask(@PathVariable UUID employeeId,
                                                              @Valid @RequestBody AddOnboardingTaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(onboardingService.add(employeeId, ChecklistKind.EXIT, request.title()));
    }

    @PostMapping("/employees/{employeeId}/exit-checklist/seed-defaults")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','HR')")
    public List<OnboardingTaskResponse> seedExitDefaults(@PathVariable UUID employeeId) {
        return onboardingService.seedDefaults(employeeId, ChecklistKind.EXIT);
    }

    /**
     * Ticking works for both checklists through one route — the task knows which list it is on, and
     * the service applies that list's rule about who may tick it.
     */
    @PatchMapping("/onboarding/{taskId}")
    public OnboardingTaskResponse toggle(@PathVariable UUID taskId,
                                         @Valid @RequestBody ToggleOnboardingTaskRequest request,
                                         @CurrentUser AuthPrincipal principal) {
        return onboardingService.toggle(taskId, request.completed(), principal);
    }

    @DeleteMapping("/onboarding/{taskId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','HR')")
    public ResponseEntity<Void> delete(@PathVariable UUID taskId) {
        onboardingService.delete(taskId);
        return ResponseEntity.noContent().build();
    }
}
