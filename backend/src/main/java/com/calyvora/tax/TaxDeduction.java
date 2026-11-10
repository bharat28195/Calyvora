package com.calyvora.tax;

/**
 * Everything a salaried person can put on their declaration (Form 124, formerly 12BB), one entry per
 * line the form shows.
 *
 * <p><b>Granular on purpose.</b> Section 123 (the old 80C) is one ₹1,50,000 ceiling over a dozen
 * different things — life insurance, PPF, ELSS, tuition fees, home-loan principal. People do not
 * think "80C", they think "my LIC premium", and HR checks a proof per item. So each is its own line,
 * and {@link Group} says which lines share a ceiling. The arithmetic of every ceiling lives in
 * {@link IncomeTaxCalculator}, not here: a cap enforced only on a form is not a cap.
 *
 * <p>Section numbers are the Income-tax Act, 2025's (in force from 1 April 2026), with the 1961 Act's
 * number alongside, because every employee and most HR teams still know them by the old ones.
 * Figures are those in force for tax year 2026-27; the Budget of 2026 changed none of them.
 *
 * <p>The keys are stored in declarations. Renaming one is a migration, never an edit here.
 */
public enum TaxDeduction {

    // ---- Section 123 (80C, 80CCC, 80CCD(1)) — one ₹1,50,000 ceiling across the lot ----
    LIFE_INSURANCE(Group.SEC_123, "123", "80C", "Life insurance premium", "For yourself, your spouse or children"),
    PPF(Group.SEC_123, "123", "80C", "Public Provident Fund (PPF)", "Deposits made this year"),
    ELSS(Group.SEC_123, "123", "80C", "Tax-saving mutual funds (ELSS)", "Lump sums and SIPs this year"),
    TUITION_FEES(Group.SEC_123, "123", "80C", "Children's tuition fees", "Up to two children; tuition only"),
    HOME_LOAN_PRINCIPAL(Group.SEC_123, "123", "80C", "Home loan principal repaid", "The principal part of your EMIs"),
    STAMP_DUTY(Group.SEC_123, "123", "80C", "Stamp duty and registration of a home", "In the year the house was bought"),
    NSC(Group.SEC_123, "123", "80C", "National Savings Certificate", "Including interest reinvested"),
    TAX_SAVER_FD(Group.SEC_123, "123", "80C", "5-year tax-saving bank deposit", null),
    POST_OFFICE_TD(Group.SEC_123, "123", "80C", "5-year Post Office time deposit", null),
    SCSS(Group.SEC_123, "123", "80C", "Senior Citizens Savings Scheme", null),
    SUKANYA(Group.SEC_123, "123", "80C", "Sukanya Samriddhi account", null),
    ULIP(Group.SEC_123, "123", "80C", "Unit-linked insurance plan (ULIP)", null),
    VPF(Group.SEC_123, "123", "80C", "Voluntary provident fund", "Only what payroll does not already deduct"),
    PENSION_PLAN(Group.SEC_123, "123", "80CCC", "Pension plan of an insurer", null),
    NPS_EMPLOYEE(Group.SEC_123, "124(1)", "80CCD(1)", "Your own NPS contribution", "Up to 10% of basic, inside the ₹1,50,000"),
    OTHER_123(Group.SEC_123, "123", "80C", "Other Section 123 investments", "NABARD bonds and the like"),

    // ---- Section 124 — NPS outside the ₹1,50,000 ----
    NPS_ADDITIONAL(Group.NPS_EXTRA, "124(3)", "80CCD(1B)", "Additional NPS contribution", "Up to ₹50,000 more, on top of Section 123"),
    EMPLOYER_NPS(Group.EMPLOYER_NPS, "124(2)", "80CCD(2)", "Employer's NPS contribution", "Up to 14% of basic in the new regime, 10% in the old"),
    AGNIVEER(Group.AGNIVEER, "125", "80CCH", "Agniveer Corpus Fund", null),

    // ---- Section 126 (80D) — health ----
    HEALTH_SELF_PREMIUM(Group.HEALTH_SELF, "126", "80D", "Health insurance — you, spouse, children", "₹25,000 a year; ₹50,000 if you are 60 or older"),
    HEALTH_SELF_CHECKUP(Group.HEALTH_SELF, "126", "80D", "Preventive health check-up — family", "Up to ₹5,000, inside the limit above"),
    HEALTH_SELF_MEDICAL(Group.HEALTH_SELF, "126", "80D", "Medical bills — you, if 60+ and uninsured", "Only when nobody is insured"),
    HEALTH_PARENTS_PREMIUM(Group.HEALTH_PARENTS, "126", "80D", "Health insurance — parents", "₹25,000 a year; ₹50,000 if a parent is 60 or older"),
    HEALTH_PARENTS_CHECKUP(Group.HEALTH_PARENTS, "126", "80D", "Preventive health check-up — parents", "₹5,000 in total with your family's"),
    HEALTH_PARENTS_MEDICAL(Group.HEALTH_PARENTS, "126", "80D", "Medical bills — senior parents, uninsured", "Only when they are 60+ and not insured"),

