package com.calyvora.tax;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * One salaried person's income tax for one tax year, under the Income-tax Act, 2025.
 *
 * <p>A pure function of the facts. No repository, no tenant, no clock — because this number is
 * deducted from somebody's pay every month and reported to the Income Tax Department every quarter,
 * it has to be checkable by writing down a salary and an expected tax, and those checks have to run
 * in milliseconds so there can be dozens of them ({@code IncomeTaxCalculatorTest}).
 *
 * <p><b>Rates for tax year 2026-27.</b> The Budget of 2026 changed no slab, rebate or limit. They are
 * constants in one place because the one certainty about tax law is that it changes every February.
 *
 * <p><b>The order of operations is the part that goes wrong</b>, so it is written out once:
 * <ol>
 *   <li><b>Salary.</b> Gross salary, less exempt allowances (HRA, LTA — old regime only), less the
 *       standard deduction, less professional tax (old regime only).</li>
 *   <li><b>House property.</b> Interest on a self-occupied home is a loss (old regime, ₹2,00,000);
 *       a let-out home is rent less municipal tax, less 30% of that, less its interest. A net loss
 *       comes off other income only up to ₹2,00,000, and not at all in the new regime.</li>
 *   <li><b>Other sources</b> the employee asked to have taxed here — interest and the like.</li>
 *   <li>Those three make <em>gross total income</em>; the Chapter VIII deductions (Sections 123 to
 *       154) come off that, each against its own ceiling, never below zero.</li>
 *   <li>Total income is rounded to the nearest ₹10 (Section 516).</li>
 *   <li>The slabs apply band by band — each rate only on the slice inside its band.</li>
 *   <li>The Section 156 rebate (the old 87A) comes off the tax, with marginal relief in the new
 *       regime so that a rupee over ₹12 lakh never costs sixty thousand.</li>
 *   <li>Surcharge for high incomes, with marginal relief at every threshold.</li>
 *   <li>Health and education cess, 4% of tax plus surcharge.</li>
 *   <li>The tax is kept to the rupee, the way TRACES computes it on Form 130 (Form 16) Part B —
 *       ₹2,93,520 of tax and ₹11,741 of cess make ₹3,05,261, not ₹3,05,260. Rounding the tax to the
 *       nearest ₹10 is for the return the employee files; TDS deposited and certified is exact, and
 *       a payroll that rounded would disagree with every Form 16 by up to ₹5.</li>
 * </ol>
 *
 * <p>Residents only: a non-resident gets neither the rebate nor the senior-citizen slabs, and
 * nothing here models that.
 */
public final class IncomeTaxCalculator {

    private IncomeTaxCalculator() {
    }

    // ---- statutory constants, tax year 2026-27 --------------------------------------------------

    /** Standard deduction for salary (Section 19; 16(ia) before). */
    static final BigDecimal STANDARD_DEDUCTION_NEW = rs(75000);
    static final BigDecimal STANDARD_DEDUCTION_OLD = rs(50000);

    /** Section 156 (87A): income ceiling to qualify, and the most the rebate can be. */
    static final BigDecimal REBATE_LIMIT_NEW = rs(1200000);
    static final BigDecimal REBATE_MAX_NEW = rs(60000);
    static final BigDecimal REBATE_LIMIT_OLD = rs(500000);
    static final BigDecimal REBATE_MAX_OLD = rs(12500);

    static final BigDecimal CESS_RATE = new BigDecimal("0.04");

    static final BigDecimal CAP_123 = rs(150000);
    static final BigDecimal CAP_NPS_EXTRA = rs(50000);
    static final BigDecimal CAP_HEALTH = rs(25000);
    static final BigDecimal CAP_HEALTH_SENIOR = rs(50000);
    static final BigDecimal CAP_CHECKUP = rs(5000);
    static final BigDecimal CAP_PROFESSIONAL_TAX = rs(2500);
    static final BigDecimal CAP_SELF_OCCUPIED_INTEREST = rs(200000);
    static final BigDecimal CAP_HOUSE_LOSS_SETOFF = rs(200000);
    static final BigDecimal CAP_SAVINGS_INTEREST = rs(10000);
    static final BigDecimal CAP_SENIOR_INTEREST = rs(50000);
    static final BigDecimal CAP_RENT_NO_HRA = rs(60000);   // Section 134 (80GG): ₹5,000 a month

    /** A band of income and the rate charged on the part of the income inside it. */
    record Band(BigDecimal upTo, BigDecimal rate) {
    }

    /** null {@code upTo} means "and everything above". */
    private static final List<Band> NEW_REGIME_BANDS = List.of(
            new Band(rs(400000), BigDecimal.ZERO),
            new Band(rs(800000), new BigDecimal("0.05")),
            new Band(rs(1200000), new BigDecimal("0.10")),
            new Band(rs(1600000), new BigDecimal("0.15")),
            new Band(rs(2000000), new BigDecimal("0.20")),
            new Band(rs(2400000), new BigDecimal("0.25")),
            new Band(null, new BigDecimal("0.30")));

    private static final List<Band> OLD_REGIME_BANDS = List.of(
            new Band(rs(250000), BigDecimal.ZERO),
            new Band(rs(500000), new BigDecimal("0.05")),
            new Band(rs(1000000), new BigDecimal("0.20")),
            new Band(null, new BigDecimal("0.30")));

