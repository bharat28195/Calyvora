package com.calyvora.people;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** A month's salary by date: raises, joiners and leavers, each checkable by hand. */
class SalaryCalendarTest {

    private static final UUID CO = UUID.randomUUID(), EMP = UUID.randomUUID();
    private static final YearMonth JUNE = YearMonth.of(2026, 6);   // 30 days

    private static CompensationRecord salary(String effective, int annual) {
        return new CompensationRecord(UUID.randomUUID(), CO, EMP, LocalDate.parse(effective),
                BigDecimal.valueOf(annual), "INR", CompensationChangeType.INITIAL, null, null);
    }

    private static BigDecimal gross(List<CompensationRecord> newestFirst, LocalDate start, LocalDate end) {
        return SalaryCalendar.grossForMonth(newestFirst, JUNE, start, end);
    }

    @Test
    void one_salary_all_month_is_exactly_a_twelfth() {
        assertThat(gross(List.of(salary("2026-01-01", 1_200_000)), null, null)).isEqualByComparingTo("100000.00");
    }

    @Test
    void the_first_salary_applies_backwards_so_entering_it_today_does_not_halve_the_month() {
        // Typed in on 20 June for somebody who has worked here for years.
        assertThat(gross(List.of(salary("2026-06-20", 1_200_000)), null, null)).isEqualByComparingTo("100000.00");
    }

    @Test
    void a_raise_mid_month_pays_each_day_at_its_own_rate() {
        // 100,000 a month for 15 days, then 130,000 for 15 days = 50,000 + 65,000.
        var history = List.of(salary("2026-06-16", 1_560_000), salary("2026-01-01", 1_200_000));
        assertThat(gross(history, null, null)).isEqualByComparingTo("115000.00");
    }

    @Test
    void a_raise_from_the_first_is_the_new_twelfth() {
        var history = List.of(salary("2026-06-01", 1_560_000), salary("2026-01-01", 1_200_000));
        assertThat(gross(history, null, null)).isEqualByComparingTo("130000.00");
    }

    @Test
    void a_joiner_is_paid_from_the_start_date() {
        // Joins 21 June: 10 of 30 days.
        assertThat(gross(List.of(salary("2026-06-21", 1_200_000)), LocalDate.of(2026, 6, 21), null))
                .isEqualByComparingTo("33333.33");
    }

    @Test
    void a_leaver_is_paid_to_the_end_date_and_nothing_after() {
        var history = List.of(salary("2026-01-01", 1_200_000));
        assertThat(gross(history, null, LocalDate.of(2026, 6, 15))).isEqualByComparingTo("50000.00");
        assertThat(SalaryCalendar.grossForMonth(history, YearMonth.of(2026, 7), null, LocalDate.of(2026, 6, 15)))
                .isEqualByComparingTo("0");
    }

    @Test
    void nobody_on_record_is_null() {
        assertThat(gross(List.of(), null, null)).isNull();
    }
}
