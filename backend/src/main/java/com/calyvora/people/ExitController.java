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

    /**
     * Who is serving notice: the whole company for HR and leadership, your own org for everyone else.
     *
     * <p>No role check, deliberately. The old one — OWNER/ADMIN/HR/MANAGER — got both halves wrong at
     * once: it handed every manager every resignation in the business, including departments they have
     * nothing to do with, and it denied the screen entirely to a senior engineer with two interns
     * whose exit clearance is just as much their job. {@link ExitService#leaving} scopes to the
     * reporting tree instead, and answers an empty list to somebody who leads nobody.
     */
    @GetMapping("/exits")
    public List<ExitResponse> leaving(@CurrentUser AuthPrincipal principal) {
        return exitService.leaving(principal);
    }

    /** One person's exit. 403 unless they are in the caller's org, or the caller is HR/leadership. */
    @GetMapping("/employees/{employeeId}/exit")
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
