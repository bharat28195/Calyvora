package com.calyvora.people.dto;

import java.math.BigDecimal;

/**
 * What one person has, for one leave type, right now.
 *
 * <p>Broken out rather than reported as a single number because "you have 14 days" invites the
 * question the old balance could not answer: 14 out of what, earned when, and how much of it dies in
 * December. Every field here exists because somebody asks about it.
 *
 * @param earnedThisYear accrued so far this year — the whole entitlement under ANNUAL, a share of it
 *                       under MONTHLY
 * @param carriedForward days that survived last year, already capped by the policy
 * @param pendingDays    requested but not yet decided; held back so two requests cannot spend the
 *                       same day
 * @param availableDays  what can actually be requested today: earned + carried − used − pending
 */
public record LeaveTypeBalanceResponse(
        String type,
        boolean paid,
        String accrual,
        BigDecimal entitlementPerYear,
        BigDecimal earnedThisYear,
        BigDecimal carriedForward,
        BigDecimal usedDays,
        BigDecimal pendingDays,
        BigDecimal availableDays
) {
}
