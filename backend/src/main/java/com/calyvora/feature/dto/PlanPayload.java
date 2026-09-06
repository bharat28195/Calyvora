package com.calyvora.feature.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Create or edit a plan. Every field optional on update — a screen changing only the feature list
 * should not have to resend the price and risk clobbering a concurrent edit with a stale one.
 *
 * @param features the complete list, not a delta. Sending it replaces what the plan includes, which
 *                 is what a checkbox grid naturally produces and what makes "uncheck one" work.
 */
public record PlanPayload(
        String code,
        String name,
        String description,
        BigDecimal pricePerEmployee,
        Integer sortOrder,
        Boolean active,
        List<String> features
) {
}
