package com.calyvora.tax;

import java.math.BigDecimal;

/**
 * The deductions a salaried person can declare, with the ceiling the Income-tax Act puts on each.
 *
 * <p><b>The cap lives here rather than in the form.</b> A limit enforced only in the browser is not a
 * limit: the declaration arrives over an API, and a ₹5,00,000 claim under 80C would otherwise reduce
 * somebody's tax by seventy thousand rupees they do not owe — money the employer failed to deduct and
 * the employee owes with interest when the return is assessed. Capping where the tax is computed
 * means every path gets it, including an import and a seeder.
 *
 * <p>{@code allowedInNewRegime} is the other half of the same idea. The new regime disallows almost
 * all of these, so a person who declares ₹1,50,000 of 80C and then switches regime must simply stop
 * getting it — silently, and without anybody having to remember to clear the form.
 *
 * <p>Amounts are annual and in rupees. Figures are those in force for FY 2026-27; the Budget of 2026
 * changed no slab or limit, so they are the same as FY 2025-26.
 */
public enum TaxDeduction {

    /**
     * The big one: provident fund, PPF, ELSS, life insurance premiums, the principal part of a home
     * loan repayment, children's tuition fees, NSC, five-year deposits.
     */
    SECTION_80C("80C", "PF, PPF, ELSS, insurance, tuition, home loan principal",
            new BigDecimal("150000"), false),

    /** NPS, over and above 80C. This is the one deduction people most often miss entirely. */
    SECTION_80CCD_1B("80CCD(1B)", "National Pension System — additional",
            new BigDecimal("50000"), false),

    /**
     * The employer's own NPS contribution — and the exception that matters.
     *
     * <p>Alone among these, it survives into the new regime. A company that contributes to NPS on
     * behalf of its staff gives them a deduction the new regime otherwise has none of, and missing
     * that overstates the tax of every employee on the scheme.
     */
    SECTION_80CCD_2("80CCD(2)", "Employer's NPS contribution", null, true),

    /** Health insurance for self and family. ₹50,000 where the insured is a senior citizen. */
    SECTION_80D_SELF("80D (self)", "Health insurance — self, spouse, children",
            new BigDecimal("25000"), false),

    /** Health insurance for parents, a separate ceiling from the one above. */
    SECTION_80D_PARENTS("80D (parents)", "Health insurance — parents",
            new BigDecimal("50000"), false),

    /** Interest on an education loan. Deliberately uncapped — the Act sets no ceiling. */
    SECTION_80E("80E", "Interest on an education loan", null, false),

    /** Donations. The qualifying amount varies by institution, so what is declared is what counts. */
    SECTION_80G("80G", "Donations to approved funds", null, false),

    /** Interest on savings accounts. ₹50,000 under 80TTB for senior citizens. */
    SECTION_80TTA("80TTA", "Interest on savings accounts", new BigDecimal("10000"), false),

    /**
     * Interest on a home loan for a self-occupied property, under section 24(b).
     *
     * <p>Strictly a loss from house property set off against salary rather than a Chapter VI-A
     * deduction, but it reduces taxable income the same way and belongs on the same form.
     */
    HOME_LOAN_INTEREST("24(b)", "Home loan interest — self-occupied",
            new BigDecimal("200000"), false),

    /**
     * House Rent Allowance exempt under section 10(13A).
     *
     * <p>Not a flat cap: the exemption is the least of the HRA actually received, rent paid minus 10%
     * of salary, and 50% of salary in the metros or 40% elsewhere. That arithmetic needs the salary
     * structure and the city, so the computed figure is declared here rather than derived.
     */
    HRA_EXEMPTION("10(13A)", "House Rent Allowance exemption", null, false),

    /** Leave Travel Allowance, exempt under 10(5) for actual travel within India. */
    LTA_EXEMPTION("10(5)", "Leave Travel Allowance exemption", null, false),

    /** Professional tax paid to the state, deductible under 16(iii). */
    PROFESSIONAL_TAX("16(iii)", "Professional tax", new BigDecimal("2500"), false);

    private final String section;
    private final String label;
    private final BigDecimal cap;
    private final boolean allowedInNewRegime;

    TaxDeduction(String section, String label, BigDecimal cap, boolean allowedInNewRegime) {
        this.section = section;
        this.label = label;
        this.cap = cap;
        this.allowedInNewRegime = allowedInNewRegime;
    }

    public String section() {
        return section;
    }

    public String label() {
        return label;
    }

    /** The statutory ceiling, or null where the Act sets none. */
    public BigDecimal cap() {
        return cap;
    }

    public boolean allowedIn(TaxRegime regime) {
        return regime == TaxRegime.OLD || allowedInNewRegime;
    }

    /** What actually counts, given what was declared: never negative, never above the ceiling. */
    public BigDecimal allowable(BigDecimal declared, TaxRegime regime) {
        if (declared == null || declared.signum() <= 0 || !allowedIn(regime)) {
            return BigDecimal.ZERO;
        }
        return cap == null ? declared : declared.min(cap);
    }
}
