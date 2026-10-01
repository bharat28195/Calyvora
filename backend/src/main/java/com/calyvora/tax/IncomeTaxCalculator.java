package com.calyvora.tax;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * One salaried person's income tax for one financial year.
 *
 * <p>A pure function of (gross salary, regime, declared deductions). No repository, no tenant, no
 * clock — the same reasoning as {@code PfCalculator}, and for the same reason: this number is
 * deducted from somebody's pay every month and reported to the Income Tax Department in a quarterly
 * return. It has to be checkable by writing down a salary and an expected tax, and those checks have
 * to run in milliseconds so there can be dozens of them.
 *
 * <p><b>Rates in force for FY 2026-27 (AY 2027-28).</b> The Budget of 2026 changed no slab, rebate
 * or limit, so these are also the FY 2025-26 figures. They are constants in one place rather than
 * scattered through the arithmetic, because the one certainty about tax law is that it is amended
 * every February.
 *
 * <p><b>The order of operations is the part that goes wrong.</b> Tax is not "rate × income"; it is a
 * sequence, and each step operates on the output of the last:
 *
 * <ol>
 *   <li>Gross salary, less the standard deduction, less every deduction the chosen regime allows,
 *       gives <em>taxable income</em>.</li>
 *   <li>The slabs apply to that — each rate on the slice of income inside its own band, never on the
 *       whole amount. Someone who crosses into 30% does not pay 30% on all of it, and a calculator
 *       that says otherwise will be disbelieved by every employee who checks.</li>
 *   <li>The 87A rebate is subtracted, and it is a rebate on <em>tax</em>, not an exemption of income.
 *       This is what makes income up to ₹12 lakh tax-free under the new regime despite the slabs
 *       clearly charging tax from ₹4 lakh.</li>
 *   <li>Surcharge applies to the tax, for high incomes, at a rate set by the income.</li>
 *   <li>Health and education cess of 4% applies last, to tax plus surcharge.</li>
 * </ol>
 *
 * <p><b>Marginal relief appears twice and is not optional.</b> Both the rebate and the surcharge have
 * cliffs: without relief, earning one rupee more than ₹12,00,000 would cost about ₹61,000 in tax, and
 * one rupee over ₹50,00,000 about ₹1,40,000. The Act provides relief in both places precisely so that
 * more income never means less money in hand. Leaving it out produces numbers that are wrong in the
 * most visible way possible — for exactly the employees who examine their payslips most closely.
 */
public final class IncomeTaxCalculator {

    private IncomeTaxCalculator() {
    }

    // ---- statutory constants, FY 2026-27 ------------------------------------------------------

    /** Salaried standard deduction under section 16(ia). Larger under the new regime. */
    static final BigDecimal STANDARD_DEDUCTION_NEW = new BigDecimal("75000");
    static final BigDecimal STANDARD_DEDUCTION_OLD = new BigDecimal("50000");

    /** Section 87A: income ceiling to qualify, and the most the rebate can be. */
    static final BigDecimal REBATE_LIMIT_NEW = new BigDecimal("1200000");
    static final BigDecimal REBATE_MAX_NEW = new BigDecimal("60000");
    static final BigDecimal REBATE_LIMIT_OLD = new BigDecimal("500000");
    static final BigDecimal REBATE_MAX_OLD = new BigDecimal("12500");

    /** Health and education cess, on tax plus surcharge. */
    static final BigDecimal CESS_RATE = new BigDecimal("0.04");

    /** A band of income and the rate charged on the part of the income that falls inside it. */
    record Band(BigDecimal upTo, BigDecimal rate) {
    }

    /** null {@code upTo} means "and everything above" — the top band has no ceiling. */
    private static final List<Band> NEW_REGIME_BANDS = List.of(
            new Band(new BigDecimal("400000"), BigDecimal.ZERO),
            new Band(new BigDecimal("800000"), new BigDecimal("0.05")),
            new Band(new BigDecimal("1200000"), new BigDecimal("0.10")),
            new Band(new BigDecimal("1600000"), new BigDecimal("0.15")),
            new Band(new BigDecimal("2000000"), new BigDecimal("0.20")),
            new Band(new BigDecimal("2400000"), new BigDecimal("0.25")),
            new Band(null, new BigDecimal("0.30")));

    /** Below 60. Senior and super-senior citizens get ₹3,00,000 and ₹5,00,000 instead of ₹2,50,000. */
    private static final List<Band> OLD_REGIME_BANDS = List.of(
            new Band(new BigDecimal("250000"), BigDecimal.ZERO),
            new Band(new BigDecimal("500000"), new BigDecimal("0.05")),
            new Band(new BigDecimal("1000000"), new BigDecimal("0.20")),
            new Band(null, new BigDecimal("0.30")));

