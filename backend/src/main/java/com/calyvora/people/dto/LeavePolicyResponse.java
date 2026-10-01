package com.calyvora.people.dto;

import com.calyvora.people.LeavePolicy;

import java.math.BigDecimal;

/** One leave type's rule, as the settings screen shows it. */
public record LeavePolicyResponse(
        String type,
        boolean paid,
        String accrual,
        BigDecimal daysPerYear,
        BigDecimal carryForwardCap,
        int compOffExpiryDays
) {
    public static LeavePolicyResponse of(LeavePolicy p) {
        return new LeavePolicyResponse(p.getType().name(), p.isPaid(), p.getAccrual().name(),
                p.getDaysPerYear(), p.getCarryForwardCap(), p.getCompOffExpiryDays());
    }
}
