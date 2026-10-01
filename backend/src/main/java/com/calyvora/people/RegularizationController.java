package com.calyvora.people;

import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.CurrentUser;
import com.calyvora.people.dto.RegularizationRequest;
import com.calyvora.people.dto.RegularizationResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Attendance regularization — raise a fix-up for a missed day; managers/HR approve. */
@RestController
@RequestMapping("/api/v1/attendance/regularizations")
public class RegularizationController {

    private final RegularizationService service;

    public RegularizationController(RegularizationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RegularizationResponse raise(@Valid @RequestBody RegularizationRequest req,
                                        @CurrentUser AuthPrincipal principal) {
        return service.raise(req, principal);
    }

    @GetMapping("/mine")
    public List<RegularizationResponse> mine(@CurrentUser AuthPrincipal principal) {
        return service.mine(principal);
    }

    /** Requests the caller can approve (their reports', or all for HR/admin). */
    @GetMapping("/pending")
    public List<RegularizationResponse> pending(@CurrentUser AuthPrincipal principal) {
        return service.pending(principal);
    }

    @PostMapping("/{id}/approve")
    public RegularizationResponse approve(@PathVariable UUID id, @RequestBody(required = false) Map<String, String> body,
                                          @CurrentUser AuthPrincipal principal) {
        return service.decide(id, true, body == null ? null : body.get("note"), principal);
    }

    @PostMapping("/{id}/reject")
    public RegularizationResponse reject(@PathVariable UUID id, @RequestBody(required = false) Map<String, String> body,
                                         @CurrentUser AuthPrincipal principal) {
        return service.decide(id, false, body == null ? null : body.get("note"), principal);
    }
}
