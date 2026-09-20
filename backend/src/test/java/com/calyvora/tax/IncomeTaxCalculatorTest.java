package com.calyvora.tax;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Worked examples, checked against the Act rather than against the implementation.
 *
 * <p>Every figure below was computed by hand from the FY 2026-27 rules and written down before the
 * calculator was run, which is the only way a test of arithmetic is worth anything: a number copied
 * out of a debugger proves the code does what it does.
 */
class IncomeTaxCalculatorTest {

    private static BigDecimal rs(String v) {
        return new BigDecimal(v);
    }

    private static IncomeTaxCalculator.Result tax(String gross, TaxRegime regime) {
        return IncomeTaxCalculator.compute(IncomeTaxCalculator.Input.of(rs(gross), regime));
    }

    private static IncomeTaxCalculator.Result tax(String gross, TaxRegime regime,
                                                  Map<TaxDeduction, BigDecimal> declared) {
        return IncomeTaxCalculator.compute(
                new IncomeTaxCalculator.Input(rs(gross), regime, declared));
    }

    @Nested
    @DisplayName("the new regime")
    class New {

        @Test
        @DisplayName("₹12,00,000 gross pays nothing — the rebate wipes out the slab tax")
        void twelve_lakh_is_free() {
            // 12,00,000 - 75,000 standard = 11,25,000 taxable.
            //   first 4,00,000        nil
            //   4,00,001-8,00,000  @5% = 20,000
            //   8,00,001-11,25,000 @10% = 32,500
            //   tax 52,500, rebate covers it (income under the 12,00,000 line).
            IncomeTaxCalculator.Result r = tax("1200000", TaxRegime.NEW);
            assertThat(r.taxableIncome()).isEqualByComparingTo(rs("1125000"));
            assertThat(r.taxOnIncome()).isEqualByComparingTo(rs("52500"));
            assertThat(r.rebate()).isEqualByComparingTo(rs("52500"));
            assertThat(r.totalTax()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("the rebate is a rebate on tax, not an exemption of income")
        void rebate_applies_to_tax() {
            // 12,75,000 - 75,000 = 12,00,000 taxable, exactly on the line.
            //   4-8L @5% = 20,000; 8-12L @10% = 40,000; tax 60,000.
            //   Rebate is capped at 60,000 — so precisely nil, and one rupee of salary more is not.
            IncomeTaxCalculator.Result r = tax("1275000", TaxRegime.NEW);
            assertThat(r.taxableIncome()).isEqualByComparingTo(rs("1200000"));
            assertThat(r.taxOnIncome()).isEqualByComparingTo(rs("60000"));
            assertThat(r.totalTax()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("marginal relief means a rupee over the line never costs sixty thousand")
        void marginal_relief_on_the_rebate() {
            // 12,85,000 - 75,000 = 12,10,000 taxable: 10,000 over the rebate line.
            //   Slab tax would be 61,500. Relief caps tax at the 10,000 earned above the line.
            //   Cess still applies: 10,000 + 4% = 10,400.
            IncomeTaxCalculator.Result r = tax("1285000", TaxRegime.NEW);
            assertThat(r.taxableIncome()).isEqualByComparingTo(rs("1210000"));
            assertThat(r.taxAfterRebate()).isEqualByComparingTo(rs("10000"));
            assertThat(r.totalTax()).isEqualByComparingTo(rs("10400"));
        }

        @Test
        @DisplayName("the marginal rate above the rebate line is 104% — never more")
        void the_marginal_rate_is_capped_at_the_cess() {
            // This test was first written to assert that take-home never falls, and it failed — which
            // turned out to be the law rather than the code. Marginal relief caps the tax at the
            // income earned above ₹12,00,000, but the 4% cess is charged on top of the relieved
            // figure, so the effective marginal rate in that band is exactly 104% and take-home does
            // dip slightly until the breakeven near ₹12.77 lakh. Relief before cess is the published
            // order of operations, so 104% is the real guarantee and the one worth pinning.
            BigDecimal previousTax = null;
            int previousGross = 0;
            for (int gross = 1200000; gross <= 1400000; gross += 5000) {
                IncomeTaxCalculator.Result r = tax(String.valueOf(gross), TaxRegime.NEW);
                if (previousTax != null) {
                    BigDecimal extraTax = r.totalTax().subtract(previousTax);
                    BigDecimal extraIncome = new BigDecimal(gross - previousGross);
                    assertThat(extraTax)
                            .as("tax must never rise faster than income plus cess (at ₹%d)", gross)
                            .isLessThanOrEqualTo(extraIncome.multiply(new BigDecimal("1.04")));
                    assertThat(extraTax).as("and never fall (at ₹%d)", gross)
                            .isGreaterThanOrEqualTo(BigDecimal.ZERO);
                }
                previousTax = r.totalTax();
                previousGross = gross;
            }
        }

        @Test
        @DisplayName("a published worked example: ₹12,25,000 taxable pays ₹26,000")
        void matches_a_published_example() {
            // ClearTax's own illustration of marginal relief, used as an outside check rather than
            // another number of our own: total income ₹12,25,000, slab tax ₹63,750, relief brings it
            // to the ₹25,000 earned above the line, cess takes it to ₹26,000.
            // Gross is ₹13,00,000 because the standard deduction of ₹75,000 comes off first.
            IncomeTaxCalculator.Result r = tax("1300000", TaxRegime.NEW);
            assertThat(r.taxableIncome()).isEqualByComparingTo(rs("1225000"));
            assertThat(r.taxOnIncome()).isEqualByComparingTo(rs("63750"));
            assertThat(r.taxAfterRebate()).isEqualByComparingTo(rs("25000"));
            assertThat(r.totalTax()).isEqualByComparingTo(rs("26000"));
        }

        @Test
        @DisplayName("₹20,00,000 pays slab tax across five bands, plus 4% cess")
        void twenty_lakh() {
            // 20,00,000 - 75,000 = 19,25,000 taxable.
            //   4-8L   @5%  = 20,000
            //   8-12L  @10% = 40,000
            //   12-16L @15% = 60,000
            //   16-19.25L @20% = 65,000
            //   tax 1,85,000; no rebate; cess 7,400 -> 1,92,400
            IncomeTaxCalculator.Result r = tax("2000000", TaxRegime.NEW);
            assertThat(r.taxOnIncome()).isEqualByComparingTo(rs("185000"));
            assertThat(r.rebate()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(r.cess()).isEqualByComparingTo(rs("7400"));
            assertThat(r.totalTax()).isEqualByComparingTo(rs("192400"));
            assertThat(r.monthlyTds()).isEqualByComparingTo(rs("16033"));
        }

        @Test
        @DisplayName("80C is ignored, but the employer's NPS contribution is not")
        void only_employer_nps_survives() {
            Map<TaxDeduction, BigDecimal> declared = new EnumMap<>(TaxDeduction.class);
            declared.put(TaxDeduction.SECTION_80C, rs("150000"));
            declared.put(TaxDeduction.SECTION_80CCD_2, rs("100000"));

            IncomeTaxCalculator.Result r = tax("2000000", TaxRegime.NEW, declared);
            // Only the 1,00,000 of employer NPS reduces income: 20,00,000 - 75,000 - 1,00,000.
            assertThat(r.taxableIncome()).isEqualByComparingTo(rs("1825000"));
            assertThat(r.totalDeductions()).isEqualByComparingTo(rs("100000"));
        }
    }

    @Nested
    @DisplayName("the old regime")
    class Old {

        @Test
        @DisplayName("₹5,00,000 of taxable income pays nothing, via the ₹12,500 rebate")
        void five_lakh_is_free() {
            // 5,50,000 - 50,000 standard = 5,00,000 taxable.
            //   2.5-5L @5% = 12,500, and the rebate is exactly 12,500.
            IncomeTaxCalculator.Result r = tax("550000", TaxRegime.OLD);
            assertThat(r.taxableIncome()).isEqualByComparingTo(rs("500000"));
            assertThat(r.taxOnIncome()).isEqualByComparingTo(rs("12500"));
            assertThat(r.totalTax()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("the old rebate has no marginal relief — it simply stops")
        void the_old_rebate_stops_dead() {
            // 5,51,000 - 50,000 = 5,01,000 taxable. Over the line, so no rebate at all:
            //   2.5-5L @5% = 12,500; 5L-5.01L @20% = 200; tax 12,700; cess 508 -> 13,208.
            IncomeTaxCalculator.Result r = tax("551000", TaxRegime.OLD);
            assertThat(r.rebate()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(r.totalTax()).isEqualByComparingTo(rs("13208"));
        }

        @Test
        @DisplayName("deductions are what the old regime is for")
        void deductions_reduce_the_bill() {
            Map<TaxDeduction, BigDecimal> declared = new EnumMap<>(TaxDeduction.class);
            declared.put(TaxDeduction.SECTION_80C, rs("150000"));
            declared.put(TaxDeduction.SECTION_80CCD_1B, rs("50000"));
            declared.put(TaxDeduction.SECTION_80D_SELF, rs("25000"));
            declared.put(TaxDeduction.HOME_LOAN_INTEREST, rs("200000"));

            // 12,00,000 - 50,000 - (1,50,000 + 50,000 + 25,000 + 2,00,000) = 7,25,000 taxable.
            //   2.5-5L @5% = 12,500; 5-7.25L @20% = 45,000; tax 57,500; cess 2,300 -> 59,800.
            IncomeTaxCalculator.Result r = tax("1200000", TaxRegime.OLD, declared);
            assertThat(r.totalDeductions()).isEqualByComparingTo(rs("425000"));
            assertThat(r.taxableIncome()).isEqualByComparingTo(rs("725000"));
            assertThat(r.totalTax()).isEqualByComparingTo(rs("59800"));
        }

        @Test
        @DisplayName("a claim beyond the statutory cap is trimmed, not honoured")
        void caps_are_enforced_here_not_in_the_form() {
            Map<TaxDeduction, BigDecimal> declared = new EnumMap<>(TaxDeduction.class);
            declared.put(TaxDeduction.SECTION_80C, rs("500000"));   // the cap is 1,50,000

            IncomeTaxCalculator.Result r = tax("1200000", TaxRegime.OLD, declared);
            assertThat(r.totalDeductions())
                    .as("80C is capped at 1,50,000 however much is declared")
                    .isEqualByComparingTo(rs("150000"));
            // And the screen can still show what was claimed against what was allowed.
            IncomeTaxCalculator.AllowedDeduction c = r.deductions().stream()
                    .filter(d -> d.deduction() == TaxDeduction.SECTION_80C)
                    .findFirst().orElseThrow();
            assertThat(c.declared()).isEqualByComparingTo(rs("500000"));
            assertThat(c.allowed()).isEqualByComparingTo(rs("150000"));
        }
    }

    @Nested
    @DisplayName("surcharge")
    class Surcharge {

        @Test
        @DisplayName("nothing below ₹50 lakh")
        void none_below_fifty_lakh() {
            assertThat(tax("4000000", TaxRegime.NEW).surcharge()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("10% above ₹50 lakh, and marginal relief right at the edge")
        void relief_at_the_fifty_lakh_cliff() {
            // A rupee over the line must not cost more than the rupee earned.
            IncomeTaxCalculator.Result at = tax("5075000", TaxRegime.NEW);       // taxable 50,00,000
            IncomeTaxCalculator.Result just = tax("5085000", TaxRegime.NEW);     // taxable 50,10,000
            BigDecimal extraTax = just.totalTax().subtract(at.totalTax());
            assertThat(extraTax)
                    .as("₹10,000 more income must not cost more than ₹10,000 plus cess")
                    .isLessThanOrEqualTo(rs("10400"));
        }

        @Test
        @DisplayName("no surcharge threshold costs more than the income earned past it, plus cess")
        void no_cliff_anywhere() {
            // Same correction as the rebate cliff: relief caps tax plus surcharge at what was earned
            // above the threshold, and cess is charged on that, so the marginal rate tops out at
            // 104% rather than at 100%. Without relief at all, crossing ₹50 lakh by a rupee would
            // cost about ₹1,40,000, which is the defect this guards.
            for (String[] pair : new String[][]{
                    {"5075000", "5200000"},      // 50 lakh
                    {"10075000", "10300000"},    // 1 crore
                    {"20075000", "20400000"}}) { // 2 crore
                IncomeTaxCalculator.Result lower = tax(pair[0], TaxRegime.NEW);
                IncomeTaxCalculator.Result higher = tax(pair[1], TaxRegime.NEW);
                BigDecimal extraTax = higher.totalTax().subtract(lower.totalTax());
                BigDecimal extraIncome = rs(pair[1]).subtract(rs(pair[0]));
                assertThat(extraTax)
                        .as("crossing the threshold between ₹%s and ₹%s must not cost more than the "
                                + "income gained plus cess", pair[0], pair[1])
                        .isLessThanOrEqualTo(extraIncome.multiply(new BigDecimal("1.04")));
            }
        }

        @Test
        @DisplayName("the new regime caps surcharge at 25%, the old goes to 37%")
        void the_regimes_differ_at_the_top() {
            IncomeTaxCalculator.Result newRegime = tax("60000000", TaxRegime.NEW);
            IncomeTaxCalculator.Result oldRegime = tax("60000000", TaxRegime.OLD);
            // Same income, and the old regime's steeper surcharge makes it the costlier one here
            // even before its narrower slabs are considered.
            assertThat(oldRegime.surcharge()).isGreaterThan(newRegime.surcharge());
        }
    }

    @Nested
    @DisplayName("the edges")
    class Edges {

        @Test
        @DisplayName("no salary, no tax, and nothing negative anywhere")
        void zero_salary() {
            IncomeTaxCalculator.Result r = tax("0", TaxRegime.NEW);
            assertThat(r.taxableIncome()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(r.totalTax()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(r.standardDeduction()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("a salary smaller than the standard deduction does not go negative")
        void tiny_salary() {
            IncomeTaxCalculator.Result r = tax("30000", TaxRegime.NEW);
            assertThat(r.taxableIncome()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(r.totalTax()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("deductions larger than the salary leave zero, not a refund")
        void deductions_cannot_go_below_zero() {
            Map<TaxDeduction, BigDecimal> declared = new EnumMap<>(TaxDeduction.class);
            declared.put(TaxDeduction.SECTION_80C, rs("150000"));
            declared.put(TaxDeduction.HOME_LOAN_INTEREST, rs("200000"));

            IncomeTaxCalculator.Result r = tax("200000", TaxRegime.OLD, declared);
            assertThat(r.taxableIncome()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(r.totalTax()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("the slab working adds up to the tax it reports")
        void the_working_reconciles() {
            IncomeTaxCalculator.Result r = tax("3000000", TaxRegime.NEW);
            BigDecimal summed = r.bands().stream()
                    .map(IncomeTaxCalculator.BandTax::tax)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            // The screen shows these rows and the total beside them; if they disagree the employee
            // is right to distrust the payslip.
            assertThat(summed).isEqualByComparingTo(r.taxOnIncome());
            BigDecimal slices = r.bands().stream()
                    .map(IncomeTaxCalculator.BandTax::taxable)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(slices).isEqualByComparingTo(r.taxableIncome());
        }

        @Test
        @DisplayName("a null regime is taxed under the statutory default")
        void null_regime_defaults() {
            IncomeTaxCalculator.Result r = IncomeTaxCalculator.compute(
                    new IncomeTaxCalculator.Input(rs("2000000"), null, null));
            assertThat(r.regime()).isEqualTo(TaxRegime.DEFAULT);
        }
    }
}
