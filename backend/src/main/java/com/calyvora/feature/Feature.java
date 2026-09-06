package com.calyvora.feature;

import java.util.Arrays;
import java.util.List;

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
 * @param pathPrefix  the API surface this feature guards, or null when it guards none
 */
public enum Feature {

    PAYROLL("Payroll", "Salaries, payslips, payroll runs and the bank file", true, "/api/v1/payroll/"),
    STATUTORY_PAYROLL("Statutory payroll (PF)",
            "Provident Fund computed on payslips. Off until the numbers have been checked.", false, null),
    RECRUITMENT("Recruitment", "Job openings, candidate pipeline, offers and hiring", true, "/api/v1/recruit/"),
    PERFORMANCE("Performance", "Review cycles, self and manager reviews, hikes", true, "/api/v1/performance/"),
    WORK("Work", "Projects, tasks, sprints and tickets", true, "/api/v1/work/"),
    KNOWLEDGE("Knowledge base", "Spaces, pages and search", true, "/api/v1/knowledge/"),
    HELPDESK("Helpdesk", "Internal tickets and replies", true, "/api/v1/helpdesk/"),
    EXPENSES("Expenses", "Claims, approval and reimbursement", true, "/api/v1/expenses"),
    SHIFTS("Shifts", "Shift patterns and the roster", true, "/api/v1/shifts"),
    CLIENTS("Clients", "Client records and their staffing requirements", true, "/api/v1/clients"),
    FEED("Company feed", "Announcements, posts and comments", true, "/api/v1/feed"),
    ASSISTANT("AI assistant", "Answers questions from the company's own data", true, "/api/v1/assistant/"),
    ANALYTICS("Insights", "Headcount, attrition and cost analytics", true, "/api/v1/analytics/");

    private final String label;
    private final String description;
    private final boolean defaultOn;
    private final String pathPrefix;

    Feature(String label, String description, boolean defaultOn, String pathPrefix) {
        this.label = label;
        this.description = description;
        this.defaultOn = defaultOn;
        this.pathPrefix = pathPrefix;
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

    public String pathPrefix() {
        return pathPrefix;
    }

    /**
     * The feature guarding {@code path}, if any.
     *
     * <p>Longest prefix wins, which is the whole reason this is a method rather than a map lookup:
     * {@code /api/v1/payroll/} and a future {@code /api/v1/payroll/statutory/} would both match a
     * statutory request, and the more specific one has to.
     */
    public static Feature guarding(String path) {
        Feature best = null;
        for (Feature f : values()) {
            if (f.pathPrefix != null && path.startsWith(f.pathPrefix)
                    && (best == null || f.pathPrefix.length() > best.pathPrefix.length())) {
                best = f;
            }
        }
        return best;
    }

    public static Feature parse(String raw) {
        return Feature.valueOf(raw.trim().toUpperCase());
    }

    /** The ones a plan is assembled from — every feature, in declaration order. */
    public static List<Feature> all() {
        return Arrays.asList(values());
    }
}
