package com.calyvora.access;

/**
 * What a role may do inside one company (PD-54).
 *
 * <p>A permission says <em>what</em>. For the ones marked {@code scoped}, each grant also says
 * <em>whose</em>: {@link PermissionScope#COMPANY} for everyone, or {@link PermissionScope#TEAM} for the
 * people below the holder in the reporting tree ({@code OrgScope}). Unscoped permissions are company
 * actions with no "own team" version — running payroll for half the company is not a thing.
 *
 * <p>The names are stored in {@code company_role_permissions}, so an existing constant is never
 * renamed or removed without a migration that rewrites the rows first (the V61 lesson).
 */
public enum Permission {

    // --- Company administration ---
    MEMBERS_MANAGE("Company", "Manage members and roles", "Invite people, change anyone's role, and create or edit roles.", false),
    COMPANY_SETTINGS("Company", "Company settings", "Company details, timezone, currency, sign-in rules and the letterpad.", false),
    BILLING_MANAGE("Company", "Billing and seats", "See the subscription and invoices, and ask for more seats.", false),
    FEED_MODERATE("Company", "Moderate announcements", "Pin, edit or remove anybody's post in the company feed.", false),

    // --- People ---
    PEOPLE_MANAGE("People", "Manage people and org", "Edit employee records, departments, designations and onboarding.", false),
    ORG_VIEW_ALL("People", "See everyone", "See the whole company on people and team screens, not just their own reports.", false),
    PEOPLE_PRIVATE_VIEW("People", "See private details", "PAN, bank account, UAN and other personal details of other employees.", false),
    LEAVE_POLICY_MANAGE("People", "Manage leave policy and holidays", "Set leave entitlements, accrual, carry-forward and the holiday calendar.", false),
    EXITS_MANAGE("People", "Manage exits", "Start, cancel and complete someone's exit, and work the exit checklist.", false),

    // --- Time ---
    LEAVE_APPROVE("Time", "Approve leave", "Approve or reject leave and comp-off requests.", true),
    ATTENDANCE_MANAGE("Time", "Manage attendance", "Approve attendance corrections; company-wide also opens the day sheet and marking.", true),
    SHIFTS_MANAGE("Time", "Manage shifts", "Create shifts and assign the roster.", false),

    // --- Money ---
    SALARY_VIEW("Money", "See salaries", "See other people's salary, payslips, salary history and the pay in reviews.", true),
    PAYROLL_MANAGE("Money", "Run payroll", "Change salaries, the payslip template and PF settings, run payroll and the bank file.", false),
    TAX_MANAGE("Money", "Manage income tax", "See everyone's tax declarations and open or close the declaration window.", false),
    EXPENSES_APPROVE("Money", "Approve expenses", "Approve or reject expense claims.", true),
    EXPENSES_REIMBURSE("Money", "Reimburse expenses", "See the company's expense queue and mark claims as paid.", false),

    // --- Talent and documents ---
    RECRUITMENT_MANAGE("Talent", "Manage hiring", "Job openings, candidates, offers and hiring.", false),
    PERFORMANCE_MANAGE("Talent", "Manage performance", "Run review cycles, approve reviews and manage anyone's goals.", false),
    DOCUMENTS_ISSUE("Talent", "Issue letters", "Generate offer, joining, increment, experience and relieving letters, and manage templates.", false),
    DOCUMENTS_PUBLISH("Talent", "Publish company documents", "Write, publish, upload and remove policies and handbooks everyone reads.", false),
    HELPDESK_MANAGE("Talent", "Answer helpdesk", "See and answer every helpdesk ticket in the company.", false),
    INSIGHTS_VIEW("Talent", "See insights", "Company analytics: headcount, attrition, leave and cost.", false);

    private final String group;
    private final String label;
    private final String description;
    private final boolean scoped;

    Permission(String group, String label, String description, boolean scoped) {
        this.group = group;
        this.label = label;
        this.description = description;
        this.scoped = scoped;
    }

    public String group() {
        return group;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    /** Whether a grant of this permission may be limited to the holder's own team. */
    public boolean scoped() {
        return scoped;
    }
}
