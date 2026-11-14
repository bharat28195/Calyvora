package com.calyvora.feature;

import java.util.Arrays;
import java.util.List;
import org.springframework.util.AntPathMatcher;

/**
 * The parts of Orbit that can be sold, or withheld, one customer at a time.
 *
 * <p>Two kinds live here and the difference matters more than it looks:
 *
 * <ul>
 *   <li><b>Modules</b> — payroll, recruitment, work, and so on. These are how a small company buys a
 *       smaller product. Every one of them is {@code defaultOn}, because they all existed before this
 *       enum did and a customer must never lose a screen because we added a way to switch it off.</li>
 *   <li><b>Capabilities we trust gradually</b> — statutory payroll today. {@code defaultOn = false},
 *       because it can print a wrong figure on somebody's payslip and have them act on it.</li>
 * </ul>
 *
 * <p>What is <em>not</em> here is deliberate: people, attendance, leave, documents and the dashboard.
 * They are what an HR product is. Selling a plan without them would not be a smaller product, it
 * would be a broken one, and a switch that must never be turned off is a liability rather than a
 * feature.
 *
 * @param label       what the owner console and the customer's own screens call it
 * @param description one line, shown beside the toggle, so a plan can be assembled without guessing
 * @param defaultOn   the answer when nothing has been configured — see the note above
 * @param paths       Ant patterns for the API surface this feature guards; none when it guards no screen
 */
public enum Feature {

    /**
     * Everything that shows or changes pay, not only the payroll screens. Salaries, payslips, the full
     * and final settlement and income-tax declarations live under {@code /people} and {@code /tax}, so
     * guarding only {@code /payroll} left a customer without payroll able to open them (2026-11-14).
     * Private details ({@code /finance}: PAN, bank, UAN) are deliberately not here: they are HR records.
     */
    PAYROLL("Payroll", "Salaries, payslips, payroll runs, the bank file, tax declarations and settlements", true,
            "/api/v1/payroll/**",
            "/api/v1/people/employees/*/compensation/**",
            "/api/v1/people/employees/*/payslip/**",
            "/api/v1/people/employees/*/settlement/**",
            "/api/v1/people/me/compensation/**",
            "/api/v1/people/me/payslip/**",
            "/api/v1/tax/**"),
    STATUTORY_PAYROLL("Statutory payroll (PF)",
            "Provident Fund computed on payslips. Off until the numbers have been checked.", false),
    /**
     * Deliberately separate from PF rather than folded into it.
     *
     * <p>Both are statutory, but a company can run one through us and not the other — plenty hand
     * TDS to their auditor — and switching PF on must not start withholding income tax from
     * everybody's pay as a side effect. Off by default for the same reason PF is: the first time a
     * deduction appears on a payslip, somebody should have decided that it should.
     *
     * <p><b>No path, deliberately.</b> This gates the deduction, not the screens. Giving it
     * {@code /api/v1/tax/} put the whole module behind a flag that is off by default, so nobody
     * could so much as open their declaration — and an employee's own tax declaration is their
     * business whether or not their employer withholds through us. The full suite caught it; the
     * tax tests passed alone because they were written before the flag existed.
     */
    INCOME_TAX("Income tax (TDS)",
            "Income tax withheld on payslips, from each employee's declaration. Off until checked.",
            false),
    RECRUITMENT("Recruitment", "Job openings, candidate pipeline, offers and hiring", true, "/api/v1/recruit/**"),
    PERFORMANCE("Performance", "Review cycles, self and manager reviews, hikes", true, "/api/v1/performance/**"),
    /**
     * Company documents: handbooks and policies HR publishes for everyone to read. The enum name
     * stays KNOWLEDGE because plans and company overrides store it by name.
     */
    KNOWLEDGE("Company documents", "Policies and handbooks everyone can read", true, "/api/v1/knowledge/**"),
    HELPDESK("Helpdesk", "Internal tickets and replies", true, "/api/v1/helpdesk/**"),
    EXPENSES("Expenses", "Claims, approval and reimbursement", true, "/api/v1/expenses", "/api/v1/expenses/**"),
    SHIFTS("Shifts", "Shift patterns and the roster", true, "/api/v1/shifts", "/api/v1/shifts/**"),
    FEED("Company feed", "Announcements, posts and comments", true, "/api/v1/feed", "/api/v1/feed/**"),
    ASSISTANT("AI assistant", "Answers questions from the company's own data", true, "/api/v1/assistant/**"),
    ANALYTICS("Insights", "Headcount, attrition and cost analytics", true, "/api/v1/analytics/**");

    private final String label;
    private final String description;
    private final boolean defaultOn;
    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private final String[] paths;

    Feature(String label, String description, boolean defaultOn, String... paths) {
        this.label = label;
        this.description = description;
        this.defaultOn = defaultOn;
        this.paths = paths;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public boolean defaultOn() {
        return defaultOn;
    }

    public List<String> paths() {
        return List.of(paths);
    }

    /**
     * The feature guarding {@code path}, if any. No two features claim the same address, so the first
     * match is the only match; {@code FeatureTest} keeps it that way.
     */
    public static Feature guarding(String path) {
        for (Feature f : values()) {
            for (String p : f.paths) {
                if (MATCHER.match(p, path)) return f;
            }
        }
        return null;
    }

    public static Feature parse(String raw) {
        return Feature.valueOf(raw.trim().toUpperCase());
    }

    /** The ones a plan is assembled from — every feature, in declaration order. */
    public static List<Feature> all() {
        return Arrays.asList(values());
    }
}
