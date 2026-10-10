package com.calyvora.tax;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Worked examples, checked against the Act rather than against the implementation.
 *
 * <p>Every figure below was computed by hand from the tax year 2026-27 rules and written down before
 * the calculator was run — the working is in the comment beside it. A number copied out of a debugger
 * proves only that the code does what it does.
 *
 * <p>Total income and tax are both rounded to the nearest ₹10 (Section 516 of the 2025 Act).
 */
class IncomeTaxCalculatorTest {

    private static BigDecimal rs(String v) {
        return new BigDecimal(v);
    }

    private static IncomeTaxCalculator.Result tax(String gross, TaxRegime regime) {
        return IncomeTaxCalculator.compute(IncomeTaxCalculator.Input.of(rs(gross), regime));
    }

    private static IncomeTaxCalculator.Result tax(String gross, TaxRegime regime, Map<TaxDeduction, BigDecimal> declared) {
        return IncomeTaxCalculator.compute(IncomeTaxCalculator.Input.of(rs(gross), regime, declared));
    }

    /** A fuller input: age, basic, PF, PT, HRA and the rest. */
    private static IncomeTaxCalculator.Input input(String gross, TaxRegime regime, AgeBand age, String basic,
                                                   String pf, String pt, String hraExempt, String hraReceived,
                                                   String lta, String rent, boolean parentsSenior,
                                                   List<IncomeTaxCalculator.HouseProperty> houses,
                                                   Map<TaxDeduction, BigDecimal> declared) {
        return new IncomeTaxCalculator.Input(rs(gross), regime, age, rs(basic), rs(pf), rs(pt), rs(hraExempt),
                rs(hraReceived), rs(lta), rs(rent), parentsSenior, houses, declared);
    }

