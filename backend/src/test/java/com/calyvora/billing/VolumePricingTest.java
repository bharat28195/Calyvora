package com.calyvora.billing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The published price list: ₹149 for the first 100 employees, ₹99 for each one after.
 *
 * <p>The test that matters most is {@link #the_bill_never_falls_as_a_company_grows()}. Reading
 * "₹99 once you pass 100" as a flat rate on every employee would mean the 101st hire <em>reduced</em>
 * the bill from ₹14,900 to ₹9,999 — a third of the revenue lost at the exact moment a customer grows.
 */
class VolumePricingTest {

    @Test
    void small_companies_pay_the_first_tier_rate() {
        assertThat(VolumePricing.monthlyFor(1)).isEqualByComparingTo("149");
        assertThat(VolumePricing.monthlyFor(10)).isEqualByComparingTo("1490");
        assertThat(VolumePricing.monthlyFor(100)).isEqualByComparingTo("14900");
    }

    @Test
    void only_the_employees_above_100_get_the_cheaper_rate() {
        // 100 × 149 = 14,900, then one at 99.
        assertThat(VolumePricing.monthlyFor(101)).isEqualByComparingTo("14999");
        // 14,900 + 50 × 99 = 19,850.
        assertThat(VolumePricing.monthlyFor(150)).isEqualByComparingTo("19850");
        // 14,900 + 400 × 99 = 54,500.
        assertThat(VolumePricing.monthlyFor(500)).isEqualByComparingTo("54500");
    }

    @Test
    void the_bill_never_falls_as_a_company_grows() {
        BigDecimal previous = BigDecimal.ZERO;
        for (long headcount = 0; headcount <= 250; headcount++) {
            BigDecimal current = VolumePricing.monthlyFor(headcount);
            assertThat(current)
                    .as("adding an employee must never reduce the bill (at %d)", headcount)
                    .isGreaterThanOrEqualTo(previous);
            previous = current;
        }
    }

    @Test
    void the_quoted_rate_is_what_the_next_employee_costs() {
        assertThat(VolumePricing.marginalRate(0)).isEqualByComparingTo("149");
        assertThat(VolumePricing.marginalRate(99)).isEqualByComparingTo("149");
        // At 100 people the next hire is the 101st, so they're on the lower rate.
        assertThat(VolumePricing.marginalRate(100)).isEqualByComparingTo("99");
        assertThat(VolumePricing.marginalRate(1_000)).isEqualByComparingTo("99");
    }

    @Test
    void an_empty_company_is_not_billed() {
        assertThat(VolumePricing.monthlyFor(0)).isEqualByComparingTo("0");
    }

    @Test
    void a_negotiated_flat_rate_overrides_the_price_list() {
        // A company the owner quoted ₹80 pays 80 per head at any size — the tiers don't apply.
        Subscription custom = new Subscription(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                new BigDecimal("80"), "INR", java.time.Instant.now());
        custom.setCustomPrice(true);

        assertThat(custom.monthlyFor(150)).isEqualByComparingTo("12000");
        assertThat(custom.rateFor(150)).isEqualByComparingTo("80");
    }

    @Test
    void a_company_on_the_standard_list_uses_the_tiers() {
        Subscription standard = new Subscription(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                new BigDecimal("100"), "INR", java.time.Instant.now());

        assertThat(standard.monthlyFor(150)).isEqualByComparingTo("19850");
        assertThat(standard.rateFor(150)).isEqualByComparingTo("99");
    }
}
