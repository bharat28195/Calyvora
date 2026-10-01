package com.calyvora.people;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.people.dto.PayrollJobResponse;
import com.calyvora.people.dto.PayrollRunResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HR/Admin payroll run — every employee's net for a month, after attendance LOP. */
@RestController
@RequestMapping("/api/v1/payroll")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','HR')")
public class PayrollRunController {

    private final CompensationService compensationService;
    private final PayrollJobService jobs;

    public PayrollRunController(CompensationService compensationService, PayrollJobService jobs) {
        this.compensationService = compensationService;
        this.jobs = jobs;
    }

    /**
     * The whole run, inline. Kept for small companies and for anything that wants the numbers in
     * one call (the bank file, the QA harness); the page itself uses the job form below.
     */
    @GetMapping("/run")
    public PayrollRunResponse run(@RequestParam(required = false) String month) {
        return compensationService.payrollRun(month);
    }

    /** Start a run in the background. 202 with the job; poll {@code GET /runs/{jobId}} for the result. */
    @PostMapping("/runs")
    public ResponseEntity<PayrollJobResponse> start(@RequestParam(required = false) String month) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(jobs.start(month));
    }

    @GetMapping("/runs/{jobId}")
    public PayrollJobResponse job(@PathVariable java.util.UUID jobId) {
        return jobs.find(jobId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Payroll run not found"));
    }
}
