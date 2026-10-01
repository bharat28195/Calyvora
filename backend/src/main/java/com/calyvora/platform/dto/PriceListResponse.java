package com.calyvora.platform.dto;

import com.calyvora.billing.PriceList;

import java.math.BigDecimal;
import java.util.List;

/** One version of the price list, as the owner console shows it. */
public record PriceListResponse(
        String id,
        String effectiveFrom,
        String note,
        boolean current,
        List<Tier> tiers,
        BigDecimal monthlyMinimum,
        int annualMonthsCharged,
        String currency
) {
    /** {@code toEmployee} is null on the final, open-ended tier. */
    public record Tier(long fromEmployee, Integer toEmployee, BigDecimal rate) {}

    public static PriceListResponse of(PriceList list, boolean current) {
        List<Tier> tiers = new java.util.ArrayList<>();
        long from = 1;
        for (var t : list.getTiers()) {
            tiers.add(new Tier(from, t.getUpTo(), t.getRate()));
            if (t.getUpTo() == null) {
                break;
            }
            from = t.getUpTo() + 1L;
        }
        return new PriceListResponse(list.getId().toString(), list.getEffectiveFrom().toString(),
                list.getNote(), current, tiers, list.getMonthlyMinimum(), list.getAnnualMonthsCharged(),
                list.getCurrency());
    }
}
