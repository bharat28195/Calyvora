package com.calyvora.expense;

/**
 * Approval and payment are separate states on purpose: an approved claim that hasn't been paid yet
 * is exactly the thing people chase, and collapsing the two would hide it.
 */
public enum ExpenseStatus {
    SUBMITTED,
    APPROVED,
    REJECTED,
    REIMBURSED,
}
