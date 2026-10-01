package com.calyvora.people;

/**
 * Kinds of time off.
 *
 * <p>Each has a {@link LeavePolicy} per company deciding entitlement, accrual and carry-forward, so
 * this list is the vocabulary rather than the rules.
 */
public enum LeaveType {
    VACATION,
    SICK,
    PERSONAL,
    UNPAID,
    /**
     * A day off earned by working a day that was not owed — a holiday, a weekend, a release night.
     *
     * <p>The odd one out: its entitlement is not an annual number but a pile of individually earned
     * credits (see {@code CompOffCredit}), each traceable to the day worked and each expiring. That
     * is why {@code daysPerYear} stays zero for it and the balance comes from counting credits.
     */
    COMP_OFF
}