    private static Map<TaxDeduction, BigDecimal> declare(Object... pairs) {
        Map<TaxDeduction, BigDecimal> m = new EnumMap<>(TaxDeduction.class);
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((TaxDeduction) pairs[i], rs((String) pairs[i + 1]));
        }
        return m;
    }

    private static BigDecimal allowed(IncomeTaxCalculator.Result r, String key) {
        return r.deductions().stream().filter(d -> d.key().equals(key)).findFirst()
                .map(IncomeTaxCalculator.AllowedDeduction::allowed).orElse(BigDecimal.ZERO);
    }

    @Nested
    @DisplayName("the new regime")
    class New {

        @Test
        @DisplayName("₹12,00,000 gross pays nothing — the rebate wipes out the slab tax")
        void twelve_lakh_is_free() {
            // 12,00,000 - 75,000 = 11,25,000. 4-8L @5% 20,000 + 8-11.25L @10% 32,500 = 52,500; rebate all.
            IncomeTaxCalculator.Result r = tax("1200000", TaxRegime.NEW);
            assertThat(r.taxableIncome()).isEqualByComparingTo(rs("1125000"));
            assertThat(r.taxOnIncome()).isEqualByComparingTo(rs("52500"));
            assertThat(r.rebate()).isEqualByComparingTo(rs("52500"));
            assertThat(r.totalTax()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("₹12,75,000 gross — exactly on the rebate line — pays nothing")
        void exactly_on_the_line() {
            // 12,00,000 taxable; tax 60,000; rebate 60,000.
            IncomeTaxCalculator.Result r = tax("1275000", TaxRegime.NEW);
            assertThat(r.taxableIncome()).isEqualByComparingTo(rs("1200000"));
            assertThat(r.totalTax()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("marginal relief: ₹10,000 over the line costs ₹10,000 plus cess")
        void marginal_relief_on_the_rebate() {
            // 12,10,000 taxable. Slab tax 60,000 + 10,000 @15% 1,500 = 61,500; relief caps it at the
            // 10,000 above the line; +4% = 10,400.
            IncomeTaxCalculator.Result r = tax("1285000", TaxRegime.NEW);
            assertThat(r.taxableIncome()).isEqualByComparingTo(rs("1210000"));
            assertThat(r.taxOnIncome()).isEqualByComparingTo(rs("61500"));
            assertThat(r.taxAfterRebate()).isEqualByComparingTo(rs("10000"));
            assertThat(r.totalTax()).isEqualByComparingTo(rs("10400"));
        }

        @Test
        @DisplayName("a published worked example: ₹12,25,000 taxable pays ₹26,000")
        void matches_a_published_example() {
            // ClearTax's illustration: slab tax 63,750, relief to 25,000, cess to 26,000.
            IncomeTaxCalculator.Result r = tax("1300000", TaxRegime.NEW);
            assertThat(r.taxOnIncome()).isEqualByComparingTo(rs("63750"));
            assertThat(r.totalTax()).isEqualByComparingTo(rs("26000"));
        }

        @Test
        @DisplayName("past the relief zone, the rebate is gone: ₹16,00,000 gross")
        void sixteen_lakh() {
            // 15,25,000 taxable. 20,000 + 40,000 + 3,25,000 @15% 48,750 = 1,08,750. Relief: excess 3,25,000
            // is more than the tax, so no rebate. Cess 4,350 -> 1,13,100.
            IncomeTaxCalculator.Result r = tax("1600000", TaxRegime.NEW);
            assertThat(r.taxOnIncome()).isEqualByComparingTo(rs("108750"));
            assertThat(r.rebate()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(r.totalTax()).isEqualByComparingTo(rs("113100"));
        }

        @Test
        @DisplayName("₹20,00,000 gross: five bands plus 4% cess")
        void twenty_lakh() {
            // 19,25,000: 20,000 + 40,000 + 60,000 + 65,000 = 1,85,000; cess 7,400 -> 1,92,400.
            IncomeTaxCalculator.Result r = tax("2000000", TaxRegime.NEW);
            assertThat(r.taxOnIncome()).isEqualByComparingTo(rs("185000"));
            assertThat(r.cess()).isEqualByComparingTo(rs("7400"));
            assertThat(r.totalTax()).isEqualByComparingTo(rs("192400"));
        }

        @Test
        @DisplayName("₹30,00,000 gross reaches the 30% band")
        void thirty_lakh() {
            // 29,25,000: 20,000+40,000+60,000+80,000+1,00,000 + 5,25,000 @30% 1,57,500 = 4,57,500.
            // Cess 18,300 -> 4,75,800.
            IncomeTaxCalculator.Result r = tax("3000000", TaxRegime.NEW);
            assertThat(r.taxOnIncome()).isEqualByComparingTo(rs("457500"));
            assertThat(r.totalTax()).isEqualByComparingTo(rs("475800"));
        }

        @Test
        @DisplayName("Section 123 is ignored, but the employer's NPS counts — up to 14% of basic")
        void only_employer_nps_survives() {
            // Basic 10,00,000 -> 14% = 1,40,000 cap; 1,00,000 declared, all allowed. 123 ignored.
            IncomeTaxCalculator.Result r = IncomeTaxCalculator.compute(input("2000000", TaxRegime.NEW,
                    AgeBand.BELOW_60, "1000000", "0", "0", "0", "0", "0", "0", false, List.of(),
                    declare(TaxDeduction.PPF, "150000", TaxDeduction.EMPLOYER_NPS, "100000")));
            assertThat(r.totalDeductions()).isEqualByComparingTo(rs("100000"));
            assertThat(r.taxableIncome()).isEqualByComparingTo(rs("1825000"));
            assertThat(allowed(r, "PPF")).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("employer's NPS above 14% of basic is trimmed")
        void employer_nps_ceiling() {
            // Basic 6,00,000: 14% = 84,000.
            IncomeTaxCalculator.Result r = IncomeTaxCalculator.compute(input("1500000", TaxRegime.NEW,
                    AgeBand.BELOW_60, "600000", "0", "0", "0", "0", "0", "0", false, List.of(),
                    declare(TaxDeduction.EMPLOYER_NPS, "100000")));
            assertThat(allowed(r, "EMPLOYER_NPS")).isEqualByComparingTo(rs("84000"));
        }

        @Test
        @DisplayName("no HRA, PT or home-loan interest in the new regime")
        void old_regime_reliefs_do_not_apply() {
            IncomeTaxCalculator.Result r = IncomeTaxCalculator.compute(input("1800000", TaxRegime.NEW,
                    AgeBand.BELOW_60, "900000", "21600", "2500", "200000", "360000", "0", "300000", false,
                    List.of(new IncomeTaxCalculator.HouseProperty(false, null, null, rs("250000"))), Map.of()));
            // 18,00,000 - 75,000 = 17,25,000, nothing else.
            assertThat(r.taxableIncome()).isEqualByComparingTo(rs("1725000"));
            assertThat(r.professionalTax()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(r.houseProperty()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("interest income is taxed in the new regime too, with no 153 deduction")
        void other_income_counts() {
            // 12,00,000 - 75,000 + 1,50,000 interest = 12,75,000 taxable.
            // Tax 60,000 + 75,000 @15% 11,250 = 71,250. Relief would cap it at the 75,000 above the line,
            // which is more than the tax, so no relief: 71,250 + 4% = 74,100.
            IncomeTaxCalculator.Result r = tax("1200000", TaxRegime.NEW,
                    declare(TaxDeduction.DEPOSIT_INTEREST, "150000"));
            assertThat(r.otherIncome()).isEqualByComparingTo(rs("150000"));
            assertThat(r.taxableIncome()).isEqualByComparingTo(rs("1275000"));
            assertThat(r.taxOnIncome()).isEqualByComparingTo(rs("71250"));
            assertThat(r.totalTax()).isEqualByComparingTo(rs("74100"));
        }
    }

    @Nested
    @DisplayName("the old regime")
    class Old {

        @Test
        @DisplayName("₹5,00,000 taxable pays nothing, via the ₹12,500 rebate")
        void five_lakh_is_free() {
            IncomeTaxCalculator.Result r = tax("550000", TaxRegime.OLD);
            assertThat(r.taxableIncome()).isEqualByComparingTo(rs("500000"));
            assertThat(r.totalTax()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("the old rebate has no marginal relief, and the tax rounds to ten")
        void the_old_rebate_stops_dead() {
            // 5,01,000 taxable: 12,500 + 200 = 12,700; cess 508 -> 13,208 -> rounded 13,210.
            IncomeTaxCalculator.Result r = tax("551000", TaxRegime.OLD);
            assertThat(r.rebate()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(r.totalTax()).isEqualByComparingTo(rs("13210"));
        }

        @Test
        @DisplayName("a full declaration: PF from payroll, 123 lines, NPS, health, home loan, HRA, PT")
        void a_realistic_old_regime_employee() {
            // Gross 15,00,000; basic 6,00,000; HRA received 3,00,000 of which 1,80,000 exempt.
            // PF 72,000 (12% of 6L) from payroll; PT 2,400.
            //   Salary: 15,00,000 - 1,80,000 HRA = 13,20,000 - 50,000 std = 12,70,000 - 2,400 PT = 12,67,600
            //   Home loan interest (self-occupied) 2,40,000 -> -2,00,000. GTI 10,67,600.
            //   123: PF 72,000 + LIC 30,000 + ELSS 80,000 = 1,82,000 -> capped 1,50,000.
            //   124(3) NPS 50,000. 126 self 20,000 + parents (not senior) 30,000 -> 25,000.
            //   Deductions 2,45,000. Total income 8,22,600.
            //   Tax: 12,500 + 3,22,600 @20% 64,520 = 77,020; cess 3,080.80 -> 80,100.80 -> 80,100.
            IncomeTaxCalculator.Result r = IncomeTaxCalculator.compute(input("1500000", TaxRegime.OLD,
                    AgeBand.BELOW_60, "600000", "72000", "2400", "180000", "300000", "0", "240000", false,
                    List.of(new IncomeTaxCalculator.HouseProperty(false, null, null, rs("240000"))),
                    declare(TaxDeduction.LIFE_INSURANCE, "30000", TaxDeduction.ELSS, "80000",
                            TaxDeduction.NPS_ADDITIONAL, "50000", TaxDeduction.HEALTH_SELF_PREMIUM, "20000",
                            TaxDeduction.HEALTH_PARENTS_PREMIUM, "30000")));
            assertThat(r.salaryIncome()).isEqualByComparingTo(rs("1267600"));
            assertThat(r.houseProperty()).isEqualByComparingTo(rs("-200000"));
            assertThat(r.grossTotalIncome()).isEqualByComparingTo(rs("1067600"));
            assertThat(allowed(r, "EPF")).isEqualByComparingTo(rs("72000"));
            assertThat(allowed(r, "LIFE_INSURANCE")).isEqualByComparingTo(rs("30000"));
            assertThat(allowed(r, "ELSS")).isEqualByComparingTo(rs("48000"));
            assertThat(allowed(r, "HEALTH_PARENTS_PREMIUM")).isEqualByComparingTo(rs("25000"));
            assertThat(r.totalDeductions()).isEqualByComparingTo(rs("245000"));
            assertThat(r.taxableIncome()).isEqualByComparingTo(rs("822600"));
            assertThat(r.taxOnIncome()).isEqualByComparingTo(rs("77020"));
            assertThat(r.totalTax()).isEqualByComparingTo(rs("80100"));
        }

        @Test
        @DisplayName("your own NPS is held to 10% of basic before it joins Section 123")
        void own_nps_ceiling() {
            // Basic 3,00,000 -> 30,000 of a 60,000 NPS claim; PPF 50,000. Section 123 total 80,000.
            IncomeTaxCalculator.Result r = IncomeTaxCalculator.compute(input("900000", TaxRegime.OLD,
                    AgeBand.BELOW_60, "300000", "0", "0", "0", "0", "0", "0", false, List.of(),
                    declare(TaxDeduction.PPF, "50000", TaxDeduction.NPS_EMPLOYEE, "60000")));
            assertThat(allowed(r, "NPS_EMPLOYEE")).isEqualByComparingTo(rs("30000"));
            assertThat(r.totalDeductions()).isEqualByComparingTo(rs("80000"));
        }

        @Test
        @DisplayName("senior parents raise their ceiling to ₹50,000; the check-up is ₹5,000 for everyone")
        void health_ceilings() {
            // Self: premium 20,000 + check-up 4,000 = 24,000 (under 25,000).
            // Parents (senior): premium 45,000 + check-up 3,000 -> check-up room only 1,000 (5,000 family-wide)
            //   -> 46,000, under 50,000.
            IncomeTaxCalculator.Result r = IncomeTaxCalculator.compute(input("1200000", TaxRegime.OLD,
                    AgeBand.BELOW_60, "500000", "0", "0", "0", "0", "0", "0", true, List.of(),
                    declare(TaxDeduction.HEALTH_SELF_PREMIUM, "20000", TaxDeduction.HEALTH_SELF_CHECKUP, "4000",
                            TaxDeduction.HEALTH_PARENTS_PREMIUM, "45000", TaxDeduction.HEALTH_PARENTS_CHECKUP, "3000")));
            assertThat(allowed(r, "HEALTH_SELF_CHECKUP")).isEqualByComparingTo(rs("4000"));
            assertThat(allowed(r, "HEALTH_PARENTS_CHECKUP")).isEqualByComparingTo(rs("1000"));
            assertThat(r.totalDeductions()).isEqualByComparingTo(rs("70000"));
        }

        @Test
        @DisplayName("medical bills count only for an uninsured senior")
        void medical_bills() {
            Map<TaxDeduction, BigDecimal> d = declare(TaxDeduction.HEALTH_SELF_MEDICAL, "30000");
            IncomeTaxCalculator.Result young = IncomeTaxCalculator.compute(input("1200000", TaxRegime.OLD,
                    AgeBand.BELOW_60, "500000", "0", "0", "0", "0", "0", "0", false, List.of(), d));
            IncomeTaxCalculator.Result senior = IncomeTaxCalculator.compute(input("1200000", TaxRegime.OLD,
                    AgeBand.SENIOR, "500000", "0", "0", "0", "0", "0", "0", false, List.of(), d));
            assertThat(allowed(young, "HEALTH_SELF_MEDICAL")).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(allowed(senior, "HEALTH_SELF_MEDICAL")).isEqualByComparingTo(rs("30000"));
        }

        @Test
        @DisplayName("a senior's first ₹3,00,000 is free, and interest up to ₹50,000 comes off")
        void senior_citizen() {
            // Gross 8,00,000 - 50,000 = 7,50,000; + deposit interest 60,000 = GTI 8,10,000.
            // 153 (80TTB): 50,000. Total income 7,60,000.
            // Senior slabs: 3-5L @5% 10,000 + 2,60,000 @20% 52,000 = 62,000; cess 2,480 -> 64,480.
            IncomeTaxCalculator.Result r = IncomeTaxCalculator.compute(input("800000", TaxRegime.OLD,
                    AgeBand.SENIOR, "400000", "0", "0", "0", "0", "0", "0", false, List.of(),
                    declare(TaxDeduction.DEPOSIT_INTEREST, "60000")));
            assertThat(r.taxableIncome()).isEqualByComparingTo(rs("760000"));
            assertThat(r.taxOnIncome()).isEqualByComparingTo(rs("62000"));
            assertThat(r.totalTax()).isEqualByComparingTo(rs("64480"));
        }

        @Test
        @DisplayName("a super-senior's first ₹5,00,000 is free")
        void super_senior() {
            // 9,50,000 - 50,000 = 9,00,000. 5-9L @20% = 80,000; cess 3,200 -> 83,200.
            IncomeTaxCalculator.Result r = IncomeTaxCalculator.compute(input("950000", TaxRegime.OLD,
                    AgeBand.SUPER_SENIOR, "400000", "0", "0", "0", "0", "0", "0", false, List.of(), Map.of()));
            assertThat(r.totalTax()).isEqualByComparingTo(rs("83200"));
        }

        @Test
        @DisplayName("savings interest: ₹10,000 comes off, deposit interest stays taxed under 60")
        void young_interest() {
            // 10,00,000 - 50,000 + 15,000 savings + 20,000 FD = 9,85,000; 153 deduction 10,000 -> 9,75,000.
            IncomeTaxCalculator.Result r = tax("1000000", TaxRegime.OLD,
                    declare(TaxDeduction.SAVINGS_INTEREST, "15000", TaxDeduction.DEPOSIT_INTEREST, "20000"));
            assertThat(r.taxableIncome()).isEqualByComparingTo(rs("975000"));
        }

        @Test
        @DisplayName("a let-out house: 30% off the rent, interest in full, and a loss capped at ₹2,00,000")
        void let_out_house() {
            // Rent 2,40,000, municipal tax 20,000 -> NAV 2,20,000; 30% 66,000 -> 1,54,000; interest 4,00,000
            // -> loss 2,46,000, set off only 2,00,000. 12,00,000 - 50,000 - 2,00,000 = 9,50,000.
            IncomeTaxCalculator.Result r = IncomeTaxCalculator.compute(input("1200000", TaxRegime.OLD,
                    AgeBand.BELOW_60, "500000", "0", "0", "0", "0", "0", "0", false,
                    List.of(new IncomeTaxCalculator.HouseProperty(true, rs("240000"), rs("20000"), rs("400000"))),
                    Map.of()));
            assertThat(r.houseProperty()).isEqualByComparingTo(rs("-200000"));
            assertThat(r.taxableIncome()).isEqualByComparingTo(rs("950000"));
        }

        @Test
        @DisplayName("a let-out house's profit is taxed in the new regime, its loss is not set off")
        void let_out_new_regime() {
            IncomeTaxCalculator.Result profit = IncomeTaxCalculator.compute(input("1000000", TaxRegime.NEW,
                    AgeBand.BELOW_60, "500000", "0", "0", "0", "0", "0", "0", false,
                    List.of(new IncomeTaxCalculator.HouseProperty(true, rs("300000"), rs("0"), rs("100000"))),
                    Map.of()));
            // 3,00,000 - 90,000 - 1,00,000 = 1,10,000 added.
            assertThat(profit.houseProperty()).isEqualByComparingTo(rs("110000"));
            IncomeTaxCalculator.Result loss = IncomeTaxCalculator.compute(input("1000000", TaxRegime.NEW,
                    AgeBand.BELOW_60, "500000", "0", "0", "0", "0", "0", "0", false,
                    List.of(new IncomeTaxCalculator.HouseProperty(true, rs("100000"), rs("0"), rs("300000"))),
                    Map.of()));
            assertThat(loss.houseProperty()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("LTA is exempt only up to the LTA in the salary")
        void lta() {
            IncomeTaxCalculator.Result r = IncomeTaxCalculator.compute(input("1000000", TaxRegime.OLD,
                    AgeBand.BELOW_60, "500000", "0", "0", "0", "0", "30000", "0", false, List.of(),
                    declare(TaxDeduction.LTA, "45000")));
            assertThat(r.exemptions().get(0).allowed()).isEqualByComparingTo(rs("30000"));
            // 10,00,000 - 30,000 - 50,000 = 9,20,000.
            assertThat(r.taxableIncome()).isEqualByComparingTo(rs("920000"));
        }

        @Test
        @DisplayName("professional tax, payroll's and elsewhere's together, stops at ₹2,500")
        void professional_tax() {
            IncomeTaxCalculator.Result r = IncomeTaxCalculator.compute(input("1000000", TaxRegime.OLD,
                    AgeBand.BELOW_60, "500000", "0", "2000", "0", "0", "0", "0", false, List.of(),
                    declare(TaxDeduction.PROFESSIONAL_TAX_OTHER, "1200")));
            assertThat(r.professionalTax()).isEqualByComparingTo(rs("2500"));
        }

        @Test
        @DisplayName("80DD and 80U are fixed amounts, the severe one replacing the ordinary")
        void fixed_amounts() {
            IncomeTaxCalculator.Result r = tax("1500000", TaxRegime.OLD,
                    declare(TaxDeduction.DISABLED_DEPENDENT, "10000", TaxDeduction.SELF_DISABILITY_SEVERE, "1",
                            TaxDeduction.SELF_DISABILITY, "1"));
            assertThat(allowed(r, "DISABLED_DEPENDENT")).isEqualByComparingTo(rs("75000"));
            assertThat(allowed(r, "SELF_DISABILITY_SEVERE")).isEqualByComparingTo(rs("125000"));
            assertThat(allowed(r, "SELF_DISABILITY")).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(r.totalDeductions()).isEqualByComparingTo(rs("200000"));
        }

        @Test
        @DisplayName("Section 130 and 131 cannot both be claimed")
        void home_loan_sections() {
            IncomeTaxCalculator.Result r = tax("1500000", TaxRegime.OLD,
                    declare(TaxDeduction.FIRST_HOME_LOAN, "70000", TaxDeduction.AFFORDABLE_HOME_LOAN, "100000"));
            assertThat(allowed(r, "FIRST_HOME_LOAN")).isEqualByComparingTo(rs("50000"));
            assertThat(allowed(r, "AFFORDABLE_HOME_LOAN")).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("donations: 100% and 50%, and the limited kind held to 10% of adjusted income")
        void donations() {
            // 10,00,000 - 50,000 = 9,50,000 GTI; PPF 1,50,000 -> 8,00,000 adjusted; limit 80,000.
            // 100% no limit 10,000; 50% no limit 20,000 -> 10,000; 50% limited 1,00,000 -> only 80,000
            // qualifies -> 40,000. Donations 60,000; total deductions 2,10,000.
            IncomeTaxCalculator.Result r = tax("1000000", TaxRegime.OLD,
                    declare(TaxDeduction.PPF, "150000", TaxDeduction.DONATION_100, "10000",
                            TaxDeduction.DONATION_50, "20000", TaxDeduction.DONATION_50_LIMITED, "100000"));
            assertThat(allowed(r, "DONATION_50")).isEqualByComparingTo(rs("10000"));
            assertThat(allowed(r, "DONATION_50_LIMITED")).isEqualByComparingTo(rs("40000"));
            assertThat(r.totalDeductions()).isEqualByComparingTo(rs("210000"));
        }

        @Test
        @DisplayName("rent with no HRA at all: the least of ₹60,000, 25%, and rent less 10%")
        void rent_without_hra() {
            // 6,50,000 - 50,000 = 6,00,000 adjusted. 25% = 1,50,000; rent 1,20,000 - 60,000 = 60,000;
            // cap 60,000 -> 60,000. Total income 5,40,000.
            IncomeTaxCalculator.Result r = IncomeTaxCalculator.compute(input("650000", TaxRegime.OLD,
                    AgeBand.BELOW_60, "300000", "0", "0", "0", "0", "0", "120000", false, List.of(), Map.of()));
            assertThat(allowed(r, "RENT_NO_HRA")).isEqualByComparingTo(rs("60000"));
            assertThat(r.taxableIncome()).isEqualByComparingTo(rs("540000"));
        }

        @Test
        @DisplayName("an old HRA figure is honoured only without rent details, and never above HRA received")
        void legacy_hra() {
            IncomeTaxCalculator.Result r = IncomeTaxCalculator.compute(input("1000000", TaxRegime.OLD,
                    AgeBand.BELOW_60, "500000", "0", "0", "0", "100000", "0", "0", false, List.of(),
                    declare(TaxDeduction.HRA_EXEMPTION, "150000")));
            assertThat(r.exemptions().get(0).allowed()).isEqualByComparingTo(rs("100000"));
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
        @DisplayName("₹60 lakh taxable: 10% surcharge, no relief needed")
        void sixty_lakh() {
            // 60,75,000 gross -> 60,00,000. Tax 3,00,000 to 24L + 36,00,000 @30% 10,80,000 = 13,80,000.
            // Surcharge 1,38,000. Cess (15,18,000 × 4%) 60,720 -> 15,78,720.
            IncomeTaxCalculator.Result r = tax("6075000", TaxRegime.NEW);
            assertThat(r.taxOnIncome()).isEqualByComparingTo(rs("1380000"));
            assertThat(r.surcharge()).isEqualByComparingTo(rs("138000"));
            assertThat(r.totalTax()).isEqualByComparingTo(rs("1578720"));
        }

        @Test
        @DisplayName("relief at ₹1 crore is measured against tax plus the 10% surcharge, not bare tax")
        void relief_above_one_crore() {
            // Taxable 1,00,10,000 (gross 1,00,85,000).
            //   Tax: 3,00,000 + 76,10,000 @30% 22,83,000 = 25,83,000. 15% surcharge 3,87,450 -> 29,70,450.
            //   At ₹1 crore: tax 25,80,000 + 10% 2,58,000 = 28,38,000; + 10,000 earned above = 28,48,000.
            //   So relief: surcharge = 28,48,000 - 25,83,000 = 2,65,000. Cess 1,13,920 -> 29,61,920.
            // (Measured against bare tax, the ceiling would be 25,90,000 and the surcharge 7,000.)
            IncomeTaxCalculator.Result r = tax("10085000", TaxRegime.NEW);
            assertThat(r.taxableIncome()).isEqualByComparingTo(rs("10010000"));
            assertThat(r.surcharge()).isEqualByComparingTo(rs("265000"));
            assertThat(r.totalTax()).isEqualByComparingTo(rs("2961920"));
        }

        @Test
        @DisplayName("no threshold costs more than the income earned past it, plus cess")
        void no_cliff_anywhere() {
            for (String[] pair : new String[][]{
                    {"5075000", "5200000"}, {"10075000", "10300000"}, {"20075000", "20400000"},
                    {"50075000", "51000000"}}) {
                for (TaxRegime regime : TaxRegime.values()) {
                    IncomeTaxCalculator.Result lower = tax(pair[0], regime);
                    IncomeTaxCalculator.Result higher = tax(pair[1], regime);
                    BigDecimal extraTax = higher.totalTax().subtract(lower.totalTax());
                    BigDecimal extraIncome = rs(pair[1]).subtract(rs(pair[0]));
                    assertThat(extraTax)
                            .as("%s regime, ₹%s to ₹%s", regime, pair[0], pair[1])
                            .isLessThanOrEqualTo(extraIncome.multiply(new BigDecimal("1.04")).add(BigDecimal.TEN));
                }
            }
        }

        @Test
        @DisplayName("tax never falls as income rises, in either regime, across the whole range")
        void monotonic() {
            for (TaxRegime regime : TaxRegime.values()) {
                BigDecimal previous = BigDecimal.ZERO;
                for (long g = 0; g <= 60_000_000; g += 137_000) {
                    BigDecimal t = tax(String.valueOf(g), regime).totalTax();
                    assertThat(t).as("%s regime at ₹%d", regime, g).isGreaterThanOrEqualTo(previous);
                    previous = t;
                }
            }
        }

        @Test
        @DisplayName("the new regime caps surcharge at 25%, the old goes to 37%")
        void the_regimes_differ_at_the_top() {
            assertThat(tax("60000000", TaxRegime.OLD).surcharge())
                    .isGreaterThan(tax("60000000", TaxRegime.NEW).surcharge());
        }
    }

    @Nested
    @DisplayName("rounding and age")
    class Rounding {

        @Test
        @DisplayName("Section 516: paise ignored, then the nearest ten — five rounds up")
        void round_to_ten() {
            assertThat(IncomeTaxCalculator.roundToTen(rs("12344.99"))).isEqualByComparingTo(rs("12340"));
            assertThat(IncomeTaxCalculator.roundToTen(rs("12345"))).isEqualByComparingTo(rs("12350"));
            assertThat(IncomeTaxCalculator.roundToTen(rs("12349.50"))).isEqualByComparingTo(rs("12350"));
        }

        @Test
        @DisplayName("someone born on 1 April turns 60 the day before, so is a senior that year")
        void age_bands() {
            FinancialYear fy = FinancialYear.parse("2026-27");
            assertThat(AgeBand.of(LocalDate.of(1967, 4, 1), fy)).isEqualTo(AgeBand.SENIOR);
            assertThat(AgeBand.of(LocalDate.of(1967, 4, 2), fy)).isEqualTo(AgeBand.BELOW_60);
            assertThat(AgeBand.of(LocalDate.of(1947, 4, 1), fy)).isEqualTo(AgeBand.SUPER_SENIOR);
            assertThat(AgeBand.of(null, fy)).isEqualTo(AgeBand.BELOW_60);
        }
    }

    @Nested
    @DisplayName("the edges")
    class Edges {

        @Test
        @DisplayName("no salary, no tax, nothing negative")
        void zero_salary() {
            IncomeTaxCalculator.Result r = tax("0", TaxRegime.NEW);
            assertThat(r.taxableIncome()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(r.totalTax()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(r.standardDeduction()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("deductions larger than income leave zero, not a refund")
        void deductions_cannot_go_below_zero() {
            IncomeTaxCalculator.Result r = tax("200000", TaxRegime.OLD,
                    declare(TaxDeduction.PPF, "150000", TaxDeduction.EDUCATION_LOAN, "200000"));
            assertThat(r.taxableIncome()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(r.totalDeductions()).isEqualByComparingTo(r.grossTotalIncome());
        }

        @Test
        @DisplayName("the slab working adds up to the tax it reports")
        void the_working_reconciles() {
            IncomeTaxCalculator.Result r = tax("3000000", TaxRegime.NEW);
            BigDecimal summed = r.bands().stream().map(IncomeTaxCalculator.BandTax::tax)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(summed).isEqualByComparingTo(r.taxOnIncome());
        }

        @Test
        @DisplayName("a null regime is taxed under the default, the new regime")
        void null_regime_defaults() {
            assertThat(IncomeTaxCalculator.compute(IncomeTaxCalculator.Input.of(rs("2000000"), null)).regime())
                    .isEqualTo(TaxRegime.NEW);
        }
    }
}
