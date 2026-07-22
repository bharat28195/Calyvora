package com.calyvora.expense.dto;

import com.calyvora.expense.ExpenseClaim;

import java.math.BigDecimal;

/** A claim, with the claimant's name so a list needs no extra lookups. */
public record ExpenseResponse(
        String id,
        String employeeId,
        String employeeName,
        String title,
        String category,
        BigDecimal amount,
        String currency,
        String spentOn,
        String description,
        String receiptUrl,
        String status,
        String decisionNote,
        String decidedAt,
        String reimbursedAt,
        String createdAt
) {
    public static ExpenseResponse of(ExpenseClaim c, String employeeName) {
        return new ExpenseResponse(c.getId().toString(), c.getEmployeeId().toString(), employeeName,
                c.getTitle(), c.getCategory().name(), c.getAmount(), c.getCurrency(),
                c.getSpentOn().toString(), c.getDescription(), c.getReceiptUrl(), c.getStatus().name(),
                c.getDecisionNote(),
                c.getDecidedAt() == null ? null : c.getDecidedAt().toString(),
                c.getReimbursedAt() == null ? null : c.getReimbursedAt().toString(),
                c.getCreatedAt().toString());
    }
}
