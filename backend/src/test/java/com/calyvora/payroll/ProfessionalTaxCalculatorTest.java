package com.calyvora.payroll;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Professional tax by state, each case checked against the state's published schedule.
 */
class ProfessionalTaxCalculatorTest {

    private static final YearMonth OCT = YearMonth.of(2026, 10);
    private static final YearMonth FEB = YearMonth.of(2027, 2);
    private static final YearMonth SEP = YearMonth.of(2026, 9);
    private static final YearMonth MAR = YearMonth.of(2027, 3);

    private static BigDecimal pt(String state, String gender, int monthly, YearMonth month) {
        return ProfessionalTaxCalculator.compute(state, gender, null,
                BigDecimal.valueOf(monthly), BigDecimal.valueOf(monthly), month).amount();
    }

    @Test
    void maharashtra_men_pay_from_7501_and_women_from_25001() {
        assertThat(pt("Maharashtra", "MALE", 7_500, OCT)).isEqualByComparingTo("0");
        assertThat(pt("Maharashtra", "MALE", 9_000, OCT)).isEqualByComparingTo("175");
        assertThat(pt("Maharashtra", "MALE", 30_000, OCT)).isEqualByComparingTo("200");
        assertThat(pt("Maharashtra", "FEMALE", 20_000, OCT)).isEqualByComparingTo("0");
        assertThat(pt("Maharashtra", "FEMALE", 30_000, OCT)).isEqualByComparingTo("200");
    }

    @Test
    void maharashtra_unknown_gender_uses_the_slab_that_does_not_exempt() {
        assertThat(pt("MH", null, 20_000, OCT)).isEqualByComparingTo("200");
    }

    @Test
    void february_collects_300_in_the_top_slab_so_the_year_totals_2500() {
        assertThat(pt("Maharashtra", "MALE", 30_000, FEB)).isEqualByComparingTo("300");
        assertThat(pt("Maharashtra", "MALE", 9_000, FEB)).isEqualByComparingTo("175");
        assertThat(pt("Karnataka", null, 30_000, FEB)).isEqualByComparingTo("300");
    }

    @Test
    void karnataka_starts_at_25000_and_exempts_sixty_and_over() {
        assertThat(pt("Karnataka", null, 24_999, OCT)).isEqualByComparingTo("0");
        assertThat(pt("Karnataka", null, 25_000, OCT)).isEqualByComparingTo("200");
        BigDecimal senior = ProfessionalTaxCalculator.compute("Karnataka", null, LocalDate.of(1960, 1, 1),
                BigDecimal.valueOf(80_000), BigDecimal.valueOf(80_000), OCT).amount();
        assertThat(senior).isEqualByComparingTo("0");
    }

    @Test
    void west_bengal_has_four_paying_slabs() {
        assertThat(pt("West Bengal", null, 10_000, OCT)).isEqualByComparingTo("0");
        assertThat(pt("West Bengal", null, 12_000, OCT)).isEqualByComparingTo("110");
        assertThat(pt("West Bengal", null, 20_000, OCT)).isEqualByComparingTo("130");
        assertThat(pt("West Bengal", null, 30_000, OCT)).isEqualByComparingTo("150");
        assertThat(pt("West Bengal", null, 50_000, OCT)).isEqualByComparingTo("200");
    }

    @Test
    void telangana_and_gujarat() {
        assertThat(pt("Telangana", null, 18_000, OCT)).isEqualByComparingTo("150");
        assertThat(pt("TS", null, 25_000, OCT)).isEqualByComparingTo("200");
        assertThat(pt("Gujarat", null, 12_000, OCT)).isEqualByComparingTo("0");
        assertThat(pt("Gujarat", null, 12_001, OCT)).isEqualByComparingTo("200");
    }

    @Test
    void madhya_pradesh_is_annual_in_twelve_instalments_with_the_remainder_in_march() {
        // 50,000 a month = 6 lakh a year → 2,500 a year → 208 × 11 + 212.
        assertThat(pt("Madhya Pradesh", null, 50_000, OCT)).isEqualByComparingTo("208");
        assertThat(pt("Madhya Pradesh", null, 50_000, MAR)).isEqualByComparingTo("212");
        // 30,000 a month = 3.6 lakh → 2,000 → 166 × 11 + 174.
        assertThat(pt("Madhya Pradesh", null, 30_000, OCT)).isEqualByComparingTo("166");
        assertThat(pt("Madhya Pradesh", null, 30_000, MAR)).isEqualByComparingTo("174");
    }

    @Test
    void jharkhand_exempts_15000_a_month_whatever_the_annual_slab() {
        assertThat(pt("Jharkhand", null, 15_000, OCT)).isEqualByComparingTo("0");
        assertThat(pt("Jharkhand", null, 50_000, OCT)).isEqualByComparingTo("150");   // 6 lakh → 1,800
    }

    @Test
    void tamil_nadu_collects_twice_a_year_on_half_year_income() {
        // 10,000 a month = 60,000 a half-year → 930, in September and March only.
        assertThat(pt("Tamil Nadu", null, 10_000, SEP)).isEqualByComparingTo("930");
        assertThat(pt("Tamil Nadu", null, 10_000, MAR)).isEqualByComparingTo("930");
        assertThat(pt("Tamil Nadu", null, 10_000, OCT)).isEqualByComparingTo("0");
        assertThat(pt("Tamil Nadu", null, 50_000, SEP)).isEqualByComparingTo("1250");
    }

    @Test
    void states_without_professional_tax_charge_nothing() {
        assertThat(pt("Delhi", null, 90_000, OCT)).isEqualByComparingTo("0");
        assertThat(pt("Haryana", null, 90_000, OCT)).isEqualByComparingTo("0");
        assertThat(pt("Odisha", null, 90_000, OCT)).isEqualByComparingTo("0");   // repealed April 2026
    }

    @Test
    void a_levying_state_without_a_schedule_here_is_reported_not_silently_zero() {
        var r = ProfessionalTaxCalculator.compute("Meghalaya", null, null,
                BigDecimal.valueOf(40_000), BigDecimal.valueOf(40_000), OCT);
        assertThat(r.amount()).isEqualByComparingTo("0");
        assertThat(r.supported()).isFalse();
    }

    @Test
    void the_monthly_basis_is_the_salary_actually_paid() {
        // Contracted 30,000 but earned 24,000 after loss of pay: Karnataka slabs the 24,000 → nil.
        BigDecimal amount = ProfessionalTaxCalculator.compute("Karnataka", null, null,
                BigDecimal.valueOf(24_000), BigDecimal.valueOf(30_000), OCT).amount();
        assertThat(amount).isEqualByComparingTo("0");
    }

    @Test
    void state_names_resolve_however_they_are_typed() {
        assertThat(ProfessionalTaxCalculator.stateCode(" tamil   nadu ")).isEqualTo("TN");
        assertThat(ProfessionalTaxCalculator.stateCode("ka")).isEqualTo("KA");
        assertThat(ProfessionalTaxCalculator.stateCode("Pondicherry")).isEqualTo("PY");
        assertThat(ProfessionalTaxCalculator.stateCode("")).isNull();
    }
}
