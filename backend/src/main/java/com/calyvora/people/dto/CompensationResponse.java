package com.calyvora.people.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * An employee's compensation: current pay plus the full history of raises/adjustments, with each
 * record's hike over the previous one — so the owner can see "how much hike we've provided" (feedback C1–C2).
 */
public record CompensationResponse(
        String employeeId,
        String employeeName,
        String currency,
        BigDecimal currentAnnual,
        BigDecimal currentMonthly,
        String effectiveDate,
        List<Entry> history
) {
    public record Entry(
            String id,
            String effectiveDate,
            BigDecimal annualAmount,
            String changeType,
            String reason,
            BigDecimal hikeAmount,   // vs the previous record (null for the first)
            Double hikePercent
    ) {}
}
