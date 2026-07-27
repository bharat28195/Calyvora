package com.calyvora.recruit;

import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.CurrentUser;
import com.calyvora.recruit.dto.CandidatePayload;
import com.calyvora.recruit.dto.CandidateResponse;
import com.calyvora.recruit.dto.JobOpeningPayload;
import com.calyvora.recruit.dto.JobOpeningResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Recruitment / ATS (Owner/Admin). Base {@code /api/v1/recruit}. */
@RestController
@RequestMapping("/api/v1/recruit")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','HR')")
public class RecruitController {

    private final RecruitService service;

    public RecruitController(RecruitService service) {
        this.service = service;
    }

    // ---- jobs ----

    @GetMapping("/jobs")
    public List<JobOpeningResponse> jobs() {
        return service.jobs();
    }

    @GetMapping("/jobs/{id}")
    public JobOpeningResponse job(@PathVariable UUID id) {
        return service.job(id);
    }

    @PostMapping("/jobs")
    @ResponseStatus(HttpStatus.CREATED)
    public JobOpeningResponse createJob(@Valid @RequestBody JobOpeningPayload req,
                                        @CurrentUser AuthPrincipal principal) {
        return service.createJob(req, principal);
    }

    @PatchMapping("/jobs/{id}")
    public JobOpeningResponse updateJob(@PathVariable UUID id, @Valid @RequestBody JobOpeningPayload req) {
        return service.updateJob(id, req);
    }

    @DeleteMapping("/jobs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteJob(@PathVariable UUID id) {
        service.deleteJob(id);
    }

    // ---- candidates ----

    @GetMapping("/jobs/{id}/candidates")
    public List<CandidateResponse> candidates(@PathVariable UUID id) {
        return service.candidates(id);
    }

    @PostMapping("/jobs/{id}/candidates")
    @ResponseStatus(HttpStatus.CREATED)
    public CandidateResponse addCandidate(@PathVariable UUID id, @Valid @RequestBody CandidatePayload req) {
        return service.addCandidate(id, req);
    }

    @PatchMapping("/candidates/{id}")
    public CandidateResponse updateCandidate(@PathVariable UUID id, @Valid @RequestBody CandidatePayload req) {
        return service.updateCandidate(id, req);
    }

    @PostMapping("/candidates/{id}/move")
    public CandidateResponse moveStage(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        return service.moveStage(id, body.get("stage"));
    }

    @DeleteMapping("/candidates/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCandidate(@PathVariable UUID id) {
        service.deleteCandidate(id);
    }
}
