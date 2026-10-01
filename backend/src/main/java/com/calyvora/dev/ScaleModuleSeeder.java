package com.calyvora.dev;

import com.calyvora.client.Client;
import com.calyvora.client.ClientRepository;
import com.calyvora.client.ClientRequest;
import com.calyvora.client.ClientRequestRepository;
import com.calyvora.client.ClientStatus;
import com.calyvora.client.RequestStatus;
import com.calyvora.expense.ExpenseCategory;
import com.calyvora.expense.ExpenseClaim;
import com.calyvora.expense.ExpenseClaimRepository;
import com.calyvora.expense.ExpenseStatus;
import com.calyvora.feed.Post;
import com.calyvora.feed.PostComment;
import com.calyvora.feed.PostCommentRepository;
import com.calyvora.feed.PostKind;
import com.calyvora.feed.PostReaction;
import com.calyvora.feed.PostReactionRepository;
import com.calyvora.feed.PostRepository;
import com.calyvora.feed.PostVisibility;
import com.calyvora.helpdesk.HelpdeskComment;
import com.calyvora.helpdesk.HelpdeskCommentRepository;
import com.calyvora.helpdesk.HelpdeskTicket;
import com.calyvora.helpdesk.HelpdeskTicketRepository;
import com.calyvora.helpdesk.TicketCategory;
import com.calyvora.helpdesk.TicketPriority;
import com.calyvora.helpdesk.TicketStatus;
import com.calyvora.knowledge.Page;
import com.calyvora.knowledge.PageRepository;
import com.calyvora.knowledge.PageStatus;
import com.calyvora.knowledge.Space;
import com.calyvora.knowledge.SpaceRepository;
import com.calyvora.people.AttendanceRegularization;
import com.calyvora.people.AttendanceRegularizationRepository;
import com.calyvora.people.Department;
import com.calyvora.people.Employee;
import com.calyvora.people.Goal;
import com.calyvora.people.GoalRepository;
import com.calyvora.people.LeaveRequest;
import com.calyvora.people.LeaveRequestRepository;
import com.calyvora.people.LeaveStatus;
import com.calyvora.people.LeaveType;
import com.calyvora.performance.PerformanceReview;
import com.calyvora.performance.PerformanceReviewRepository;
import com.calyvora.performance.ReviewCycle;
import com.calyvora.performance.ReviewCycleRepository;
import com.calyvora.performance.ReviewStatus;
import com.calyvora.recruit.Candidate;
import com.calyvora.recruit.CandidateRepository;
import com.calyvora.recruit.CandidateStage;
import com.calyvora.recruit.JobOpening;
import com.calyvora.recruit.JobOpeningRepository;
import com.calyvora.shift.Shift;
import com.calyvora.shift.ShiftAssignment;
import com.calyvora.shift.ShiftAssignmentRepository;
import com.calyvora.shift.ShiftRepository;
import com.calyvora.work.Project;
import com.calyvora.work.ProjectRepository;
import com.calyvora.work.Sprint;
import com.calyvora.work.SprintRepository;
import com.calyvora.work.SprintStatus;
import com.calyvora.work.Task;
import com.calyvora.work.TaskPriority;
import com.calyvora.work.TaskRepository;
import com.calyvora.work.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Fills the scale tenant's other twenty modules, so every tab has something in it.
 *
 * <p>The scale seeder built a thousand people, their pay and their attendance, and stopped. Opening
 * Scaleworks therefore showed a convincing People section and eighteen empty screens — which is a
 * worse demo than seven people with a full app, because the emptiness reads as the product not
 * having the feature rather than the tenant not having the data.
 *
 * <p><b>Proportion, not volume.</b> Every module gets a realistic slice of a thousand-person
 * company, not a thousand rows: about one in five people has taken leave, one in eight has claimed
 * an expense, sixty tickets are open across the company. A thousand expense claims would not make
 * the screen more convincing, it would make it slower and less believable.
 *
 * <p>Everything is built as entities and saved in bulk rather than pushed through the services.
 * The services notify, validate and re-read per row; at this size that is tens of thousands of
 * round trips, and none of it changes what ends up on screen.
 *
 * <p>Seeded from a fixed number, so re-seeding produces the same company rather than a different
 * one each time — a demo that reshuffles itself between rehearsal and performance is its own kind
 * of bug.
 */
@Service
public class ScaleModuleSeeder {

    private static final Logger log = LoggerFactory.getLogger(ScaleModuleSeeder.class);

    /** Bulk saves go in slices; one saveAll of 20,000 entities is a heap problem, not a speed-up. */
    private static final int BATCH = 500;

    /**
     * How many items each approval queue is guaranteed to hold.
     *
     * <p>Not left to the dice. The first version rolled for every status and a sixty-person run
     * produced twelve expense claims, none of them awaiting a decision — so the approvals screen was
     * empty on a tenant that had been seeded specifically so it would not be. A demo tenant must
     * always have something to approve, at any headcount.
     */
    private static final int MIN_IN_QUEUE = 5;