    /** 60 to 79: the first ₹3,00,000 is free in the old regime. */
    private static final List<Band> OLD_REGIME_SENIOR_BANDS = List.of(
            new Band(rs(300000), BigDecimal.ZERO),
            new Band(rs(500000), new BigDecimal("0.05")),
            new Band(rs(1000000), new BigDecimal("0.20")),
            new Band(null, new BigDecimal("0.30")));

    /** 80 and above: the first ₹5,00,000. */
    private static final List<Band> OLD_REGIME_SUPER_SENIOR_BANDS = List.of(
            new Band(rs(500000), BigDecimal.ZERO),
            new Band(rs(1000000), new BigDecimal("0.20")),
            new Band(null, new BigDecimal("0.30")));

    /** Surcharge thresholds, lowest first. The new regime stops at 25%; the old goes to 37%. */
    private record SurchargeBand(BigDecimal over, BigDecimal oldRate, BigDecimal newRate) {
    }

    private static final List<SurchargeBand> SURCHARGE = List.of(
            new SurchargeBand(rs(5000000), new BigDecimal("0.10"), new BigDecimal("0.10")),
            new SurchargeBand(rs(10000000), new BigDecimal("0.15"), new BigDecimal("0.15")),
            new SurchargeBand(rs(20000000), new BigDecimal("0.25"), new BigDecimal("0.25")),
            new SurchargeBand(rs(50000000), new BigDecimal("0.37"), new BigDecimal("0.25")));

    // ---- inputs ---------------------------------------------------------------------------------

    /** A house the employee owns. Interest is for the year; rent and municipal tax only if let out. */
    public record HouseProperty(boolean letOut, BigDecimal annualRent, BigDecimal municipalTax,
                                BigDecimal interest) {
    }

    /**
     * Everything the tax depends on.
     *
     * @param salary          the year's gross salary from every employer, before anything comes off
     * @param basic           the year's basic (plus DA) — the base of the NPS ceilings
     * @param employeePf      the employee's own statutory PF, which counts towards Section 123
     * @param professionalTax professional tax deducted by payroll (and any previous employer)
     * @param hraExemption    the HRA exemption already worked out month by month ({@link HraCalculator})
     * @param hraReceived     the HRA in the salary for the year; zero means the employee gets none
     * @param ltaReceived     the LTA in the salary for the year — the most that can be exempt
     * @param rentPaid        rent for the year, used for Section 134 when there is no HRA at all
     * @param parentsSenior   whether either parent is 60 or older (Section 126)
     * @param declared        what the employee declared, uncapped — every ceiling is applied here
     */
    public record Input(BigDecimal salary, TaxRegime regime, AgeBand age, BigDecimal basic,
                        BigDecimal employeePf, BigDecimal professionalTax,
                        BigDecimal hraExemption, BigDecimal hraReceived, BigDecimal ltaReceived,
                        BigDecimal rentPaid, boolean parentsSenior,
                        List<HouseProperty> properties, Map<TaxDeduction, BigDecimal> declared) {

        public Input {
            regime = regime == null ? TaxRegime.DEFAULT : regime;
            age = age == null ? AgeBand.BELOW_60 : age;
            salary = nn(salary);
            basic = nn(basic);
            employeePf = nn(employeePf);
            professionalTax = nn(professionalTax);
            hraExemption = nn(hraExemption);
            hraReceived = nn(hraReceived);
            ltaReceived = nn(ltaReceived);
            rentPaid = nn(rentPaid);
            properties = properties == null ? List.of() : List.copyOf(properties);
            declared = declared == null ? Map.of() : Map.copyOf(declared);
        }

        /** A salary and a regime, nothing declared. */
        public static Input of(BigDecimal salary, TaxRegime regime) {
            return new Input(salary, regime, AgeBand.BELOW_60, null, null, null, null, null, null, null,
                    false, List.of(), Map.of());
        }

        /** A salary, a regime and declarations — for the simple cases. */
        public static Input of(BigDecimal salary, TaxRegime regime, Map<TaxDeduction, BigDecimal> declared) {
            return new Input(salary, regime, AgeBand.BELOW_60, null, null, null, null, null, null, null,
                    false, List.of(), declared);
        }

        public Input withRegime(TaxRegime other) {
            return new Input(salary, other, age, basic, employeePf, professionalTax, hraExemption,
                    hraReceived, ltaReceived, rentPaid, parentsSenior, properties, declared);
        }
    }

    // ---- outputs --------------------------------------------------------------------------------

    /** One slab's contribution, kept so a screen can show the working rather than just the total. */
    public record BandTax(BigDecimal from, BigDecimal to, BigDecimal rate, BigDecimal taxable,
                          BigDecimal tax) {
    }

