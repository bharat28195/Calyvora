package com.calyvora.assistant;

import com.calyvora.client.ClientRepository;
import com.calyvora.document.DocumentTemplateRepository;
import com.calyvora.expense.ExpenseClaimRepository;
import com.calyvora.expense.ExpenseStatus;
import com.calyvora.invitation.InvitationRepository;
import com.calyvora.invitation.InvitationStatus;
import com.calyvora.people.EmployeeRepository;
import com.calyvora.people.EmploymentStatus;
import com.calyvora.people.HolidayRepository;
import com.calyvora.people.LeaveRequestRepository;
import com.calyvora.people.LeaveStatus;
import com.calyvora.performance.ReviewCycleRepository;
import com.calyvora.performance.ReviewCycleStatus;
import com.calyvora.recruit.CandidateRepository;
import com.calyvora.recruit.CandidateStage;
import com.calyvora.recruit.JobOpeningRepository;
import com.calyvora.recruit.JobStatus;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything the assistant is allowed to know about a company, gathered in one place.
 *
 * <p>This exists so the assistant can answer about the <em>whole</em> app rather than the two or
 * three modules it originally read. It is a separate bean rather than ten more constructor arguments
 * on {@link AssistantService}: the service's job is retrieval and provider choice, and it should not
 * also be the place that knows which repository holds expense claims.
 *
 * <p><b>Scope is the important part.</b> The assistant is available to every signed-in person, so
 * what it retrieves has to match what the caller could already see by walking the app themselves.
 * A MEMBER asking "how many people are on notice?" must not get an answer HR would get — the
 * assistant would then be a way around the navigation's own role gates. Nothing here ever includes
 * salary, PAN or bank details at any scope: those are private by design (PD-19) and an assistant is
 * exactly the sort of side door that quietly undoes such a rule.
 */
@Component
class CompanySnapshot {

    /** What the caller is allowed to be told, mirroring the nav's role gates in app-shell.tsx. */
    enum Scope {
        /** Any signed-in employee: the directory, the handbook, the holiday list. */
        EVERYONE,
        /** Managers: the work their team owes and the approvals sitting with them. */
        MANAGES,
        /** HR, Admin, Owner: hiring, reviews, documents, clients — the operational picture. */
        HR_PLUS;

        static Scope of(String role) {
            if (role == null) return EVERYONE;
            return switch (role) {
                case "OWNER", "ADMIN", "HR" -> HR_PLUS;
                case "MANAGER" -> MANAGES;
                default -> EVERYONE;
            };
        }

        boolean atLeast(Scope other) {
            return ordinal() >= other.ordinal();
        }
    }

    private final EmployeeRepository employees;
    private final LeaveRequestRepository leave;
    private final HolidayRepository holidays;
    private final com.calyvora.helpdesk.HelpdeskTicketRepository helpdesk;
    private final JobOpeningRepository jobs;
    private final CandidateRepository candidates;
    private final ExpenseClaimRepository expenses;
    private final ReviewCycleRepository cycles;
    private final DocumentTemplateRepository templates;
    private final ClientRepository clients;
    private final InvitationRepository invitations;

    CompanySnapshot(EmployeeRepository employees, LeaveRequestRepository leave, HolidayRepository holidays,
                    com.calyvora.helpdesk.HelpdeskTicketRepository helpdesk, JobOpeningRepository jobs,
                    CandidateRepository candidates, ExpenseClaimRepository expenses,
                    ReviewCycleRepository cycles, DocumentTemplateRepository templates,
                    ClientRepository clients, InvitationRepository invitations) {
        this.employees = employees;
        this.leave = leave;
        this.holidays = holidays;
        this.helpdesk = helpdesk;
        this.jobs = jobs;
        this.candidates = candidates;
        this.expenses = expenses;
        this.cycles = cycles;
        this.templates = templates;
        this.clients = clients;
        this.invitations = invitations;
    }

    /**
     * The counts this caller may be told, keyed by the same names the offline provider matches on.
     * Ordered, because the map is also printed verbatim into the model prompt and a stable order
     * makes two identical questions produce two identical prompts.
     */
    Map<String, Long> metrics(UUID companyId, Scope scope) {
        Map<String, Long> m = new LinkedHashMap<>();

        // --- anyone in the company ---
        m.put("holidays", holidays.countByCompanyId(companyId));

        if (scope.atLeast(Scope.MANAGES)) {
            m.put("pendingLeaveRequests", leave.countByCompanyIdAndStatus(companyId, LeaveStatus.PENDING));
            m.put("pendingExpenseClaims", expenses.countByCompanyIdAndStatus(companyId, ExpenseStatus.SUBMITTED));
            m.put("peopleOnNotice", employees.countByCompanyIdAndEmploymentStatus(companyId, EmploymentStatus.NOTICE));
            m.put("peopleOnboarding", employees.countByCompanyIdAndEmploymentStatus(companyId, EmploymentStatus.ONBOARDING));
        }

        if (scope.atLeast(Scope.HR_PLUS)) {
            m.put("openHelpdeskTickets", helpdesk.countByCompanyIdAndStatusIn(companyId,
                    List.of(com.calyvora.helpdesk.TicketStatus.OPEN,
                            com.calyvora.helpdesk.TicketStatus.IN_PROGRESS)));
            m.put("openRoles", jobs.countByCompanyIdAndStatus(companyId, JobStatus.OPEN));
            // "In pipeline" means still being considered — the hired and the rejected have left it.
            m.put("candidatesInPipeline", candidates.countByCompanyIdAndStageNotIn(companyId,
                    List.of(CandidateStage.HIRED, CandidateStage.REJECTED)));
            m.put("candidatesTotal", candidates.countByCompanyId(companyId));
            m.put("openReviewCycles", cycles.countByCompanyIdAndStatus(companyId, ReviewCycleStatus.OPEN));
            m.put("letterTemplates", templates.countByCompanyId(companyId));
            m.put("clients", clients.countByCompanyId(companyId));
            m.put("pendingInvitations", invitations.countByCompanyIdAndStatus(companyId, InvitationStatus.PENDING));
        }

        return m;
    }

    /**
     * One line per module explaining what the numbers mean, so a language model does not have to
     * guess that "peopleOnNotice" is an exit in progress rather than a disciplinary matter.
     */
    String legend(Scope scope) {
        StringBuilder sb = new StringBuilder("""
                WHAT THE METRICS MEAN:
                - holidays: public holidays configured for the year
                """);
        if (scope.atLeast(Scope.MANAGES)) {
            sb.append("""
                    - pendingLeaveRequests: time-off requests waiting for an approver
                    - pendingExpenseClaims: expense claims submitted and not yet decided
                    - peopleOnNotice: employees serving notice, with an exit checklist running
                    - peopleOnboarding: employees hired but not yet fully started
                    """);
        }
        if (scope.atLeast(Scope.HR_PLUS)) {
            sb.append("""
                    - openHelpdeskTickets: HR helpdesk tickets employees are waiting on
                    - openRoles: job openings currently being hired for
                    - candidatesInPipeline: candidates still in play (excludes hired and rejected)
                    - openReviewCycles: performance review cycles currently running
                    - letterTemplates: offer/joining/relieving letter templates set up
                    - clients: client companies on the books
                    - pendingInvitations: people invited who have not accepted yet
                    """);
        }
        return sb.toString();
    }
}