    private final LeaveRequestRepository leaveRepository;
    private final ExpenseClaimRepository expenseRepository;
    private final ProjectRepository projectRepository;
    private final SprintRepository sprintRepository;
    private final TaskRepository taskRepository;
    private final SpaceRepository spaceRepository;
    private final PageRepository pageRepository;
    private final HelpdeskTicketRepository ticketRepository;
    private final HelpdeskCommentRepository ticketCommentRepository;
    private final PostRepository postRepository;
    private final PostCommentRepository postCommentRepository;
    private final PostReactionRepository postReactionRepository;
    private final ReviewCycleRepository cycleRepository;
    private final PerformanceReviewRepository reviewRepository;
    private final GoalRepository goalRepository;
    private final JobOpeningRepository jobRepository;
    private final CandidateRepository candidateRepository;
    private final ClientRepository clientRepository;
    private final ClientRequestRepository clientRequestRepository;
    private final ShiftRepository shiftRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final AttendanceRegularizationRepository regularizationRepository;

    public ScaleModuleSeeder(LeaveRequestRepository leaveRepository, ExpenseClaimRepository expenseRepository,
                             ProjectRepository projectRepository, SprintRepository sprintRepository,
                             TaskRepository taskRepository, SpaceRepository spaceRepository,
                             PageRepository pageRepository, HelpdeskTicketRepository ticketRepository,
                             HelpdeskCommentRepository ticketCommentRepository, PostRepository postRepository,
                             PostCommentRepository postCommentRepository,
                             PostReactionRepository postReactionRepository,
                             ReviewCycleRepository cycleRepository,
                             PerformanceReviewRepository reviewRepository, GoalRepository goalRepository,
                             JobOpeningRepository jobRepository, CandidateRepository candidateRepository,
                             ClientRepository clientRepository, ClientRequestRepository clientRequestRepository,
                             ShiftRepository shiftRepository,
                             ShiftAssignmentRepository shiftAssignmentRepository,
                             AttendanceRegularizationRepository regularizationRepository) {
        this.leaveRepository = leaveRepository;
        this.expenseRepository = expenseRepository;
        this.projectRepository = projectRepository;
        this.sprintRepository = sprintRepository;
        this.taskRepository = taskRepository;
        this.spaceRepository = spaceRepository;
        this.pageRepository = pageRepository;
        this.ticketRepository = ticketRepository;
        this.ticketCommentRepository = ticketCommentRepository;
        this.postRepository = postRepository;
        this.postCommentRepository = postCommentRepository;
        this.postReactionRepository = postReactionRepository;
        this.cycleRepository = cycleRepository;
        this.reviewRepository = reviewRepository;
        this.goalRepository = goalRepository;
        this.jobRepository = jobRepository;
        this.candidateRepository = candidateRepository;
        this.clientRepository = clientRepository;
        this.clientRequestRepository = clientRequestRepository;
        this.shiftRepository = shiftRepository;
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.regularizationRepository = regularizationRepository;
    }

    /** What was created, for the seed endpoint's response. */
    public record Counts(int leave, int expenses, int projects, int tasks, int pages, int tickets,
                         int posts, int reviews, int goals, int candidates, int clients,
                         int shiftAssignments, int regularizations) {
    }

    /**
     * @param employees everybody, in the order the tree was built: admin, HR, heads, leads, then the
     *                  rest. Position carries seniority, which is what makes a manager's review point
     *                  at a real manager rather than at a random colleague.
     */
    public Counts seed(UUID companyId, List<Employee> employees, List<Department> departments, UUID adminUserId) {
        long started = System.currentTimeMillis();
        Random rnd = new Random(20260916L);

        int leave = seedLeave(companyId, employees, adminUserId, rnd);
        int expenses = seedExpenses(companyId, employees, adminUserId, rnd);
        Work work = seedWork(companyId, employees, rnd);
        int pages = seedKnowledge(companyId, employees, rnd);
        int tickets = seedHelpdesk(companyId, employees, rnd);
        int posts = seedFeed(companyId, employees, departments, rnd);
        Perf perf = seedPerformance(companyId, employees, adminUserId, rnd);
        int candidates = seedRecruitment(companyId, departments, adminUserId, rnd);
        int clients = seedClients(companyId, adminUserId, rnd);
        int shifts = seedShifts(companyId, employees, rnd);
        int regularizations = seedRegularizations(companyId, employees, rnd);

        Counts counts = new Counts(leave, expenses, work.projects(), work.tasks(), pages, tickets,
                posts, perf.reviews(), perf.goals(), candidates, clients, shifts, regularizations);
        log.info("Scale modules seeded in {} ms: {}", System.currentTimeMillis() - started, counts);
        return counts;
    }

