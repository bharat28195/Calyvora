package com.calyvora.payroll;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

/** Labour Welfare Fund: fixed amounts, each state on its own calendar. */
class LwfCalculatorTest {

    private static final BigDecimal PAID = BigDecimal.valueOf(40_000);

    private static LwfCalculator.Result lwf(String state, int year, int month) {
        return LwfCalculator.compute(state, PAID, YearMonth.of(year, month));
    }

    @Test
    void maharashtra_collects_in_june_and_december_only() {
        assertThat(lwf("Maharashtra", 2026, 6).employee()).isEqualByComparingTo("25");
        assertThat(lwf("Maharashtra", 2026, 6).employer()).isEqualByComparingTo("75");
        assertThat(lwf("Maharashtra", 2026, 12).employee()).isEqualByComparingTo("25");
        assertThat(lwf("Maharashtra", 2026, 10).employee()).isEqualByComparingTo("0");
    }

    @Test
    void karnataka_and_tamil_nadu_collect_once_a_year_in_december() {
        assertThat(lwf("Karnataka", 2026, 12).employee()).isEqualByComparingTo("50");
        assertThat(lwf("Karnataka", 2026, 12).employer()).isEqualByComparingTo("100");
        assertThat(lwf("Karnataka", 2026, 6).employee()).isEqualByComparingTo("0");
        assertThat(lwf("Tamil Nadu", 2026, 12).employer()).isEqualByComparingTo("40");
    }

    @Test
    void punjab_collects_every_month() {
        assertThat(lwf("Punjab", 2026, 10).employee()).isEqualByComparingTo("5");
        assertThat(lwf("Punjab", 2026, 10).employer()).isEqualByComparingTo("20");
    }

    @Test
    void delhi_collects_six_months_of_its_monthly_rate_at_once() {
        assertThat(lwf("Delhi", 2026, 6).employee()).isEqualByComparingTo("4.50");
        assertThat(lwf("Delhi", 2026, 6).employer()).isEqualByComparingTo("13.50");
    }

    @Test
    void a_state_with_unconfirmed_amounts_is_reported_not_guessed() {
        LwfCalculator.Result r = lwf("Haryana", 2026, 10);
        assertThat(r.supported()).isFalse();
        assertThat(r.employee()).isEqualByComparingTo("0");
    }

    @Test
    void nobody_paid_contributes_nothing_and_a_state_without_lwf_charges_nothing() {
        assertThat(LwfCalculator.compute("Maharashtra", BigDecimal.ZERO, YearMonth.of(2026, 6)).employee())
                .isEqualByComparingTo("0");
        assertThat(lwf("Rajasthan", 2026, 6).employee()).isEqualByComparingTo("0");
        assertThat(lwf("Rajasthan", 2026, 6).supported()).isTrue();
    }
}