    /**
     * One line of the computation as claimed and as allowed. {@code deduction} is null for the lines
     * Orbit works out itself — PF from payroll, the HRA from the rent.
     */
    public record AllowedDeduction(String key, String section, String label, BigDecimal declared,
                                   BigDecimal allowed, TaxDeduction deduction,
                                   /** The ceiling this line was measured against, where it shares one. */
                                   BigDecimal limit,
                                   /** How much of {@code limit} the lines before it had already used. */
                                   BigDecimal usedBefore,
                                   /** Interest counted under Section 22 instead, because it is worth more there. */
                                   BigDecimal movedToHouse) {
        public AllowedDeduction(String key, String section, String label, BigDecimal declared,
                                BigDecimal allowed, TaxDeduction deduction) {
            this(key, section, label, declared, allowed, deduction, null, null, null);
        }

        AllowedDeduction withLimit(BigDecimal limit, BigDecimal usedBefore) {
            return new AllowedDeduction(key, section, label, declared, allowed, deduction, round(limit), round(usedBefore), movedToHouse);
        }
    }

    /** A shared ceiling and how full it is, for the progress bars on the form. */
    public record GroupTotal(TaxDeduction.Group group, BigDecimal claimed, BigDecimal cap, BigDecimal allowed) {
    }

    /**
     * The whole calculation, step by step. Every intermediate is kept, because the screen this feeds
     * has to answer "why am I paying this" — and an employee who cannot see the working assumes the
     * payroll is wrong.
     */
    public record Result(
            BigDecimal grossSalary,
            TaxRegime regime,
            AgeBand age,
            List<AllowedDeduction> exemptions,
            BigDecimal standardDeduction,
            BigDecimal professionalTax,
            BigDecimal salaryIncome,
            BigDecimal houseProperty,
            BigDecimal otherIncome,
            BigDecimal grossTotalIncome,
            List<AllowedDeduction> deductions,
            List<GroupTotal> groups,
            BigDecimal totalDeductions,
            BigDecimal taxableIncome,
            List<BandTax> bands,
            BigDecimal taxOnIncome,
            BigDecimal rebate,
            BigDecimal taxAfterRebate,
            BigDecimal surcharge,
            BigDecimal cess,
            BigDecimal totalTax,
            BigDecimal monthlyTds,
            /**
             * Home-loan interest declared under Section 130 or 131 that was counted as self-occupied
             * interest under Section 22 instead — where it is deductible up to ₹2,00,000 rather than
             * ₹50,000 / ₹1,50,000 — before the excess went back to 130 / 131. Zero when nothing moved.
             */
            BigDecimal interestMovedToHouse) {
    }

    // ---- the calculation ------------------------------------------------------------------------