    private record Work(int projects, int tasks) {}

    private record Perf(int reviews, int goals) {}

    // --- time off --------------------------------------------------------------

    private static final String[] LEAVE_REASONS = {
            "Family wedding", "Annual trip home", "Medical appointment", "Not well — fever",
            "Moving house", "Child's school event", "Festival at home", "Personal work",
            "Recovering from surgery", "Extended weekend",
    };

    /**
     * About one person in five has booked time off: a spread of approved history, a few decisions
     * still waiting, and some refusals — a company where every request was approved looks seeded.
     */
    private int seedLeave(UUID companyId, List<Employee> employees, UUID adminUserId, Random rnd) {
        List<LeaveRequest> rows = new ArrayList<>();
        LocalDate today = LocalDate.now();
        int pending = 0;
        for (int i = 0; i < employees.size(); i += 5) {
            Employee e = employees.get(i);
            // Two or three past requests each, then a live one for some of them.
            int past = 2 + rnd.nextInt(2);
            for (int k = 0; k < past; k++) {
                LocalDate start = today.minusDays(20 + rnd.nextInt(200));
                int days = 1 + rnd.nextInt(4);
                LeaveRequest lr = new LeaveRequest(UUID.randomUUID(), companyId, e.getId(),
                        pick(rnd, LeaveType.VACATION, LeaveType.SICK, LeaveType.PERSONAL),
                        start, start.plusDays(days - 1L), days, pick(rnd, LEAVE_REASONS));
                lr.decide(rnd.nextInt(10) < 8 ? LeaveStatus.APPROVED : LeaveStatus.REJECTED, adminUserId);
                rows.add(lr);
            }
            if (rnd.nextInt(4) == 0 || pending < MIN_IN_QUEUE) {
                pending++;
                LocalDate start = today.plusDays(3 + rnd.nextInt(30));
                int days = 1 + rnd.nextInt(5);
                // Left PENDING on purpose: an approvals queue with nothing in it cannot be demoed.
                rows.add(new LeaveRequest(UUID.randomUUID(), companyId, e.getId(),
                        pick(rnd, LeaveType.VACATION, LeaveType.PERSONAL),
                        start, start.plusDays(days - 1L), days, pick(rnd, LEAVE_REASONS)));
            }
        }
        saveInBatches(rows, leaveRepository::saveAll);
        return rows.size();
    }

    // --- expenses --------------------------------------------------------------

    private static final String[][] EXPENSES = {
            {"Client visit — cab fare", "TRAVEL", "1200"},
            {"Flight to Bengaluru", "TRAVEL", "8400"},
            {"Hotel — two nights", "ACCOMMODATION", "11600"},
            {"Team lunch", "MEALS", "3800"},
            {"Customer dinner", "MEALS", "5200"},
            {"Laptop stand and keyboard", "SUPPLIES", "4300"},
            {"Monitor for home desk", "SUPPLIES", "14500"},
            {"AWS certification", "TRAINING", "9000"},
            {"Conference ticket", "TRAINING", "18000"},
            {"Internet reimbursement", "OTHER", "1500"},
    };

    private int seedExpenses(UUID companyId, List<Employee> employees, UUID adminUserId, Random rnd) {
        List<ExpenseClaim> rows = new ArrayList<>();
        LocalDate today = LocalDate.now();
        int submitted = 0;
        for (int i = 3; i < employees.size(); i += 8) {
            Employee e = employees.get(i);
            int claims = 1 + rnd.nextInt(2);
            for (int k = 0; k < claims; k++) {
                String[] spec = pick(rnd, EXPENSES);
                ExpenseClaim claim = new ExpenseClaim(UUID.randomUUID(), companyId, e.getId(), spec[0],
                        ExpenseCategory.valueOf(spec[1]), new BigDecimal(spec[2]), "INR",
                        today.minusDays(5 + rnd.nextInt(90)));
                // A spread across the whole lifecycle, so approve, reject and reimburse all have
                // something to act on rather than the queue being one state repeated.
                int roll = submitted < MIN_IN_QUEUE ? 0 : rnd.nextInt(10);
                if (roll < 3) {
                    submitted++;   // left SUBMITTED — the approval queue
                } else if (roll < 5) {
                    claim.decide(ExpenseStatus.APPROVED, adminUserId, "Approved.");
                } else if (roll < 6) {
                    claim.decide(ExpenseStatus.REJECTED, adminUserId, "Please attach the receipt.");
                } else {
                    claim.decide(ExpenseStatus.APPROVED, adminUserId, "Approved.");
                    claim.reimburse();
                }
                rows.add(claim);
            }
        }
        saveInBatches(rows, expenseRepository::saveAll);
        return rows.size();
    }

    // --- work ------------------------------------------------------------------