    /**
     * Surcharge thresholds, highest first.
     *
     * <p>The new regime caps surcharge at 25%; the old regime charges 37% above ₹5 crore.
     */
    private record SurchargeBand(BigDecimal over, BigDecimal oldRate, BigDecimal newRate) {
    }

    private static final List<SurchargeBand> SURCHARGE = List.of(
            new SurchargeBand(new BigDecimal("50000000"), new BigDecimal("0.37"), new BigDecimal("0.25")),
            new SurchargeBand(new BigDecimal("20000000"), new BigDecimal("0.25"), new BigDecimal("0.25")),
            new SurchargeBand(new BigDecimal("10000000"), new BigDecimal("0.15"), new BigDecimal("0.15")),
            new SurchargeBand(new BigDecimal("5000000"), new BigDecimal("0.10"), new BigDecimal("0.10")));

    // ---- inputs and outputs -------------------------------------------------------------------

    /**
     * What the tax is computed from.
     *
     * @param grossSalary annual gross, before any deduction
     * @param regime      which rules to apply
     * @param declared    what the employee declared, uncapped — the caps are applied here
     */
    public record Input(BigDecimal grossSalary, TaxRegime regime, Map<TaxDeduction, BigDecimal> declared) {

        public Input {
            declared = declared == null ? Map.of() : Map.copyOf(declared);
        }

        public static Input of(BigDecimal grossSalary, TaxRegime regime) {
            return new Input(grossSalary, regime, Map.of());
        }
    }

    /** One slab's contribution, kept so a screen can show the working rather than just the total. */
    public record BandTax(BigDecimal from, BigDecimal to, BigDecimal rate, BigDecimal taxable,
                          BigDecimal tax) {
    }

    /** A deduction that was actually allowed, and what was declared for it. */
    public record AllowedDeduction(TaxDeduction deduction, BigDecimal declared, BigDecimal allowed) {
    }

    /**
     * The whole calculation, step by step.
     *
     * <p>Every intermediate is kept rather than just the total, because the screen this feeds has to
     * answer "why am I paying this" — and an employee who cannot see the working assumes the payroll
     * is wrong.
     */
    public record Result(
            BigDecimal grossSalary,
            TaxRegime regime,
            BigDecimal standardDeduction,
            List<AllowedDeduction> deductions,
            BigDecimal totalDeductions,
            BigDecimal taxableIncome,
            List<BandTax> bands,
            BigDecimal taxOnIncome,
            BigDecimal rebate,
            BigDecimal taxAfterRebate,
            BigDecimal surcharge,
            BigDecimal cess,
            BigDecimal totalTax,
            BigDecimal monthlyTds) {
    }

    // ---- the calculation ----------------------------------------------------------------------

    public static Result compute(Input input) {
        TaxRegime regime = input.regime() == null ? TaxRegime.DEFAULT : input.regime();
        BigDecimal gross = nonNegative(input.grossSalary());

        BigDecimal standard = regime == TaxRegime.NEW ? STANDARD_DEDUCTION_NEW : STANDARD_DEDUCTION_OLD;
        // The standard deduction cannot exceed the salary itself — somebody who earned ₹30,000 in
        // their first part-year does not get a negative income out of it.
        standard = standard.min(gross);

        List<AllowedDeduction> allowed = new ArrayList<>();
        BigDecimal deductionTotal = BigDecimal.ZERO;
        Map<TaxDeduction, BigDecimal> declared = new EnumMap<>(TaxDeduction.class);
        declared.putAll(input.declared());
        for (TaxDeduction d : TaxDeduction.values()) {
            BigDecimal claimed = declared.get(d);
            BigDecimal ok = d.allowable(claimed, regime);
            if (ok.signum() > 0 || (claimed != null && claimed.signum() > 0)) {
                allowed.add(new AllowedDeduction(d, claimed == null ? BigDecimal.ZERO : claimed, ok));
            }
            deductionTotal = deductionTotal.add(ok);
        }

        BigDecimal taxable = gross.subtract(standard).subtract(deductionTotal);
        if (taxable.signum() < 0) {
            taxable = BigDecimal.ZERO;
        }
        taxable = taxable.setScale(0, RoundingMode.DOWN);

        List<Band> bands = regime == TaxRegime.NEW ? NEW_REGIME_BANDS : OLD_REGIME_BANDS;
        List<BandTax> working = slabTax(taxable, bands);
        BigDecimal taxOnIncome = working.stream()
                .map(BandTax::tax)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal rebate = rebate(taxable, taxOnIncome, regime, bands);
        BigDecimal afterRebate = taxOnIncome.subtract(rebate).max(BigDecimal.ZERO);

        BigDecimal surcharge = surcharge(taxable, afterRebate, regime, bands);
        BigDecimal cess = afterRebate.add(surcharge).multiply(CESS_RATE);

        BigDecimal total = afterRebate.add(surcharge).add(cess).setScale(0, RoundingMode.HALF_UP);
        BigDecimal monthly = total.divide(BigDecimal.valueOf(12), 0, RoundingMode.HALF_UP);

        return new Result(gross, regime, standard, List.copyOf(allowed),
                deductionTotal, taxable, working,
                round(taxOnIncome), round(rebate), round(afterRebate),
                round(surcharge), round(cess), total, monthly);
    }

