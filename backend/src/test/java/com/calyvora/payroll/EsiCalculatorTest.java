package com.calyvora.payroll;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ESI arithmetic, each case a wage and an answer that can be checked by hand against the ESI Act.
 */
class EsiCalculatorTest {

    private static final BigDecimal EMPLOYEE = new BigDecimal("0.75");
    private static final BigDecimal EMPLOYER = new BigDecimal("3.25");
    private static final BigDecimal CEILING = new BigDecimal("21000");

    private static EsiCalculator.Result esi(int earned, int periodStart, double days) {
        return EsiCalculator.compute(BigDecimal.valueOf(earned), BigDecimal.valueOf(periodStart), days,
                EMPLOYEE, EMPLOYER, CEILING);
    }

    @Test
    void a_covered_wage_splits_at_the_statutory_rates() {
        // 18,000: employee 0.75% = 135, employer 3.25% = 585.
        EsiCalculator.Result r = esi(18_000, 18_000, 30);
        assertThat(r.covered()).isTrue();
        assertThat(r.employee()).isEqualByComparingTo("135");
        assertThat(r.employer()).isEqualByComparingTo("585");
        assertThat(r.wages()).isEqualByComparingTo("18000");
    }

    @Test
    void each_share_is_rounded_up_to_the_next_rupee() {
        // 15,001 × 0.75% = 112.5075 → 113; × 3.25% = 487.5325 → 488. Half-up would give 113 and 488
        // too, so use a wage where they differ: 15,010 × 0.75% = 112.575 → 113; 15,010 × 3.25% =
        // 487.825 → 488; and 14,990 × 0.75% = 112.425 → 113 (half-up says 112).
        assertThat(esi(14_990, 14_990, 30).employee()).isEqualByComparingTo("113");
        assertThat(esi(15_010, 15_010, 30).employer()).isEqualByComparingTo("488");
    }

    @Test
    void the_ceiling_itself_is_covered_and_one_rupee_over_is_not() {
        assertThat(esi(21_000, 21_000, 30).covered()).isTrue();
        assertThat(esi(21_001, 21_001, 30).covered()).isFalse();
    }

    @Test
    void a_raise_mid_period_does_not_end_cover_until_the_period_ends() {
        // Earned 25,000 this month, but was on 20,000 when the period began: still covered, and the
        // contribution is on what was actually paid.
        EsiCalculator.Result r = esi(25_000, 20_000, 30);
        assertThat(r.covered()).isTrue();
        assertThat(r.employee()).isEqualByComparingTo("188");   // 187.5 up
        assertThat(r.employer()).isEqualByComparingTo("813");   // 812.5 up
    }

    @Test
    void a_low_earner_pays_no_employee_share_but_the_employer_still_pays() {
        // 5,000 over 30 days = 166.67 a day, under 176.
        EsiCalculator.Result r = esi(5_000, 5_000, 30);
        assertThat(r.covered()).isTrue();
        assertThat(r.employee()).isEqualByComparingTo("0");
        assertThat(r.employer()).isEqualByComparingTo("163");   // 162.5 up
    }

    @Test
    void nothing_paid_means_nothing_contributed() {
        assertThat(esi(0, 18_000, 0).covered()).isFalse();
    }

    @Test
    void contribution_periods_run_april_to_september_and_october_to_march() {
        assertThat(EsiCalculator.periodStart(YearMonth.of(2026, 4))).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(EsiCalculator.periodStart(YearMonth.of(2026, 9))).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(EsiCalculator.periodStart(YearMonth.of(2026, 10))).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(EsiCalculator.periodStart(YearMonth.of(2027, 2))).isEqualTo(LocalDate.of(2026, 10, 1));
    }
}