    private static final String[][] PROJECTS = {
            {"Atlas Platform", "ATL", "The multi-tenant core: identity, tenancy and the shared services every app builds on."},
            {"Mobile App", "MOB", "The Android and iOS clients, and the shared design system behind them."},
            {"Billing Revamp", "BIL", "Usage metering, invoices and the move off the legacy payment gateway."},
            {"Data Platform", "DAT", "The warehouse, the pipelines feeding it, and the reporting on top."},
            {"Customer Onboarding", "ONB", "Everything between a signed contract and a customer in production."},
            {"Site Reliability", "SRE", "Uptime, alerting, and the runbooks that keep the pager quiet."},
            {"Security Hardening", "SEC", "Access reviews, dependency patching and the annual penetration test."},
            {"Marketing Site", "WWW", "The public site, the pricing page and the docs that sit beside them."},
    };

    private static final String[] TASK_TITLES = {
            "Write the migration", "Review the API contract", "Fix the flaky test", "Add request logging",
            "Update the runbook", "Cut the release branch", "Triage last week's bugs", "Profile the slow query",
            "Design the empty state", "Wire the feature flag", "Back-fill the missing rows",
            "Document the rollback", "Add the missing index", "Handle the timeout case",
            "Split the god component", "Cache the lookup", "Retire the old endpoint", "Chase the vendor",
            "Prepare the demo data", "Rotate the signing key",
    };

    private Work seedWork(UUID companyId, List<Employee> employees, Random rnd) {
        List<Project> projects = new ArrayList<>();
        List<Sprint> sprints = new ArrayList<>();
        List<Task> tasks = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (int p = 0; p < PROJECTS.length; p++) {
            String[] spec = PROJECTS[p];
            // Leads sit just after the heads in the list; picking from there means a project lead is
            // somebody with a team rather than a random junior.
            Employee lead = employees.get(Math.min(2 + p, employees.size() - 1));
            Project project = new Project(UUID.randomUUID(), companyId, spec[0], spec[1], spec[2],
                    lead.getUserId());
            projects.add(project);

            // Two finished sprints, one running: the velocity chart needs history to plot.
            Sprint done1 = sprint(companyId, project.getId(), "Sprint " + (p * 3 + 1),
                    today.minusDays(42), today.minusDays(29), SprintStatus.COMPLETED);
            Sprint done2 = sprint(companyId, project.getId(), "Sprint " + (p * 3 + 2),
                    today.minusDays(28), today.minusDays(15), SprintStatus.COMPLETED);
            Sprint active = sprint(companyId, project.getId(), "Sprint " + (p * 3 + 3),
                    today.minusDays(14), today.plusDays(1), SprintStatus.ACTIVE);
            sprints.add(done1);
            sprints.add(done2);
            sprints.add(active);

            int number = 0;
            for (Sprint s : List.of(done1, done2, active)) {
                int count = 8 + rnd.nextInt(5);
                for (int t = 0; t < count; t++) {
                    Employee assignee = employees.get(rnd.nextInt(employees.size()));
                    Task task = new Task(UUID.randomUUID(), companyId, project.getId(), ++number,
                            pick(rnd, TASK_TITLES), lead.getUserId());
                    task.setAssigneeId(assignee.getId());
                    task.setSprintId(s.getId());
                    task.setPriority(pick(rnd, TaskPriority.LOW, TaskPriority.MEDIUM, TaskPriority.HIGH));
                    task.setStoryPoints(pick(rnd, 1, 2, 3, 5, 8));
                    task.setStatus(s.getStatus() == SprintStatus.COMPLETED
                            ? TaskStatus.DONE
                            : pick(rnd, TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.DONE));
                    task.setSortOrder(t);
                    tasks.add(task);
                }
            }
            // A backlog: tasks in no sprint at all, which is where a board starts.
            for (int t = 0; t < 6; t++) {
                Task task = new Task(UUID.randomUUID(), companyId, project.getId(), ++number,
                        pick(rnd, TASK_TITLES), lead.getUserId());
                task.setPriority(pick(rnd, TaskPriority.LOW, TaskPriority.MEDIUM));
                task.setStatus(TaskStatus.TODO);
                task.setSortOrder(t);
                tasks.add(task);
            }
        }

        projectRepository.saveAll(projects);
        sprintRepository.saveAll(sprints);
        saveInBatches(tasks, taskRepository::saveAll);
        return new Work(projects.size(), tasks.size());
    }

    private Sprint sprint(UUID companyId, UUID projectId, String name, LocalDate from, LocalDate to,
                          SprintStatus status) {
        Sprint s = new Sprint(UUID.randomUUID(), companyId, projectId, name, "Ship what we committed to",
                from, to);
        s.setStatus(status);
        s.setCapacityPoints(40);
        return s;
    }

    // --- knowledge -------------------------------------------------------------