    public static Result compute(Input in) {
        TaxRegime regime = in.regime();
        boolean old = regime == TaxRegime.OLD;
        Map<TaxDeduction, BigDecimal> declared = new EnumMap<>(TaxDeduction.class);
        in.declared().forEach((k, v) -> declared.put(k, nn(v)));
        BigDecimal gross = in.salary();

        // 1. Salary: exemptions, then the standard deduction, then professional tax.
        List<AllowedDeduction> exemptions = new ArrayList<>();
        BigDecimal exempt = BigDecimal.ZERO;
        if (old) {
            BigDecimal hra = in.hraExemption().min(in.hraReceived());
            BigDecimal legacy = declared.getOrDefault(TaxDeduction.HRA_EXEMPTION, BigDecimal.ZERO);
            if (hra.signum() > 0) {
                exemptions.add(auto("HRA", "Sch. III (10(13A))", "House rent allowance — worked out from your rent",
                        in.hraReceived(), hra));
            } else if (legacy.signum() > 0 && in.rentPaid().signum() == 0) {
                hra = legacy.min(in.hraReceived());
                exemptions.add(line(TaxDeduction.HRA_EXEMPTION, legacy, hra));
            }
            BigDecimal lta = declared.getOrDefault(TaxDeduction.LTA, BigDecimal.ZERO);
            BigDecimal ltaAllowed = lta.min(in.ltaReceived());
            if (lta.signum() > 0) {
                exemptions.add(line(TaxDeduction.LTA, lta, ltaAllowed));
            }
            exempt = hra.add(ltaAllowed).min(gross);
        }
        BigDecimal afterExempt = gross.subtract(exempt);
        BigDecimal standard = (old ? STANDARD_DEDUCTION_OLD : STANDARD_DEDUCTION_NEW).min(afterExempt);
        BigDecimal pt = BigDecimal.ZERO;
        if (old) {
            pt = in.professionalTax().add(declared.getOrDefault(TaxDeduction.PROFESSIONAL_TAX_OTHER, BigDecimal.ZERO))
                    .min(CAP_PROFESSIONAL_TAX).min(afterExempt.subtract(standard));
        }
        BigDecimal salaryIncome = afterExempt.subtract(standard).subtract(pt).max(BigDecimal.ZERO);

        // 2. House property.
        BigDecimal selfOccupied = BigDecimal.ZERO;
        BigDecimal letOut = BigDecimal.ZERO;
        for (HouseProperty p : in.properties()) {
            if (p.letOut()) {
                BigDecimal nav = nn(p.annualRent()).subtract(nn(p.municipalTax())).max(BigDecimal.ZERO);
                BigDecimal thirty = nav.multiply(new BigDecimal("0.30"));
                letOut = letOut.add(nav.subtract(thirty).subtract(nn(p.interest())));
            } else {
                selfOccupied = selfOccupied.add(nn(p.interest()));
            }
        }

        // 2a. Interest declared under Section 130 / 131 (80EE / 80EEA) is the same home-loan interest
        // Section 22 (24(b)) allows up to ₹2,00,000 on a home you live in — and 131 allows only
        // ₹1,50,000 (130, ₹50,000). People routinely claim it all under 131 and lose the difference.
        // So, old regime only, it fills Section 22's headroom first and only the excess stays under
        // 130 / 131: never worse, often better. Bounded by both the ₹2,00,000 self-occupied ceiling and
        // the ₹2,00,000 limit on setting off a house loss, so not a rupee is moved that would be wasted.
        BigDecimal moved = BigDecimal.ZERO;
        if (old) {
            TaxDeduction from = nn(declared.get(TaxDeduction.FIRST_HOME_LOAN)).signum() > 0
                    ? TaxDeduction.FIRST_HOME_LOAN : TaxDeduction.AFFORDABLE_HOME_LOAN;
            BigDecimal claim = nn(declared.get(from));
            BigDecimal selfRoom = CAP_SELF_OCCUPIED_INTEREST.subtract(selfOccupied).max(BigDecimal.ZERO);
            BigDecimal setOffRoom = letOut.subtract(selfOccupied.min(CAP_SELF_OCCUPIED_INTEREST))
                    .add(CAP_HOUSE_LOSS_SETOFF).max(BigDecimal.ZERO);
            moved = claim.min(selfRoom).min(setOffRoom);
            if (moved.signum() > 0) {
                declared.put(from, claim.subtract(moved));
                selfOccupied = selfOccupied.add(moved);
            }
        }
        BigDecimal house = old ? letOut.subtract(selfOccupied.min(CAP_SELF_OCCUPIED_INTEREST)) : letOut;
        if (house.signum() < 0) {
            house = old ? house.max(CAP_HOUSE_LOSS_SETOFF.negate()) : BigDecimal.ZERO;
        }

        // 3. Other sources.
        BigDecimal savingsInterest = declared.getOrDefault(TaxDeduction.SAVINGS_INTEREST, BigDecimal.ZERO);
        BigDecimal depositInterest = declared.getOrDefault(TaxDeduction.DEPOSIT_INTEREST, BigDecimal.ZERO);
        BigDecimal otherIncome = savingsInterest.add(depositInterest)
                .add(declared.getOrDefault(TaxDeduction.OTHER_INCOME, BigDecimal.ZERO));

        BigDecimal gti = salaryIncome.add(house).add(otherIncome).max(BigDecimal.ZERO);

        // 4. Chapter VIII deductions.
        Deductions d = deductions(in, declared, savingsInterest, depositInterest, gti);
        if (moved.signum() > 0) d = d.showingMoved(in.declared(), moved);
        BigDecimal deductionTotal = d.total().min(gti);

        // 5. Total income, rounded to the nearest ten rupees.
        BigDecimal taxable = roundToTen(gti.subtract(deductionTotal).max(BigDecimal.ZERO));

        // 6-9. Tax.
        List<Band> bands = bandsFor(regime, in.age());
        List<BandTax> working = slabTax(taxable, bands);
        BigDecimal taxOnIncome = sum(working);
        BigDecimal rebate = rebate(taxable, taxOnIncome, regime);
        BigDecimal afterRebate = taxOnIncome.subtract(rebate).max(BigDecimal.ZERO);
        BigDecimal surcharge = surcharge(taxable, afterRebate, regime, bands);
        BigDecimal cess = afterRebate.add(surcharge).multiply(CESS_RATE);

        // 10. The tax, to the rupee (see the class comment: TRACES does not round it to ten).
        BigDecimal total = round(afterRebate.add(surcharge).add(cess));
        BigDecimal monthly = total.divide(BigDecimal.valueOf(12), 0, RoundingMode.HALF_UP);

        return new Result(gross, regime, in.age(), List.copyOf(exemptions), round(standard), round(pt),
                round(salaryIncome), round(house), round(otherIncome), round(gti),
                d.lines(), d.groups(), round(deductionTotal), taxable, working,
                round(taxOnIncome), round(rebate), round(afterRebate),
                round(surcharge), round(cess), total, monthly, round(moved));
    }

    // ---- Chapter VIII ---------------------------------------------------------------------------

    private record Deductions(List<AllowedDeduction> lines, List<GroupTotal> groups, BigDecimal total) {

        /**
         * The 130 / 131 line as the employee declared it, with what moved to Section 22 noted on it — a
         * line that silently shrank would look like a mistake. If all of it moved, the line is added
         * back with nothing allowed so it still shows.
         */
        Deductions showingMoved(Map<TaxDeduction, BigDecimal> original, BigDecimal moved) {
            TaxDeduction from = nn(original.get(TaxDeduction.FIRST_HOME_LOAN)).signum() > 0
                    ? TaxDeduction.FIRST_HOME_LOAN : TaxDeduction.AFFORDABLE_HOME_LOAN;
            List<AllowedDeduction> out = new ArrayList<>();
            boolean found = false;
            for (AllowedDeduction l : lines) {
                if (l.deduction() == from) {
                    found = true;
                    out.add(new AllowedDeduction(l.key(), l.section(), l.label(), round(original.get(from)), l.allowed(),
                            from, l.limit(), l.usedBefore(), round(moved)));
                } else {
                    out.add(l);
                }
            }
            if (!found) {
                out.add(new AllowedDeduction(from.name(), from.sectionLabel(), from.label(), round(original.get(from)),
                        BigDecimal.ZERO, from, null, null, round(moved)));
            }
            return new Deductions(List.copyOf(out), groups, total);
        }
    }

