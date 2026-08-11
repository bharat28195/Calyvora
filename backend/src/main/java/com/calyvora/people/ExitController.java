package com.calyvora.people;

import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.CurrentUser;
import com.calyvora.people.dto.ExitResponse;
import com.calyvora.people.dto.StartExitRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Exit formalities (PD-20). Starting, cancelling and completing an exit are HR/admin decisions —
 * a manager works the clearance checklist (see {@link OnboardingController}) but does not decide
 * that somebody is leaving, or declare them left.
 */
@RestController
@RequestMapping("/api/v1/people")
public class ExitController {

    private final ExitService exitService;

    public ExitController(ExitService exitService) {
        this.exitService = exitService;
    }

    /** Everyone currently serving notice. Managers can see it too — it is their queue of work. */
    @GetMapping("/exits")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','HR','MANAGER')")
    public List<ExitResponse> leaving(@CurrentUser AuthPrincipal principal) {
        return exitService.leaving(principal);
    }

    @GetMapping("/employees/{employeeId}/exit")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','HR','MANAGER')")
    public ExitResponse get(@PathVariable UUID employeeId, @CurrentUser AuthPrincipal principal) {
        return exitService.get(employeeId, principal);
    }

    @PostMapping("/employees/{employeeId}/exit")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','HR')")
    public ExitResponse start(@PathVariable UUID employeeId,
                              @Valid @RequestBody StartExitRequest request,
                              @CurrentUser AuthPrincipal principal) {
        return exitService.start(employeeId, request, principal);
    }

    /** Resignation withdrawn. */
    @DeleteMapping("/employees/{employeeId}/exit")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','HR')")
    public ExitResponse cancel(@PathVariable UUID employeeId, @CurrentUser AuthPrincipal principal) {
        return exitService.cancel(employeeId, principal);
    }

    /**
     * Mark them left and issue the closing letters. Refused while clearance is outstanding unless
     * {@code force=true} — see {@link ExitService#complete}.
     */
    @PostMapping("/employees/{employeeId}/exit/complete")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','HR')")
    public ExitResponse complete(@PathVariable UUID employeeId,
                                 @RequestParam(defaultValue = "false") boolean force,
                                 @CurrentUser AuthPrincipal principal) {
        return exitService.complete(employeeId, force, principal);
    }
}