    // ---- Disability and illness ----
    DISABLED_DEPENDENT(Group.DISABLED_DEPENDENT, "127", "80DD", "Dependant with a disability", "A fixed ₹75,000 when you spend on their care"),
    DISABLED_DEPENDENT_SEVERE(Group.DISABLED_DEPENDENT, "127", "80DD", "Dependant with a severe disability (80%+)", "A fixed ₹1,25,000"),
    SPECIFIED_DISEASE(Group.SPECIFIED_DISEASE, "128", "80DDB", "Treatment of a specified illness", "Up to ₹40,000"),
    SPECIFIED_DISEASE_SENIOR(Group.SPECIFIED_DISEASE, "128", "80DDB", "Treatment of a specified illness — patient 60+", "Up to ₹1,00,000"),
    SELF_DISABILITY(Group.SELF_DISABILITY, "154", "80U", "You have a disability", "A fixed ₹75,000"),
    SELF_DISABILITY_SEVERE(Group.SELF_DISABILITY, "154", "80U", "You have a severe disability (80%+)", "A fixed ₹1,25,000"),

    // ---- Loans ----
    EDUCATION_LOAN(Group.EDUCATION_LOAN, "129", "80E", "Education loan interest", "No upper limit"),
    FIRST_HOME_LOAN(Group.FIRST_HOME_LOAN, "130", "80EE", "Extra home-loan interest — loan of 2016-17", "Up to ₹50,000; first home, loan sanctioned in 2016-17"),
    AFFORDABLE_HOME_LOAN(Group.AFFORDABLE_HOME_LOAN, "131", "80EEA", "Extra home-loan interest — loan of 2019-22", "Up to ₹1,50,000; not together with Section 130"),
    EV_LOAN(Group.EV_LOAN, "132", "80EEB", "Electric vehicle loan interest", "Up to ₹1,50,000; loan sanctioned 2019-23"),

    // ---- Donations, by cheque or online only ----
    DONATION_100(Group.DONATION, "133", "80G", "Donation — 100%, no limit", "e.g. PM National Relief Fund"),
    DONATION_50(Group.DONATION, "133", "80G", "Donation — 50%, no limit", "e.g. PM Drought Relief Fund"),
    DONATION_100_LIMITED(Group.DONATION, "133", "80G", "Donation — 100%, within the qualifying limit", "e.g. promoting family planning"),
    DONATION_50_LIMITED(Group.DONATION, "133", "80G", "Donation — 50%, within the qualifying limit", "Most registered charities"),
    POLITICAL_DONATION(Group.POLITICAL_DONATION, "137", "80GGC", "Donation to a political party", "Not in cash"),

    // ---- Income from other sources, which the employer must then tax too ----
    SAVINGS_INTEREST(Group.OTHER_INCOME, "153", "80TTA", "Savings account interest", "₹10,000 of it comes off again; ₹50,000 with deposits if you are 60+"),
    DEPOSIT_INTEREST(Group.OTHER_INCOME, "153", "80TTB", "Fixed and recurring deposit interest", "Deductible (up to ₹50,000) only if you are 60+"),
    OTHER_INCOME(Group.OTHER_INCOME, null, null, "Any other income you want taxed here", "Dividends, freelance fees and the like"),

    // ---- Exemptions and salary deductions ----
    LTA(Group.LTA, "Sch. III", "10(5)", "Leave travel — fares actually paid", "Up to the LTA in your salary"),
    PROFESSIONAL_TAX_OTHER(Group.PROFESSIONAL_TAX, "19", "16(iii)", "Professional tax paid outside this payroll", "With payroll's, up to ₹2,500"),

    /**
     * A figure typed in before rent details existed. Honoured only while the declaration has no rent
     * on it, and never above the HRA actually received; the form asks for the rent instead.
     */
    HRA_EXEMPTION(Group.LEGACY_HRA, "Sch. III", "10(13A)", "HRA exemption (entered before rent details)", "Add your rent to replace this");

    /** Which ceiling a line counts against. Lines in the same group share it. */
    public enum Group {
        SEC_123, NPS_EXTRA, EMPLOYER_NPS, AGNIVEER,
        HEALTH_SELF, HEALTH_PARENTS, DISABLED_DEPENDENT, SPECIFIED_DISEASE, SELF_DISABILITY,
        EDUCATION_LOAN, FIRST_HOME_LOAN, AFFORDABLE_HOME_LOAN, EV_LOAN,
        DONATION, POLITICAL_DONATION, OTHER_INCOME, LTA, PROFESSIONAL_TAX, LEGACY_HRA;

        /**
         * Section 202 (the new regime) keeps only the employer's NPS and the Agniveer fund. Other
         * income is income in both, so it is "allowed" in the sense that it always counts.
         */
        public boolean allowedIn(TaxRegime regime) {
            return regime == TaxRegime.OLD || this == EMPLOYER_NPS || this == AGNIVEER || this == OTHER_INCOME;
        }
    }

    private final Group group;
    private final String section;
    private final String oldSection;
    private final String label;
    private final String hint;

    TaxDeduction(Group group, String section, String oldSection, String label, String hint) {
        this.group = group;
        this.section = section;
        this.oldSection = oldSection;
        this.label = label;
        this.hint = hint;
    }

    public Group group() {
        return group;
    }

    /** The Income-tax Act, 2025 section, or null where it is plain income rather than a deduction. */
    public String section() {
        return section;
    }

    /** The 1961 Act's number for the same thing — what most people still recognise. */
    public String oldSection() {
        return oldSection;
    }

    public String label() {
        return label;
    }

    public String hint() {
        return hint;
    }

    /** Income to be taxed rather than an amount that reduces tax. */
    public boolean isIncome() {
        return group == Group.OTHER_INCOME;
    }

    public boolean allowedIn(TaxRegime regime) {
        return group.allowedIn(regime);
    }

    /** "Sec 123 (80C)" — both numbers, the way the form shows them. */
    public String sectionLabel() {
        if (section == null) {
            return "Other income";
        }
        return "Sec " + section + (oldSection == null ? "" : " (" + oldSection + ")");
    }
}
