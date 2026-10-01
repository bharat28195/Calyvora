package com.calyvora.people.dto;

/**
 * A payroll run in flight or finished. {@code status} is RUNNING, DONE or FAILED; {@code result}
 * is set only for DONE, {@code error} only for FAILED.
 */
public record PayrollJobResponse(
        String jobId,
        String month,
        String status,
        String startedAt,
        String finishedAt,
        PayrollRunResponse result,
        String error
) {
}