    private static final String[][] SPACES = {
            {"Engineering", "ENG", "How we build, review and ship."},
            {"People Ops", "POPS", "Policies, benefits and the answers to the questions HR gets weekly."},
            {"Sales Playbook", "SALES", "Positioning, objection handling and the pricing rules."},
            {"Support", "SUP", "Escalation paths and the fixes for the things that keep coming back."},
            {"Onboarding", "NEW", "Your first week, in order."},
            {"Security", "SEC", "What to do, and who to call, when something looks wrong."},
    };

    private static final String[] PAGE_TITLES = {
            "Getting started", "How we do code review", "On-call handbook", "Expense policy",
            "Leave policy explained", "Laptop and equipment", "Incident response", "Release checklist",
            "Interview process", "Glossary",
    };

    private int seedKnowledge(UUID companyId, List<Employee> employees, Random rnd) {
        List<Space> spaces = new ArrayList<>();
        List<Page> pages = new ArrayList<>();
        for (String[] spec : SPACES) {
            Employee owner = employees.get(rnd.nextInt(Math.min(50, employees.size())));
            Space space = new Space(UUID.randomUUID(), companyId, spec[0], spec[1], spec[2], owner.getUserId());
            spaces.add(space);
            for (int i = 0; i < 5; i++) {
                Employee author = employees.get(rnd.nextInt(employees.size()));
                Page page = new Page(UUID.randomUUID(), companyId, space.getId(),
                        PAGE_TITLES[(i + spaces.size()) % PAGE_TITLES.length], author.getId(),
                        author.getUserId());
                page.setBody("## " + spec[0] + "\n\nWritten up so nobody has to ask twice. "
                        + "Keep this page current — if it is wrong, fix it rather than working around it.");
                // One draft per space: a knowledge base where everything is published has no story
                // about drafts, and that is a question every buyer asks.
                page.setStatus(i == 4 ? PageStatus.DRAFT : PageStatus.PUBLISHED);
                page.setSortOrder(i);
                pages.add(page);
            }
        }
        spaceRepository.saveAll(spaces);
        saveInBatches(pages, pageRepository::saveAll);
        return pages.size();
    }

    // --- helpdesk --------------------------------------------------------------

    private static final String[][] TICKETS = {
            {"HR", "Payslip for last month is missing", "I can see October but not November."},
            {"PAYROLL", "PF number looks wrong on my payslip", "The last four digits do not match my passbook."},
            {"IT", "Laptop will not connect to the VPN", "Started after the update on Tuesday."},
            {"IT", "Need access to the reporting dashboard", "Joined the data team last week."},
            {"FACILITIES", "Air conditioning on the third floor", "It has been off since Monday."},
            {"HR", "Question about the notice period", "Moving cities — what does the policy say?"},
            {"PAYROLL", "Reimbursement not credited", "Approved three weeks ago."},
            {"IT", "Second monitor request", "Working from the Pune office from next month."},
            {"FACILITIES", "Parking pass renewal", "Mine expires at the end of the month."},
            {"HR", "Updating my emergency contact", "Cannot find where to change it."},
    };

    private int seedHelpdesk(UUID companyId, List<Employee> employees, Random rnd) {
        List<HelpdeskTicket> tickets = new ArrayList<>();
        List<HelpdeskComment> comments = new ArrayList<>();
        // HR is the second person created; they answer most of these.
        Employee hr = employees.get(1);
        int open = 0;
        for (int i = 0; i < 60; i++) {
            String[] spec = TICKETS[i % TICKETS.length];
            Employee raiser = employees.get(rnd.nextInt(employees.size()));
            HelpdeskTicket ticket = new HelpdeskTicket(UUID.randomUUID(), companyId, raiser.getUserId(),
                    TicketCategory.valueOf(spec[0]), spec[1], spec[2],
                    pick(rnd, TicketPriority.LOW, TicketPriority.MEDIUM, TicketPriority.HIGH));
            int roll = open < MIN_IN_QUEUE ? 0 : rnd.nextInt(10);
            if (roll < 4) {
                open++;   // OPEN — the queue somebody has to work through
            } else if (roll < 7) {
                ticket.setStatus(TicketStatus.IN_PROGRESS);
                ticket.setAssigneeId(hr.getUserId());
            } else {
                ticket.setStatus(TicketStatus.RESOLVED);
                ticket.setAssigneeId(hr.getUserId());
                ticket.setResolvedAt(java.time.Instant.now().minusSeconds(3600L * (1 + rnd.nextInt(200))));
            }
            tickets.add(ticket);
            if (roll >= 4) {
                comments.add(new HelpdeskComment(UUID.randomUUID(), companyId, ticket.getId(),
                        hr.getUserId(), "Looking into this — I'll come back to you today."));
            }
        }
        saveInBatches(tickets, ticketRepository::saveAll);
        saveInBatches(comments, ticketCommentRepository::saveAll);
        return tickets.size();
    }

