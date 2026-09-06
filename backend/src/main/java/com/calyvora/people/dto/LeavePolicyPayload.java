package com.calyvora.people.dto;

import java.math.BigDecimal;

/**
 * Edit one leave type's rule. Every field is optional — a PATCH-shaped payload, so a screen that
 * only changes the carry-forward cap does not have to resend the entitlement and risk clobbering a
 * concurrent edit with a stale value.
 */
public record LeavePolicyPayload(
        Boolean paid,
        String accrual,
        BigDecimal daysPerYear,
        BigDecimal carryForwardCap,
        Integer compOffExpiryDays
) {
}