    /**
     * Every deduction, each against its ceiling. Lines that share a ceiling are filled in the order
     * they are declared in {@link TaxDeduction}, so "declared vs allowed" per line always adds up to
     * the group's total.
     */
    private static Deductions deductions(Input in, Map<TaxDeduction, BigDecimal> declared,
                                         BigDecimal savingsInterest, BigDecimal depositInterest,
                                         BigDecimal gti) {
        boolean old = in.regime() == TaxRegime.OLD;
        boolean senior = in.age().isSenior();
        List<AllowedDeduction> lines = new ArrayList<>();
        List<GroupTotal> groups = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        // Employer's NPS — the one that survives into the new regime, at 14% of basic there, 10% here.
        BigDecimal employerNps = declared.getOrDefault(TaxDeduction.EMPLOYER_NPS, BigDecimal.ZERO);
        if (employerNps.signum() > 0) {
            BigDecimal cap = in.basic().multiply(old ? new BigDecimal("0.10") : new BigDecimal("0.14"));
            BigDecimal ok = employerNps.min(cap).setScale(0, RoundingMode.DOWN);
            lines.add(line(TaxDeduction.EMPLOYER_NPS, employerNps, ok));
            groups.add(new GroupTotal(TaxDeduction.Group.EMPLOYER_NPS, employerNps, cap.setScale(0, RoundingMode.DOWN), ok));
            total = total.add(ok);
        }
        BigDecimal agniveer = declared.getOrDefault(TaxDeduction.AGNIVEER, BigDecimal.ZERO);
        if (agniveer.signum() > 0) {
            lines.add(line(TaxDeduction.AGNIVEER, agniveer, agniveer));
            total = total.add(agniveer);
        }
        if (!old) {
            // Everything else is outside the new regime (Section 202). Listed as nil, so somebody who
            // declared under the old regime and switched can see why their claims stopped counting.
            for (Map.Entry<TaxDeduction, BigDecimal> e : declared.entrySet()) {
                TaxDeduction k = e.getKey();
                if (e.getValue().signum() > 0 && !k.allowedIn(TaxRegime.NEW) && k.group() != TaxDeduction.Group.LTA
                        && k.group() != TaxDeduction.Group.PROFESSIONAL_TAX && k.group() != TaxDeduction.Group.LEGACY_HRA) {
                    lines.add(line(k, e.getValue(), BigDecimal.ZERO));
                }
            }
            return new Deductions(List.copyOf(lines), List.copyOf(groups), total);
        }

        // Section 123: PF from payroll first, then each declared line, all inside ₹1,50,000.
        // Own NPS (124(1)) has its own ceiling of 10% of basic before it joins the group.
        BigDecimal room = CAP_123;
        BigDecimal claimed123 = BigDecimal.ZERO;
        if (in.employeePf().signum() > 0) {
            BigDecimal ok = in.employeePf().min(room);
            lines.add(auto("EPF", "Sec 123 (80C)", "Provident fund — deducted by payroll", in.employeePf(), ok)
                    .withLimit(CAP_123, BigDecimal.ZERO));
            room = room.subtract(ok);
            claimed123 = claimed123.add(in.employeePf());
        }
        for (TaxDeduction k : TaxDeduction.values()) {
            if (k.group() != TaxDeduction.Group.SEC_123) continue;
            BigDecimal amount = declared.getOrDefault(k, BigDecimal.ZERO);
            if (amount.signum() <= 0) continue;
            BigDecimal eligible = k == TaxDeduction.NPS_EMPLOYEE
                    ? amount.min(in.basic().multiply(new BigDecimal("0.10")).setScale(0, RoundingMode.DOWN))
                    : amount;
            BigDecimal ok = eligible.min(room);
            BigDecimal before = CAP_123.subtract(room);
            room = room.subtract(ok);
            claimed123 = claimed123.add(amount);
            lines.add(line(k, amount, ok).withLimit(CAP_123, before));
        }
        BigDecimal allowed123 = CAP_123.subtract(room);
        if (claimed123.signum() > 0) {
            groups.add(new GroupTotal(TaxDeduction.Group.SEC_123, claimed123, CAP_123, allowed123));
        }
        total = total.add(allowed123);

        total = total.add(capped(lines, groups, declared, TaxDeduction.Group.NPS_EXTRA, CAP_NPS_EXTRA));

        // Section 126: the preventive check-up is ₹5,000 across the whole family, inside each ceiling.
        BigDecimal selfCheck = declared.getOrDefault(TaxDeduction.HEALTH_SELF_CHECKUP, BigDecimal.ZERO).min(CAP_CHECKUP);
        BigDecimal parentCheck = declared.getOrDefault(TaxDeduction.HEALTH_PARENTS_CHECKUP, BigDecimal.ZERO)
                .min(CAP_CHECKUP.subtract(selfCheck));
        total = total.add(health(lines, groups, declared, TaxDeduction.Group.HEALTH_SELF,
                TaxDeduction.HEALTH_SELF_PREMIUM, TaxDeduction.HEALTH_SELF_CHECKUP, selfCheck,
                TaxDeduction.HEALTH_SELF_MEDICAL, senior, senior ? CAP_HEALTH_SENIOR : CAP_HEALTH));
        total = total.add(health(lines, groups, declared, TaxDeduction.Group.HEALTH_PARENTS,
                TaxDeduction.HEALTH_PARENTS_PREMIUM, TaxDeduction.HEALTH_PARENTS_CHECKUP, parentCheck,
                TaxDeduction.HEALTH_PARENTS_MEDICAL, in.parentsSenior(),
                in.parentsSenior() ? CAP_HEALTH_SENIOR : CAP_HEALTH));

        // Fixed amounts: the deduction is the figure the Act names, whatever was spent.
        total = total.add(fixed(lines, declared, TaxDeduction.DISABLED_DEPENDENT_SEVERE, rs(125000),
                TaxDeduction.DISABLED_DEPENDENT, rs(75000)));
        total = total.add(fixed(lines, declared, TaxDeduction.SELF_DISABILITY_SEVERE, rs(125000),
                TaxDeduction.SELF_DISABILITY, rs(75000)));

        // Section 128: ₹40,000, or ₹1,00,000 for a patient of 60 or more — one ceiling a year.
        BigDecimal disease = declared.getOrDefault(TaxDeduction.SPECIFIED_DISEASE, BigDecimal.ZERO);
        BigDecimal diseaseSenior = declared.getOrDefault(TaxDeduction.SPECIFIED_DISEASE_SENIOR, BigDecimal.ZERO);
        if (disease.signum() > 0 || diseaseSenior.signum() > 0) {
            BigDecimal okSenior = diseaseSenior.min(rs(100000));
            BigDecimal okNormal = disease.min(rs(40000)).min(rs(100000).subtract(okSenior));
            if (diseaseSenior.signum() > 0) lines.add(line(TaxDeduction.SPECIFIED_DISEASE_SENIOR, diseaseSenior, okSenior));
            if (disease.signum() > 0) lines.add(line(TaxDeduction.SPECIFIED_DISEASE, disease, okNormal));
            total = total.add(okSenior).add(okNormal);
        }

        total = total.add(uncapped(lines, declared, TaxDeduction.EDUCATION_LOAN));
        BigDecimal firstHome = declared.getOrDefault(TaxDeduction.FIRST_HOME_LOAN, BigDecimal.ZERO);
        total = total.add(capped(lines, groups, declared, TaxDeduction.Group.FIRST_HOME_LOAN, rs(50000)));
        if (firstHome.signum() > 0) {
            // Section 131 is not available to someone claiming Section 130.
            BigDecimal affordable = declared.getOrDefault(TaxDeduction.AFFORDABLE_HOME_LOAN, BigDecimal.ZERO);
            if (affordable.signum() > 0) lines.add(line(TaxDeduction.AFFORDABLE_HOME_LOAN, affordable, BigDecimal.ZERO));
        } else {
            total = total.add(capped(lines, groups, declared, TaxDeduction.Group.AFFORDABLE_HOME_LOAN, rs(150000)));
        }
        total = total.add(capped(lines, groups, declared, TaxDeduction.Group.EV_LOAN, rs(150000)));
        total = total.add(uncapped(lines, declared, TaxDeduction.POLITICAL_DONATION));

        // Section 153: savings interest up to ₹10,000; a senior's savings and deposit interest to ₹50,000.
        BigDecimal interestDeduction = senior
                ? savingsInterest.add(depositInterest).min(CAP_SENIOR_INTEREST)
                : savingsInterest.min(CAP_SAVINGS_INTEREST);
        if (interestDeduction.signum() > 0) {
            lines.add(auto("INTEREST", senior ? "Sec 153 (80TTB)" : "Sec 153 (80TTA)",
                    senior ? "Interest on savings and deposits" : "Interest on savings accounts",
                    senior ? savingsInterest.add(depositInterest) : savingsInterest, interestDeduction));
            total = total.add(interestDeduction);
        }

        // Section 134 (80GG): rent with no HRA at all. On income before 80G and itself.
        if (in.hraReceived().signum() == 0 && in.rentPaid().signum() > 0) {
            BigDecimal adjusted = gti.subtract(total).max(BigDecimal.ZERO);
            BigDecimal ok = CAP_RENT_NO_HRA
                    .min(adjusted.multiply(new BigDecimal("0.25")))
                    .min(in.rentPaid().subtract(adjusted.multiply(new BigDecimal("0.10"))))
                    .max(BigDecimal.ZERO).setScale(0, RoundingMode.DOWN);
            if (ok.signum() > 0) {
                lines.add(auto("RENT_NO_HRA", "Sec 134 (80GG)", "Rent paid without HRA", in.rentPaid(), ok));
                total = total.add(ok);
            }
        }

        // Section 133 (80G), last: its qualifying limit is 10% of income after every other deduction.
        BigDecimal limit = gti.subtract(total).max(BigDecimal.ZERO).multiply(new BigDecimal("0.10"));
        BigDecimal d100 = declared.getOrDefault(TaxDeduction.DONATION_100, BigDecimal.ZERO);
        BigDecimal d50 = declared.getOrDefault(TaxDeduction.DONATION_50, BigDecimal.ZERO);
        BigDecimal l100 = declared.getOrDefault(TaxDeduction.DONATION_100_LIMITED, BigDecimal.ZERO);
        BigDecimal l50 = declared.getOrDefault(TaxDeduction.DONATION_50_LIMITED, BigDecimal.ZERO);
        // Of the limited donations, anything beyond the qualifying limit is ignored; the 100% ones are
        // counted first, which is the reading every return-preparation utility takes.
        BigDecimal l100In = l100.min(limit);
        BigDecimal l50In = l50.min(limit.subtract(l100In).max(BigDecimal.ZERO));
        BigDecimal[] donations = {d100, d50.multiply(new BigDecimal("0.5")), l100In,
                l50In.multiply(new BigDecimal("0.5"))};
        TaxDeduction[] keys = {TaxDeduction.DONATION_100, TaxDeduction.DONATION_50,
                TaxDeduction.DONATION_100_LIMITED, TaxDeduction.DONATION_50_LIMITED};
        BigDecimal[] claims = {d100, d50, l100, l50};
        for (int i = 0; i < keys.length; i++) {
            if (claims[i].signum() > 0) {
                BigDecimal ok = donations[i].setScale(0, RoundingMode.DOWN);
                lines.add(line(keys[i], claims[i], ok));
                total = total.add(ok);
            }
        }

        return new Deductions(List.copyOf(lines), List.copyOf(groups), total);
    }

