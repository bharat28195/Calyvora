package com.calyvora.expense.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Claims plus the totals people actually ask about: what's waiting on a decision, and what's been
 * approved but not yet paid out.
 */
public record ExpenseSummaryResponse(
        List<ExpenseResponse> claims,
        BigDecimal pendingAmount,
        BigDecimal awaitingReimbursement,
        BigDecimal reimbursedThisYear,
        String currency
) {}
