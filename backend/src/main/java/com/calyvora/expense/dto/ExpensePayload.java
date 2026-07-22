package com.calyvora.expense.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Submit or edit a claim. On create, title/amount/spentOn are required (checked in the service). */
public record ExpensePayload(
        @Size(max = 200) String title,
        @Pattern(regexp = "TRAVEL|ACCOMMODATION|MEALS|SUPPLIES|TRAINING|OTHER", message = "invalid category")
        String category,
        BigDecimal amount,
        @Size(max = 3) String currency,
        /** ISO date the money was spent. */
        String spentOn,
        @Size(max = 1000) String description,
        @Size(max = 500) String receiptUrl
) {}