    // --- feed ------------------------------------------------------------------

    private static final String[][] POSTS = {
            {"ANNOUNCEMENT", "We closed the quarter at 112% of plan. Thank you, all of you."},
            {"CELEBRATION", "Welcome to the fourteen people who joined us this month 🎉"},
            {"UPDATE", "The new expense flow is live. Claims now reach your manager directly."},
            {"ANNOUNCEMENT", "Diwali holidays are confirmed — see the holiday calendar for the dates."},
            {"CELEBRATION", "Congratulations to the Atlas team on shipping multi-region."},
            {"QUESTION", "Anyone got a recommendation for a good Postgres course?"},
            {"UPDATE", "Payroll moves to the 28th from next month. Nothing else changes."},
            {"CELEBRATION", "Five years at Scaleworks for three of our engineers today."},
            {"UPDATE", "Office wifi will be down on Saturday morning for a switch upgrade."},
            {"ANNOUNCEMENT", "Our SOC 2 audit passed with no exceptions."},
            {"QUESTION", "Is anyone driving to the Pune office on Thursday?"},
            {"UPDATE", "New starters: the onboarding space has been rewritten. It is much shorter now."},
    };

    private static final String[] REPLIES = {
            "Great news.", "Congratulations!", "Thanks for sorting this out.", "This is really helpful.",
            "Finally 🙌", "Nice one.", "Well deserved.",
    };

