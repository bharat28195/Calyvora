package com.calyvora.expense.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Claims plus the totals people actually ask about: what's waiting on a decision, and what's been
 * approved but not yet paid out.
 */
public record ExpenseSummaryResponse(
        List<ExpenseResponse> claims,
        /** Where to continue from; null at the end of the list. See {@code CursorPage}. */
        String nextCursor,
        BigDecimal pendingAmount,
        BigDecimal awaitingReimbursement,
        BigDecimal reimbursedThisYear,
        String currency
) {}
