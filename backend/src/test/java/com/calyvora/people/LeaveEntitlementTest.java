package com.calyvora.people;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The accrual arithmetic, as fast unit tests.
 *
 * <p>These exist as unit tests rather than as part of the integration suite on purpose. Accrual is
 * the part of leave people argue about — "I joined in March, why do I only have 20 days?" — and every
 * awkward case is a date and an expected number. Proving them through HTTP would cost twenty-five
 * seconds a case and make it tedious to add the next one, which is exactly how rounding rules end up
 * untested.
 */
class LeaveEntitlementTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 6);

    private LeavePolicy policy(LeaveAccrual accrual, double daysPerYear, double carryCap) {
        LeavePolicy p = new LeavePolicy(UUID.randomUUID(), UUID.randomUUID(), LeaveType.VACATION);
        p.setAccrual(accrual);
        p.setDaysPerYear(BigDecimal.valueOf(daysPerYear));
        p.setCarryForwardCap(BigDecimal.valueOf(carryCap));
        return p;
    }

    private static BigDecimal days(double value) {
        return BigDecimal.valueOf(value).setScale(1, java.math.RoundingMode.HALF_UP);
    }

    // ---- ANNUAL ----

    @Test
    void annual_grants_the_whole_entitlement_to_someone_who_was_already_here() {
        LocalDate joined = LocalDate.of(2020, 4, 1);
        assertThat(LeaveEntitlement.earnedIn(policy(LeaveAccrual.ANNUAL, 25, 0), joined, 2026, TODAY))
                .isEqualTo(days(25));
    }

    @Test
    void annual_pro_rates_for_a_mid_year_joiner() {
        // Joined 1 July: six months of the year remain, so half the entitlement.
        LocalDate joined = LocalDate.of(2026, 7, 1);
        assertThat(LeaveEntitlement.earnedIn(policy(LeaveAccrual.ANNUAL, 24, 0), joined, 2026, TODAY))
                .isEqualTo(days(12));
    }

    @Test
    void annual_gives_a_december_joiner_one_month_not_a_year() {
        // The reason ANNUAL pro-rates at all: granting a full year's holiday to somebody who starts on
        // 1 December is generosity a company should opt into, not inherit from a default.
        LocalDate joined = LocalDate.of(2026, 12, 1);
        assertThat(LeaveEntitlement.earnedIn(policy(LeaveAccrual.ANNUAL, 24, 0), joined, 2026,
                LocalDate.of(2026, 12, 15))).isEqualTo(days(2));
    }

    @Test
    void nothing_is_earned_before_joining() {
        LocalDate joined = LocalDate.of(2027, 1, 1);
        assertThat(LeaveEntitlement.earnedIn(policy(LeaveAccrual.ANNUAL, 25, 0), joined, 2026, TODAY))
                .isEqualTo(days(0));
    }

    // ---- MONTHLY ----

    @Test
    void monthly_earns_only_completed_months() {
        // Joined 1 January, today is 6 September: eight completed months, not nine.
        LocalDate joined = LocalDate.of(2026, 1, 1);
        assertThat(LeaveEntitlement.earnedIn(policy(LeaveAccrual.MONTHLY, 12, 0), joined, 2026, TODAY))
                .isEqualTo(days(8));
    }

    @Test
    void monthly_ignores_the_joining_month_and_credits_the_next_when_it_ends() {
        // Joined 20 August, so August is not a whole month worked and earns nothing.
        LocalDate joined = LocalDate.of(2026, 8, 20);
        assertThat(LeaveEntitlement.earnedIn(policy(LeaveAccrual.MONTHLY, 12, 0), joined, 2026, TODAY))
                .isEqualTo(days(0));

        // Nor part-way through September — the credit lands when the month does.
        assertThat(LeaveEntitlement.earnedIn(policy(LeaveAccrual.MONTHLY, 12, 0), joined, 2026,
                LocalDate.of(2026, 9, 20))).isEqualTo(days(0));

        // On 30 September, September is complete: one day.
        assertThat(LeaveEntitlement.earnedIn(policy(LeaveAccrual.MONTHLY, 12, 0), joined, 2026,
                LocalDate.of(2026, 9, 30))).isEqualTo(days(1));
    }

    @Test
    void monthly_survives_being_hired_on_the_31st() {
        // January is not a whole month for a 31 January joiner, so it earns nothing; February is,
        // and lands the moment February ends.
        LocalDate joined = LocalDate.of(2026, 1, 31);
        assertThat(LeaveEntitlement.earnedIn(policy(LeaveAccrual.MONTHLY, 12, 0), joined, 2026,
                LocalDate.of(2026, 2, 28))).isEqualTo(days(1));
        assertThat(LeaveEntitlement.earnedIn(policy(LeaveAccrual.MONTHLY, 12, 0), joined, 2026,
                LocalDate.of(2026, 2, 27))).isEqualTo(days(0));
    }

    @Test
    void a_year_that_has_finished_accrues_in_full_not_up_to_today() {
        LocalDate joined = LocalDate.of(2024, 1, 1);
        assertThat(LeaveEntitlement.earnedIn(policy(LeaveAccrual.MONTHLY, 12, 0), joined, 2025, TODAY))
                .isEqualTo(days(12));
    }

    // ---- carry-forward ----

    @Test
    void no_cap_means_use_it_or_lose_it() {
        LocalDate joined = LocalDate.of(2024, 1, 1);
        assertThat(LeaveEntitlement.carriedInto(policy(LeaveAccrual.ANNUAL, 25, 0), joined, 2026, TODAY,
                Map.of())).isEqualTo(days(0));
    }

    @Test
    void carry_forward_is_capped() {
        // 25 days a year, nothing taken, cap 10 — so 10 carries, not 25 and not 50.
        LocalDate joined = LocalDate.of(2025, 1, 1);
        assertThat(LeaveEntitlement.carriedInto(policy(LeaveAccrual.ANNUAL, 25, 10), joined, 2026, TODAY,
                Map.of())).isEqualTo(days(10));
    }

    @Test
    void carry_forward_accounts_for_what_was_actually_taken() {
        // 25 earned, 20 taken, 5 left — under the cap of 10, so 5 carries.
        LocalDate joined = LocalDate.of(2025, 1, 1);
        assertThat(LeaveEntitlement.carriedInto(policy(LeaveAccrual.ANNUAL, 25, 10), joined, 2026, TODAY,
                Map.of(2025, BigDecimal.valueOf(20)))).isEqualTo(days(5));
    }

    @Test
    void carry_forward_compounds_across_years_but_stays_capped() {
        // Joined 2023, nothing ever taken, cap 10. Each year adds 25 to a balance that is then cut
        // back to 10 — the answer is 10 every year, never 30. This is the case a backwards recursion
        // with an arbitrary depth limit gets wrong, and why the walk goes forward from joining.
        LocalDate joined = LocalDate.of(2023, 1, 1);
        assertThat(LeaveEntitlement.carriedInto(policy(LeaveAccrual.ANNUAL, 25, 10), joined, 2026, TODAY,
                Map.of())).isEqualTo(days(10));
    }

    @Test
    void a_year_taken_into_deficit_carries_nothing_rather_than_a_negative() {
        LocalDate joined = LocalDate.of(2025, 1, 1);
        assertThat(LeaveEntitlement.carriedInto(policy(LeaveAccrual.ANNUAL, 25, 10), joined, 2026, TODAY,
                Map.of(2025, BigDecimal.valueOf(30)))).isEqualTo(days(0));
    }

    @Test
    void nothing_carries_into_the_year_someone_joined() {
        LocalDate joined = LocalDate.of(2026, 3, 1);
        assertThat(LeaveEntitlement.carriedInto(policy(LeaveAccrual.ANNUAL, 25, 10), joined, 2026, TODAY,
                Map.of())).isEqualTo(days(0));
    }

    @Test
    void accrued_reports_the_two_sources_separately() {
        LocalDate joined = LocalDate.of(2025, 1, 1);
        LeaveEntitlement.Accrued accrued = LeaveEntitlement.accrued(
                policy(LeaveAccrual.ANNUAL, 25, 10), joined, 2026, TODAY, Map.of(2025, BigDecimal.valueOf(20)));

        assertThat(accrued.earnedThisYear()).isEqualTo(days(25));
        assertThat(accrued.carriedIn()).isEqualTo(days(5));
        assertThat(accrued.total()).isEqualTo(days(30));
    }
}