    private int seedFeed(UUID companyId, List<Employee> employees, List<Department> departments, Random rnd) {
        List<Post> posts = new ArrayList<>();
        List<PostComment> comments = new ArrayList<>();
        List<PostReaction> reactions = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            String[] spec = POSTS[i % POSTS.length];
            Employee author = employees.get(rnd.nextInt(Math.min(60, employees.size())));
            boolean departmental = rnd.nextInt(4) == 0 && !departments.isEmpty();
            Post post = new Post(UUID.randomUUID(), companyId, author.getUserId(),
                    PostKind.valueOf(spec[0]), spec[1],
                    departmental ? PostVisibility.DEPARTMENT : PostVisibility.COMPANY,
                    departmental ? departments.get(rnd.nextInt(departments.size())).getId() : null);
            if (i == 0) {
                post.setPinned(true);
            }
            posts.add(post);

            for (int c = 0; c < rnd.nextInt(4); c++) {
                Employee commenter = employees.get(rnd.nextInt(employees.size()));
                comments.add(new PostComment(UUID.randomUUID(), companyId, post.getId(),
                        commenter.getUserId(), pick(rnd, REPLIES)));
            }
            // Reactions are keyed on (post, user, emoji), so the same person must not react twice
            // with the same emoji — a duplicate key is a failed seed, not a duplicate row.
            int reactors = 2 + rnd.nextInt(12);
            for (int r = 0; r < reactors; r++) {
                Employee fan = employees.get((r * 37 + i * 11) % employees.size());
                reactions.add(new PostReaction(post.getId(), fan.getUserId(),
                        pick(rnd, "👍", "🎉", "❤️"), companyId));
            }
        }
        saveInBatches(posts, postRepository::saveAll);
        saveInBatches(comments, postCommentRepository::saveAll);
        saveInBatches(reactions, postReactionRepository::saveAll);
        return posts.size();
    }

    // --- performance -----------------------------------------------------------

    private static final String[] GOALS = {
            "Cut p95 latency on the checkout path by 30%",
            "Close the quarter with no Sev-1 incidents",
            "Mentor one junior engineer through their first release",
            "Ship the customer-facing audit log",
            "Reduce onboarding time for a new customer to under a week",
            "Get the dependency backlog to zero criticals",
            "Run four customer interviews a month",
            "Take the AWS Solutions Architect certification",
    };

    private Perf seedPerformance(UUID companyId, List<Employee> employees, UUID adminUserId, Random rnd) {
        LocalDate start = LocalDate.now().minusMonths(6).withDayOfMonth(1);
        ReviewCycle cycle = new ReviewCycle(UUID.randomUUID(), companyId,
                "H1 Review " + start.getYear(), start, start.plusMonths(6).minusDays(1), adminUserId);
        cycleRepository.save(cycle);

        List<PerformanceReview> reviews = new ArrayList<>();
        List<Goal> goals = new ArrayList<>();
        // Everyone with a manager is in the cycle, which is what a real one looks like; the statuses
        // are spread so the screen shows a cycle in flight rather than one that is finished or unstarted.
        for (Employee e : employees) {
            if (e.getManagerId() == null) {
                continue;
            }
            PerformanceReview review = new PerformanceReview(UUID.randomUUID(), companyId, cycle.getId(),
                    e.getId(), e.getManagerId());
            int roll = rnd.nextInt(10);
            if (roll < 3) {
                review.setStatus(ReviewStatus.PENDING_SELF);
            } else if (roll < 6) {
                review.setStatus(ReviewStatus.PENDING_MANAGER);
                review.setSelfAssessment("Delivered what I picked up and helped two people onto the team.");
                review.setSelfSubmittedAt(java.time.Instant.now().minusSeconds(86400L * (2 + rnd.nextInt(20))));
            } else {
                review.setStatus(ReviewStatus.SUBMITTED);
                review.setSelfAssessment("A steady half — shipped the migration and kept the pager quiet.");
                review.setSelfSubmittedAt(java.time.Instant.now().minusSeconds(86400L * 30));
                review.setManagerRating(3 + rnd.nextInt(3));
                review.setManagerSummary("Reliable, and increasingly the person others ask first.");
                review.setStrengths("Ownership; clear written updates.");
                review.setImprovements("Delegate more of the routine work.");
                review.setManagerSubmittedAt(java.time.Instant.now().minusSeconds(86400L * 10));
            }
            reviews.add(review);
        }

        for (int i = 0; i < employees.size(); i += 4) {
            Employee e = employees.get(i);
            Goal goal = new Goal(UUID.randomUUID(), companyId, e.getId(), pick(rnd, GOALS), null,
                    LocalDate.now().plusMonths(1 + rnd.nextInt(3)), adminUserId);
            goal.setProgress(rnd.nextInt(101));
            goals.add(goal);
        }

        saveInBatches(reviews, reviewRepository::saveAll);
        saveInBatches(goals, goalRepository::saveAll);
        return new Perf(reviews.size(), goals.size());
    }

    // --- recruitment -----------------------------------------------------------

    private static final String[] ROLES_OPEN = {
            "Senior Backend Engineer", "Frontend Engineer", "Engineering Manager", "Product Designer",
            "Data Analyst", "Site Reliability Engineer", "Account Executive", "Customer Success Manager",
            "Talent Partner", "Finance Analyst",
    };

    private static final String[] CANDIDATE_FIRST = {
            "Aarav", "Diya", "Kabir", "Meera", "Rohan", "Sana", "Vikram", "Ananya", "Farhan", "Tara",
            "Nikhil", "Isha", "Arjun", "Priyanka", "Zoya",
    };
    private static final String[] CANDIDATE_LAST = {
            "Sharma", "Iyer", "Khan", "Reddy", "Bose", "Nair", "Gupta", "Desai", "Rao", "Mehta",
    };

    private int seedRecruitment(UUID companyId, List<Department> departments, UUID adminUserId, Random rnd) {
        List<JobOpening> jobs = new ArrayList<>();
        List<Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < ROLES_OPEN.length; i++) {
            JobOpening job = new JobOpening(UUID.randomUUID(), companyId, ROLES_OPEN[i],
                    departments.isEmpty() ? null : departments.get(i % departments.size()).getId(),
                    pick(rnd, "Ahmedabad", "Bengaluru", "Pune", "Remote"), "FULL_TIME",
                    "We are hiring a " + ROLES_OPEN[i] + " to join a team that already ships weekly.",
                    1 + rnd.nextInt(3), adminUserId);
            if (i == ROLES_OPEN.length - 1) {
                job.setStatus(com.calyvora.recruit.JobStatus.ON_HOLD);
            }
            jobs.add(job);

            // A pipeline, not a list: every stage occupied, so the board reads as work in progress.
            int count = 5 + rnd.nextInt(4);
            for (int c = 0; c < count; c++) {
                String name = pick(rnd, CANDIDATE_FIRST) + " " + pick(rnd, CANDIDATE_LAST);
                Candidate cand = new Candidate(UUID.randomUUID(), companyId, job.getId(), name,
                        name.toLowerCase(java.util.Locale.ROOT).replace(' ', '.') + "@example.com",
                        String.format("+91 9%09d", rnd.nextInt(1_000_000_000)), null,
                        pick(rnd, "Referral", "LinkedIn", "Careers page", "Agency"));
                cand.setStage(pick(rnd, CandidateStage.APPLIED, CandidateStage.APPLIED,
                        CandidateStage.SCREENING, CandidateStage.INTERVIEW, CandidateStage.OFFER,
                        CandidateStage.HIRED));
                cand.setRating(1 + rnd.nextInt(5));
                candidates.add(cand);
            }
        }
        jobRepository.saveAll(jobs);
        saveInBatches(candidates, candidateRepository::saveAll);
        return candidates.size();
    }

    // --- clients ---------------------------------------------------------------

    private static final String[] CLIENT_NAMES = {
            "Meridian Logistics", "Bluepeak Retail", "Corvus Analytics", "Harbour & Finch",
            "Northgate Health", "Solstice Media", "Tavara Foods", "Ironbridge Manufacturing",
            "Lumen Education", "Pallas Insurance", "Verda Energy", "Westbay Hotels",
    };

    private static final String[] CLIENT_ASKS = {
            "Single sign-on with our identity provider",
            "Monthly usage export to our warehouse",
            "A second sandbox environment",
            "Custom approval rules for expenses",
            "Data residency in-region",
            "Quarterly business review deck",
    };

    private int seedClients(UUID companyId, UUID adminUserId, Random rnd) {
        List<Client> clients = new ArrayList<>();
        List<ClientRequest> requests = new ArrayList<>();
        for (int i = 0; i < CLIENT_NAMES.length; i++) {
            Client client = new Client(UUID.randomUUID(), companyId, CLIENT_NAMES[i], adminUserId);
            client.setContactName(pick(rnd, CANDIDATE_FIRST) + " " + pick(rnd, CANDIDATE_LAST));
            client.setContactEmail("hello@" + CLIENT_NAMES[i].toLowerCase(java.util.Locale.ROOT)
                    .replaceAll("[^a-z]", "") + ".com");
            client.setPhone(String.format("+91 2%09d", rnd.nextInt(1_000_000_000)));
            client.setStatus(i < 8 ? ClientStatus.ACTIVE : i < 11 ? ClientStatus.LEAD : ClientStatus.CHURNED);
            clients.add(client);

            for (int r = 0; r < rnd.nextInt(4); r++) {
                ClientRequest request = new ClientRequest(UUID.randomUUID(), companyId, client.getId(),
                        pick(rnd, CLIENT_ASKS), "Raised on the last call. Needs a date before renewal.");
                request.setStatus(pick(rnd, RequestStatus.REQUESTED, RequestStatus.IN_PROGRESS,
                        RequestStatus.DELIVERED, RequestStatus.DECLINED));
                requests.add(request);
            }
        }
        clientRepository.saveAll(clients);
        clientRequestRepository.saveAll(requests);
        return clients.size();
    }

    // --- shifts ----------------------------------------------------------------

    private int seedShifts(UUID companyId, List<Employee> employees, Random rnd) {
        List<Shift> shifts = List.of(
                new Shift(UUID.randomUUID(), companyId, "General", LocalTime.of(9, 30), LocalTime.of(18, 30), "#7c5cff"),
                new Shift(UUID.randomUUID(), companyId, "Early", LocalTime.of(6, 30), LocalTime.of(15, 0), "#22c55e"),
                new Shift(UUID.randomUUID(), companyId, "Night", LocalTime.of(22, 0), LocalTime.of(6, 30), "#f59e0b"));
        shiftRepository.saveAll(shifts);

        // Only the support-shaped part of the company is rostered, over the coming fortnight —
        // rostering a thousand engineers onto a night shift would be data nobody believes.
        List<ShiftAssignment> assignments = new ArrayList<>();
        int rostered = Math.min(employees.size(), 80);
        LocalDate from = LocalDate.now().minusDays(3);
        for (int d = 0; d < 14; d++) {
            LocalDate day = from.plusDays(d);
            if (day.getDayOfWeek() == DayOfWeek.SUNDAY) {
                continue;
            }
            for (int i = 0; i < rostered; i++) {
                Employee e = employees.get(employees.size() - 1 - i);
                Shift shift = shifts.get((i + d) % shifts.size());
                assignments.add(new ShiftAssignment(UUID.randomUUID(), companyId, e.getId(), shift.getId(), day));
            }
        }
        saveInBatches(assignments, shiftAssignmentRepository::saveAll);
        return assignments.size();
    }

    // --- regularizations -------------------------------------------------------

    private static final String[] REG_REASONS = {
            "Forgot to check in — was in the office all day.",
            "Client site visit, no access to the app.",
            "Laptop battery died before check-out.",
            "Worked from home, network was down in the morning.",
            "Was on the early shift and the app would not load.",
    };

    private int seedRegularizations(UUID companyId, List<Employee> employees, Random rnd) {
        List<AttendanceRegularization> rows = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            Employee e = employees.get(rnd.nextInt(employees.size()));
            // All left PENDING: this screen exists to be an approval queue, and an empty one cannot
            // be shown to anybody.
            rows.add(new AttendanceRegularization(UUID.randomUUID(), companyId, e.getId(),
                    LocalDate.now().minusDays(1 + rnd.nextInt(20)),
                    LocalTime.of(9, 30), LocalTime.of(18, 30), pick(rnd, REG_REASONS)));
        }
        regularizationRepository.saveAll(rows);
        return rows.size();
    }

    // --- helpers ---------------------------------------------------------------

    @SafeVarargs
    private static <T> T pick(Random rnd, T... options) {
        return options[rnd.nextInt(options.length)];
    }

    private static <T> void saveInBatches(List<T> rows, Consumer<List<T>> save) {
        for (int i = 0; i < rows.size(); i += BATCH) {
            save.accept(rows.subList(i, Math.min(i + BATCH, rows.size())));
        }
    }
}
