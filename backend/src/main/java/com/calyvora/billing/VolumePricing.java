package com.calyvora.billing;

import java.math.BigDecimal;
import java.util.List;

/**
 * The platform's standard price list: ₹149 per employee per month for the first 100 people, ₹99 for
 * each one after that.
 *
 * <p><b>Graduated, not a flat band.</b> Charging every employee the lower rate once a company passes
 * 100 would mean the bill <em>falls</em> as the customer grows — 100 people at ₹149 is ₹14,900, but
 * 101 people at ₹99 would be ₹9,999, so the 101st hire costs us ₹4,901 a month. Worse, a 101-person
 * customer would pay less in total than a 71-person one. Charging the cheaper rate only on the
 * employees above the threshold keeps the bill rising with headcount (101 people = ₹14,999) while
 * still giving larger customers the better marginal rate they were promised.
 *
 * <p>A company can be moved off this list entirely — see {@link Subscription#isCustomPrice()} — in
 * which case a single negotiated rate applies to every employee.
 */
public final class VolumePricing {

    /** @param upTo employees covered by this tier, cumulative; {@link Long#MAX_VALUE} for the last. */
    public record Tier(long upTo, BigDecimal rate) {}

    public static final List<Tier> TIERS = List.of(
            new Tier(100, new BigDecimal("149")),
            new Tier(Long.MAX_VALUE, new BigDecimal("99")));

    private VolumePricing() {}

    /** What a company of this size pays per month, walking the tiers. */
    public static BigDecimal monthlyFor(long headcount) {
        BigDecimal total = BigDecimal.ZERO;
        long counted = 0;
        for (Tier tier : TIERS) {
            if (counted >= headcount) {
                break;
            }
            long inTier = Math.min(headcount, tier.upTo()) - counted;
            total = total.add(tier.rate().multiply(BigDecimal.valueOf(inTier)));
            counted += inTier;
        }
        return total;
    }

    /**
     * The rate the <em>next</em> employee would be charged at. This is the number worth showing a
     * customer — "you're on ₹99 now" — where a blended average (their bill ÷ headcount) would be a
     * figure that appears on no price list and answers no question they have.
     */
    public static BigDecimal marginalRate(long headcount) {
        for (Tier tier : TIERS) {
            if (headcount < tier.upTo()) {
                return tier.rate();
            }
        }
        return TIERS.get(TIERS.size() - 1).rate();
    }
}