    /** Every line in a group, filled in order against one ceiling. */
    private static BigDecimal capped(List<AllowedDeduction> lines, List<GroupTotal> groups,
                                     Map<TaxDeduction, BigDecimal> declared, TaxDeduction.Group group,
                                     BigDecimal cap) {
        BigDecimal room = cap;
        BigDecimal claimed = BigDecimal.ZERO;
        for (TaxDeduction k : TaxDeduction.values()) {
            if (k.group() != group) continue;
            BigDecimal amount = declared.getOrDefault(k, BigDecimal.ZERO);
            if (amount.signum() <= 0) continue;
            BigDecimal ok = amount.min(room);
            BigDecimal before = cap.subtract(room);
            room = room.subtract(ok);
            claimed = claimed.add(amount);
            lines.add(line(k, amount, ok).withLimit(cap, before));
        }
        BigDecimal allowed = cap.subtract(room);
        if (claimed.signum() > 0) {
            groups.add(new GroupTotal(group, claimed, cap, allowed));
        }
        return allowed;
    }

    /** Section 126 for one side of the family: premium, check-up (already capped) and medical bills. */
    private static BigDecimal health(List<AllowedDeduction> lines, List<GroupTotal> groups,
                                     Map<TaxDeduction, BigDecimal> declared, TaxDeduction.Group group,
                                     TaxDeduction premiumKey, TaxDeduction checkupKey, BigDecimal checkupEligible,
                                     TaxDeduction medicalKey, boolean senior, BigDecimal cap) {
        BigDecimal premium = declared.getOrDefault(premiumKey, BigDecimal.ZERO);
        BigDecimal checkup = declared.getOrDefault(checkupKey, BigDecimal.ZERO);
        BigDecimal medical = declared.getOrDefault(medicalKey, BigDecimal.ZERO);
        if (premium.signum() <= 0 && checkup.signum() <= 0 && medical.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal room = cap;
        BigDecimal okPremium = premium.min(room);
        room = room.subtract(okPremium);
        BigDecimal okCheckup = checkupEligible.min(room);
        room = room.subtract(okCheckup);
        // Medical bills count only for a senior with no insurance at all.
        BigDecimal okMedical = senior && premium.signum() == 0 ? medical.min(room) : BigDecimal.ZERO;
        if (premium.signum() > 0) lines.add(line(premiumKey, premium, okPremium));
        if (checkup.signum() > 0) lines.add(line(checkupKey, checkup, okCheckup));
        if (medical.signum() > 0) lines.add(line(medicalKey, medical, okMedical));
        BigDecimal allowed = okPremium.add(okCheckup).add(okMedical);
        groups.add(new GroupTotal(group, premium.add(checkup).add(medical), cap, allowed));
        return allowed;
    }

    /** A fixed-amount deduction with a severe variant: the severe one wins if both are claimed. */
    private static BigDecimal fixed(List<AllowedDeduction> lines, Map<TaxDeduction, BigDecimal> declared,
                                    TaxDeduction severe, BigDecimal severeAmount,
                                    TaxDeduction normal, BigDecimal normalAmount) {
        BigDecimal s = declared.getOrDefault(severe, BigDecimal.ZERO);
        BigDecimal n = declared.getOrDefault(normal, BigDecimal.ZERO);
        if (s.signum() > 0) {
            lines.add(line(severe, s, severeAmount));
            if (n.signum() > 0) lines.add(line(normal, n, BigDecimal.ZERO));
            return severeAmount;
        }
        if (n.signum() > 0) {
            lines.add(line(normal, n, normalAmount));
            return normalAmount;
        }
        return BigDecimal.ZERO;
    }

    private static BigDecimal uncapped(List<AllowedDeduction> lines, Map<TaxDeduction, BigDecimal> declared,
                                       TaxDeduction key) {
        BigDecimal amount = declared.getOrDefault(key, BigDecimal.ZERO);
        if (amount.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        lines.add(line(key, amount, amount));
        return amount;
    }

    private static AllowedDeduction line(TaxDeduction k, BigDecimal declared, BigDecimal allowed) {
        return new AllowedDeduction(k.name(), k.sectionLabel(), k.label(), round(declared), round(allowed), k);
    }

    private static AllowedDeduction auto(String key, String section, String label, BigDecimal declared,
                                         BigDecimal allowed) {
        return new AllowedDeduction(key, section, label, round(declared), round(allowed), null);
    }

    // ---- tax ------------------------------------------------------------------------------------

    static List<Band> bandsFor(TaxRegime regime, AgeBand age) {
        if (regime == TaxRegime.NEW) {
            return NEW_REGIME_BANDS;   // the new regime has no age bands
        }
        return switch (age) {
            case SUPER_SENIOR -> OLD_REGIME_SUPER_SENIOR_BANDS;
            case SENIOR -> OLD_REGIME_SENIOR_BANDS;
            default -> OLD_REGIME_BANDS;
        };
    }

    /** Tax band by band: each rate applies only to the slice of income inside its own band. */
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
                out.add(new BandTax(floor, band.upTo(), band.rate(), slice, slice.multiply(band.rate())));
            }
            if (band.upTo() == null) {
                break;
            }
            floor = band.upTo();
        }
        return List.copyOf(out);
    }

    /**
     * Section 156 (87A), with the marginal relief that makes it safe to cross the line in the new
     * regime: the tax is limited to what was earned above ₹12,00,000. The old regime's ₹12,500 rebate
     * has no such relief — it simply stops at ₹5,00,000.
     */
    private static BigDecimal rebate(BigDecimal taxable, BigDecimal taxOnIncome, TaxRegime regime) {
        BigDecimal limit = regime == TaxRegime.NEW ? REBATE_LIMIT_NEW : REBATE_LIMIT_OLD;
        BigDecimal max = regime == TaxRegime.NEW ? REBATE_MAX_NEW : REBATE_MAX_OLD;
        if (taxable.compareTo(limit) <= 0) {
            return taxOnIncome.min(max);
        }
        if (regime != TaxRegime.NEW) {
            return BigDecimal.ZERO;
        }
        BigDecimal excess = taxable.subtract(limit);
        return taxOnIncome.subtract(excess).max(BigDecimal.ZERO);
    }

    /**
     * Surcharge, with marginal relief at every threshold.
     *
     * <p>Tax plus surcharge on income above a threshold may not exceed tax plus surcharge on income
     * <em>exactly at</em> the threshold, plus the income earned beyond it. "At the threshold" includes
     * the lower band's surcharge: at ₹1 crore that is tax plus 10%, not tax alone. Comparing against
     * bare tax — which this used to do — over-relieved everybody just above ₹1, ₹2 and ₹5 crore.
     */
    private static BigDecimal surcharge(BigDecimal taxable, BigDecimal tax, TaxRegime regime, List<Band> bands) {
        SurchargeBand band = null;
        BigDecimal belowRate = BigDecimal.ZERO;
        for (SurchargeBand b : SURCHARGE) {
            if (taxable.compareTo(b.over()) > 0) {
                if (band != null) {
                    belowRate = rate(band, regime);
                }
                band = b;
            }
        }
        if (band == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal raw = tax.multiply(rate(band, regime));
        BigDecimal taxAtThreshold = sum(slabTax(band.over(), bands));
        BigDecimal atThreshold = taxAtThreshold.add(taxAtThreshold.multiply(belowRate));
        BigDecimal ceiling = atThreshold.add(taxable.subtract(band.over()));
        if (tax.add(raw).compareTo(ceiling) > 0) {
            return ceiling.subtract(tax).max(BigDecimal.ZERO);
        }
        return raw;
    }

    private static BigDecimal rate(SurchargeBand b, TaxRegime regime) {
        return regime == TaxRegime.NEW ? b.newRate() : b.oldRate();
    }

    // ---- helpers --------------------------------------------------------------------------------

    private static BigDecimal sum(List<BandTax> bands) {
        return bands.stream().map(BandTax::tax).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Section 516: paise ignored, then to the nearest ten — five and over goes up. */
    static BigDecimal roundToTen(BigDecimal v) {
        BigDecimal rupees = v.setScale(0, RoundingMode.DOWN);
        return rupees.divide(BigDecimal.TEN, 0, RoundingMode.HALF_UP).multiply(BigDecimal.TEN);
    }

    private static BigDecimal nn(BigDecimal v) {
        return v == null || v.signum() < 0 ? BigDecimal.ZERO : v;
    }

    private static BigDecimal round(BigDecimal v) {
        return v.setScale(0, RoundingMode.HALF_UP);
    }

    private static BigDecimal rs(long v) {
        return BigDecimal.valueOf(v);
    }
}
