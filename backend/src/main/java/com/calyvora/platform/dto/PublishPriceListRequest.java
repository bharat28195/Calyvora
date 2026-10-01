package com.calyvora.platform.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Publish a new version of the price list.
 *
 * @param effectiveFrom {@code YYYY-MM-DD}. Months already invoiced keep the list that was in force
 *                      then, so dating this in the future is the safe way to announce a change.
 * @param tiers         in ascending order of employee limit; the last one must be open-ended
 *                      ({@code toEmployee} null) or there'd be a headcount with no price.
 */
public record PublishPriceListRequest(
        @NotNull String effectiveFrom,
        @Size(max = 200) String note,
        @NotEmpty List<Tier> tiers,
        /** Floor a company pays regardless of headcount. Zero or null disables it. */
        BigDecimal monthlyMinimum,
        /** Months charged for an annual prepayment — 10 means two months free, 12 means no discount. */
        Integer annualMonthsCharged,
        /** Which currency this list prices in. Null means rupees, the list that existed first. */
        @Size(min = 3, max = 3) String currency
) {
    public record Tier(Integer toEmployee, @NotNull BigDecimal rate) {}
}
