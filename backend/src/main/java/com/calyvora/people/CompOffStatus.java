package com.calyvora.people;

/**
 * The life of a comp-off credit.
 *
 * <p>Expiry is deliberately absent: a credit past its date is still APPROVED and simply stops being
 * spendable (see {@code CompOffCredit.isSpendable}). Making it a status would need a scheduled job
 * to write it, and a policy change lengthening the window would then have to resurrect rows the job
 * had already killed.
 */
public enum CompOffStatus {
    PENDING,
    APPROVED,
    REJECTED,
    /** Spent on a leave request, which is named in {@code consumed_by}. */
    CONSUMED
}