    /**
     * Tax band by band.
     *
     * <p>Each rate applies only to the slice of income inside its own band. This is the single most
     * misunderstood thing about income tax, and showing the slices is how a payslip screen stops the
     * question being asked.
     */
    private static List<BandTax> slabTax(BigDecimal income, List<Band> bands) {
        List<BandTax> out = new ArrayList<>();
        BigDecimal floor = BigDecimal.ZERO;
        for (Band band : bands) {
            if (income.compareTo(floor) <= 0) {
                break;
            }
            BigDecimal ceiling = band.upTo() == null ? income : band.upTo().min(income);
            BigDecimal slice = ceiling.subtract(floor);
            if (slice.signum() > 0) {
                out.add(new BandTax(floor, band.upTo(), band.rate(), slice,
                        slice.multiply(band.rate())));
            }
            if (band.upTo() == null) {
                break;
            }
            floor = band.upTo();
        }
        return List.copyOf(out);
    }

    /**
     * Section 87A, including the marginal relief that makes it safe to cross the threshold.
     *
     * <p>Under the new regime the rebate wipes out the tax on income up to ₹12,00,000. One rupee more
     * and the rebate vanishes entirely, which without relief would turn ₹1 of extra income into about
     * ₹61,000 of extra tax. The Act therefore limits the tax to the amount by which income exceeds
     * the threshold, so the effective rate on that first slice above the line is 100% but never more
     * — you can be no worse off for earning more.
     *
     * <p>The old regime's ₹12,500 rebate has no such relief: it simply stops at ₹5,00,000.
     */
    private static BigDecimal rebate(BigDecimal taxable, BigDecimal taxOnIncome, TaxRegime regime,
                                     List<Band> bands) {
        BigDecimal limit = regime == TaxRegime.NEW ? REBATE_LIMIT_NEW : REBATE_LIMIT_OLD;
        BigDecimal max = regime == TaxRegime.NEW ? REBATE_MAX_NEW : REBATE_MAX_OLD;

        if (taxable.compareTo(limit) <= 0) {
            return taxOnIncome.min(max);
        }
        if (regime != TaxRegime.NEW) {
            return BigDecimal.ZERO;
        }
        // Marginal relief: pay at most what you earned above the line.
        BigDecimal excess = taxable.subtract(limit);
        return taxOnIncome.subtract(excess).max(BigDecimal.ZERO);
    }

    /**
     * Surcharge on the tax, with its own marginal relief at every threshold.
     *
     * <p>Same cliff, four more times. Crossing ₹50,00,000 by a rupee attracts 10% surcharge on the
     * whole tax — roughly ₹1,40,000 — so the Act caps the combined tax and surcharge at what would
     * have been paid at the threshold plus the income earned beyond it.
     */
    private static BigDecimal surcharge(BigDecimal taxable, BigDecimal tax, TaxRegime regime,
                                        List<Band> bands) {
        for (SurchargeBand band : SURCHARGE) {
            if (taxable.compareTo(band.over()) > 0) {
                BigDecimal rate = regime == TaxRegime.NEW ? band.newRate() : band.oldRate();
                BigDecimal raw = tax.multiply(rate);

                // What somebody exactly at the threshold would have paid, with no surcharge.
                BigDecimal taxAtThreshold = slabTax(band.over(), bands).stream()
                        .map(BandTax::tax)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal ceiling = taxAtThreshold.add(taxable.subtract(band.over()));
                if (tax.add(raw).compareTo(ceiling) > 0) {
                    return ceiling.subtract(tax).max(BigDecimal.ZERO);
                }
                return raw;
            }
        }
        return BigDecimal.ZERO;
    }

    private static BigDecimal nonNegative(BigDecimal v) {
        return v == null || v.signum() < 0 ? BigDecimal.ZERO : v;
    }

    private static BigDecimal round(BigDecimal v) {
        return v.setScale(0, RoundingMode.HALF_UP);
    }
}
