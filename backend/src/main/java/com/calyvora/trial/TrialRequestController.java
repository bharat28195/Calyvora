package com.calyvora.trial;

import com.calyvora.trial.dto.TrialRequestForm;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The public half of the trial flow (PD-21): one endpoint, open to anyone, that records an enquiry.
 *
 * <p>There is deliberately no GET here. Who has asked for a trial is the vendor's commercial
 * information and lives behind the platform console; this surface can only add.
 */
@RestController
@RequestMapping("/api/v1/trial-requests")
public class TrialRequestController {

    private final TrialRequestService service;

    public TrialRequestController(TrialRequestService service) {
        this.service = service;
    }

    /**
     * The response says nothing about the enquiry itself — not its id, not whether it was a repeat —
     * because an anonymous caller must not be able to use this endpoint to find out who is already
     * talking to us. All it reports is whether an acknowledgement email actually went out, which the
     * page needs so it doesn't tell someone to check an inbox nothing is coming to.
     */
    @PostMapping
    public ResponseEntity<SubmitResponse> submit(@Valid @RequestBody TrialRequestForm form) {
        TrialRequestService.Result result = service.submit(form);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new SubmitResponse(true, result.acknowledged()));
    }

    public record SubmitResponse(boolean received, boolean emailSent) {}
}
