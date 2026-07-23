package com.calyvora.dev;

import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.TenantContext;
import com.calyvora.common.util.Slugs;
import com.calyvora.company.Company;
import com.calyvora.company.CompanyRepository;
import com.calyvora.company.CompanySettings;
import com.calyvora.company.CompanySettingsRepository;
import com.calyvora.company.CompanyStatus;
import com.calyvora.expense.ExpenseService;
import com.calyvora.expense.dto.ExpensePayload;
import com.calyvora.expense.dto.ExpenseResponse;
import com.calyvora.feed.FeedService;
import com.calyvora.feed.dto.PostPayload;
import com.calyvora.identity.Role;
import com.calyvora.identity.User;
import com.calyvora.identity.UserRepository;
import com.calyvora.identity.UserStatus;
import com.calyvora.knowledge.PageService;
import com.calyvora.knowledge.SpaceService;
import com.calyvora.knowledge.dto.CreatePageRequest;
import com.calyvora.knowledge.dto.CreateSpaceRequest;
import com.calyvora.knowledge.dto.PageResponse;
import com.calyvora.knowledge.dto.SpaceResponse;
import com.calyvora.people.DepartmentService;
import com.calyvora.people.EmployeeService;
import com.calyvora.people.dto.CreateDepartmentRequest;
import com.calyvora.people.dto.DepartmentResponse;
import com.calyvora.people.dto.EmployeeResponse;
import com.calyvora.people.dto.UpdateEmployeeRequest;
import com.calyvora.work.ProjectService;
import com.calyvora.work.SprintService;
import com.calyvora.work.TaskService;
import com.calyvora.work.TicketService;
import com.calyvora.work.dto.CreateProjectRequest;
import com.calyvora.work.dto.CreateSprintRequest;
import com.calyvora.work.dto.CreateTaskRequest;
import com.calyvora.work.dto.CreateTicketRequest;
import com.calyvora.work.dto.ProjectResponse;
import com.calyvora.work.dto.SprintResponse;
import com.calyvora.work.dto.TaskResponse;
import com.calyvora.work.dto.UpdateTaskRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Provisions a rich, self-consistent demo company so every screen tells a story on first open — the
 * difference between a client seeing an empty product and a living one. It drives the <em>real</em>
 * domain services (not raw SQL), so slugs, project keys, member counts, sprint state, and the
 * cross-app graph (task ↔ doc ↔ employee) are all genuine.
 *
 * <p>Idempotent: keyed on the owner's email, a second call just returns the same credentials.
 * Dev/demo only — exposed under the already-public {@code /api/v1/dev/**} surface.
 */
@Service
@Profile("!prod")
public class DemoSeedService {

    private static final Logger log = LoggerFactory.getLogger(DemoSeedService.class);

    private static final String COMPANY = "Northwind Robotics";
    private static final String OWNER_EMAIL = "ava.chen@northwind.demo";
    private static final String DEMO_PASSWORD = "demopass123";

    private final CompanyRepository companyRepository;
    private final CompanySettingsRepository companySettingsRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DepartmentService departmentService;
    private final EmployeeService employeeService;
    private final ProjectService projectService;
    private final SprintService sprintService;
    private final TaskService taskService;
    private final TicketService ticketService;
    private final SpaceService spaceService;
    private final PageService pageService;
    private final com.calyvora.people.CompensationRepository compensationRepository;
    private final com.calyvora.people.GoalRepository goalRepository;
    private final com.calyvora.client.ClientService clientService;
    private final com.calyvora.document.DocumentService documentService;
    private final com.calyvora.people.AttendanceService attendanceService;
    private final com.calyvora.people.HolidayService holidayService;
    private final com.calyvora.people.HolidayRepository holidayRepository;
    private final ExpenseService expenseService;
    private final FeedService feedService;
    private final com.calyvora.performance.PerformanceReviewService performanceReviewService;

    public DemoSeedService(CompanyRepository companyRepository,
                           CompanySettingsRepository companySettingsRepository,
                           UserRepository userRepository, PasswordEncoder passwordEncoder,
                           DepartmentService departmentService, EmployeeService employeeService,
                           ProjectService projectService, SprintService sprintService,
                           TaskService taskService, TicketService ticketService,
                           SpaceService spaceService, PageService pageService,
                           com.calyvora.people.CompensationRepository compensationRepository,
                           com.calyvora.people.GoalRepository goalRepository,
                           com.calyvora.client.ClientService clientService,
                           com.calyvora.document.DocumentService documentService,
                           com.calyvora.people.AttendanceService attendanceService,
                           com.calyvora.people.HolidayService holidayService,
                           com.calyvora.people.HolidayRepository holidayRepository,
                           ExpenseService expenseService, FeedService feedService,
                           com.calyvora.performance.PerformanceReviewService performanceReviewService) {
        this.performanceReviewService = performanceReviewService;
        this.feedService = feedService;
        this.attendanceService = attendanceService;
        this.holidayService = holidayService;
        this.holidayRepository = holidayRepository;
        this.expenseService = expenseService;
        this.compensationRepository = compensationRepository;
        this.goalRepository = goalRepository;
        this.clientService = clientService;
        this.documentService = documentService;
        this.companyRepository = companyRepository;
        this.companySettingsRepository = companySettingsRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.departmentService = departmentService;
        this.employeeService = employeeService;
        this.projectService = projectService;
        this.sprintService = sprintService;
        this.taskService = taskService;
        this.ticketService = ticketService;
        this.spaceService = spaceService;
        this.pageService = pageService;
    }

    /** Credentials the caller (or the UI) can log in with immediately. */
    public record DemoCredentials(String companyName, String email, String password, boolean alreadySeeded) {}

    public DemoCredentials seed() {
        if (userRepository.existsByEmail(OWNER_EMAIL)) {
            log.info("Demo company already seeded — returning existing credentials for {}", OWNER_EMAIL);
            return new DemoCredentials(COMPANY, OWNER_EMAIL, DEMO_PASSWORD, true);
        }

        Company company = provisionCompany();
        User owner = createUser(company.getId(), OWNER_EMAIL, "Ava", "Chen", Role.OWNER);
        User marcus = createUser(company.getId(), "marcus.reed@northwind.demo", "Marcus", "Reed", Role.ADMIN);
        User priya = createUser(company.getId(), "priya.nair@northwind.demo", "Priya", "Nair", Role.MEMBER);
        User leo = createUser(company.getId(), "leo.martins@northwind.demo", "Leo", "Martins", Role.MEMBER);
        User sara = createUser(company.getId(), "sara.okoro@northwind.demo", "Sara", "Okoro", Role.MEMBER);
        User tom = createUser(company.getId(), "tom.becker@northwind.demo", "Tom", "Becker", Role.MEMBER);

        TenantContext.setCompanyId(company.getId());
        try {
            AuthPrincipal principal = new AuthPrincipal(owner.getId(), company.getId(), "OWNER", OWNER_EMAIL);
            seedTenant(principal, marcus, priya, leo, sara, tom);
        } finally {
            TenantContext.clear();
        }

        log.info("Seeded demo company '{}' ({} users) — owner {}", COMPANY, 6, OWNER_EMAIL);
        return new DemoCredentials(COMPANY, OWNER_EMAIL, DEMO_PASSWORD, false);
    }

    private void seedTenant(AuthPrincipal owner, User marcus, User priya, User leo, User sara, User tom) {
        // --- People: departments + profiles ---------------------------------
        DepartmentResponse engineering = departmentService.create(dept("Engineering", marcus.getId()));
        DepartmentResponse design = departmentService.create(dept("Design", leo.getId()));
        DepartmentResponse sales = departmentService.create(dept("Sales", tom.getId()));
        DepartmentResponse support = departmentService.create(dept("Customer Support", sara.getId()));

        Map<String, EmployeeResponse> emp = employeesByEmail();
        String mgrMarcus = emp.get(marcus.getEmail()).id();
        String mgrTom = emp.get(tom.getEmail()).id();
        String mgrAva = emp.get(OWNER_EMAIL).id();
        // Employee numbers and reporting lines are filled in so generated letters (Documents) read
        // like real paperwork rather than a form with gaps.
        profile(emp, OWNER_EMAIL, "NR-001", "Founder & CEO", engineering.id(), null, "2021-01-04",
                java.util.List.of("Leadership", "Product Strategy", "Fundraising"), 5);
        profile(emp, marcus.getEmail(), "NR-002", "Engineering Lead", engineering.id(), mgrAva, "2021-03-15",
                java.util.List.of("Java", "Spring Boot", "System Design", "Postgres"), 5);
        profile(emp, priya.getEmail(), "NR-003", "Senior Software Engineer", engineering.id(), mgrMarcus, "2022-06-01",
                java.util.List.of("TypeScript", "React", "Security", "RS256"), 4);
        profile(emp, leo.getEmail(), "NR-004", "Product Designer", design.id(), mgrAva, "2022-09-12",
                java.util.List.of("Figma", "Design Systems", "Prototyping"), 4);
        profile(emp, sara.getEmail(), "NR-005", "Support Specialist", support.id(), mgrTom, "2023-02-20",
                java.util.List.of("Customer Success", "Zendesk", "Troubleshooting"), 4);
        profile(emp, tom.getEmail(), "NR-006", "Sales Manager", sales.id(), mgrAva, "2021-11-08",
                java.util.List.of("B2B Sales", "Negotiation", "CRM"), 3);

        // Compensation history (initial salary + a review hike) so salary/hikes/payslips look real.
        seedComp(emp, OWNER_EMAIL, 220000, owner);
        seedComp(emp, marcus.getEmail(), 180000, owner);
        seedComp(emp, priya.getEmail(), 145000, owner);
        seedComp(emp, leo.getEmail(), 120000, owner);
        seedComp(emp, sara.getEmail(), 92000, owner);
        seedComp(emp, tom.getEmail(), 135000, owner);

        // A few goals so Performance/Goals looks real.
        seedGoal(emp, marcus.getEmail(), "Complete the RLS rollout across all tenant tables", 80, owner);
        seedGoal(emp, priya.getEmail(), "Ship RS256 key-rotation runbook", 60, owner);
        seedGoal(emp, priya.getEmail(), "Mentor one junior engineer this quarter", 30, owner);
        seedGoal(emp, sara.getEmail(), "Cut average ticket response time to under 2 hours", 45, owner);

        // --- Work: project, active sprint, tasks, tickets -------------------
        ProjectResponse atlas = projectService.create(new CreateProjectRequest(
                "Atlas Platform", "ATL",
                "The core multi-tenant platform: identity, tenancy, and the shared services every app builds on.",
                marcus.getId().toString()));
        UUID atlasId = UUID.fromString(atlas.id());

        SprintResponse sprint = sprintService.create(atlasId, new CreateSprintRequest(
                "Sprint 12 — Security hardening",
                "Ship RS256 tokens and tenant Row-Level Security; polish onboarding.",
                LocalDate.now().minusDays(4).toString(), LocalDate.now().plusDays(10).toString(), 34));
        sprintService.start(UUID.fromString(sprint.id()));

        String eMarcus = emp.get(marcus.getEmail()).id();
        String ePriya = emp.get(priya.getEmail()).id();
        String eLeo = emp.get(leo.getEmail()).id();
        String eSara = emp.get(sara.getEmail()).id();

        // In-sprint work, with realistic progress spread.
        TaskResponse t1 = task(atlasId, owner, "RS256 JWT signing with key rotation", ePriya, "HIGH",
                "Replace the HS256 shared secret with asymmetric RS256 + a JWKS endpoint.", 8);
        inSprint(t1, sprint.id(), "DONE");
        TaskResponse t2 = task(atlasId, owner, "Postgres Row-Level Security for tenant isolation", eMarcus, "URGENT",
                "Enable + force RLS on all tenant tables; bind the tenant per connection.", 13);
        inSprint(t2, sprint.id(), "IN_PROGRESS");
        TaskResponse t3 = task(atlasId, owner, "Onboarding wizard polish", eLeo, "MEDIUM",
                "Tighten the empty states and first-run experience for new tenants.", 5);
        inSprint(t3, sprint.id(), "IN_PROGRESS");
        TaskResponse t4 = task(atlasId, owner, "Rotate signing keys without downtime", ePriya, "MEDIUM",
                "Document and test the kid-based rotation flow.", 5);
        inSprint(t4, sprint.id(), "TODO");

        // Backlog (no sprint) — proves the backlog view.
        task(atlasId, owner, "Full-text search across Knowledge pages", eMarcus, "MEDIUM",
                "tsvector-based search as the retrieval layer for the assistant.", 8);
        task(atlasId, owner, "Universal AI assistant (RAG over the org graph)", eMarcus, "HIGH",
                "Ask questions in plain English, answered from People/Work/Knowledge data.", 13);
        task(atlasId, owner, "Audit log for admin actions", null, "LOW", null);

        ticketService.create(atlasId, ticket("Cannot reset my password", "Priya", eSara, "HIGH",
                "A user reports the reset link 404s after 15 minutes."), owner);
        ticketService.create(atlasId, ticket("Add SSO with Okta", "Enterprise prospect", eSara, "MEDIUM",
                "Prospect requires SAML SSO before signing."), owner);
        ticketService.create(atlasId, ticket("Invoice PDF missing tax line", "Finance", eSara, "LOW",
                "Generated invoices omit the VAT line for EU customers."), owner);

        // --- Knowledge: a handbook that links back into Work ----------------
        SpaceResponse handbook = spaceService.create(new CreateSpaceRequest(
                "Engineering Handbook", "ENG",
                "How we build and run Atlas — architecture, security, and runbooks."), owner);
        UUID spaceId = UUID.fromString(handbook.id());

        page(spaceId, owner, "New Engineer Onboarding", null,
                onboardingBody());
        page(spaceId, owner, "Authentication: RS256, JWKS & Key Rotation", t1.id(),
                authDocBody());
        page(spaceId, owner, "Multi-Tenant Isolation & Row-Level Security", t2.id(),
                rlsDocBody());
        page(spaceId, owner, "Incident Response Runbook", null,
                runbookBody());

        // --- Clients: a few customers + what they've requested --------------
        seedClient(owner, "Globex Corporation", "Hank Scorpio", "hank@globex.com", "ACTIVE",
                new String[]{"SAML SSO with Okta:IN_PROGRESS", "White-label the customer portal:REQUESTED"});
        seedClient(owner, "Initech", "Bill Lumbergh", "bill@initech.com", "LEAD",
                new String[]{"Trial extension to 30 days:DELIVERED", "Bulk CSV employee import:REQUESTED"});
        seedClient(owner, "Umbrella Inc", "Ada Wong", "ada@umbrella.com", "ACTIVE",
                new String[]{"Custom SLA (99.9%):IN_PROGRESS", "Data residency in EU:REQUESTED", "Quarterly business review:DELIVERED"});

        // --- Documents: the starter template library + a couple of issued letters ---
        seedDocuments(owner, emp, List.of(leo.getEmail(), sara.getEmail()));

        // --- Attendance: the last two weeks, so the day sheet and month grid have history ---
        seedAttendance(owner, emp);

        // --- Holidays: a starter calendar plus a couple of near-term ones, so "upcoming" isn't empty ---
        holidayService.seedDefaults(owner);
        holidayRepository.save(new com.calyvora.people.Holiday(UUID.randomUUID(), owner.companyId(),
                "Founders' Day", LocalDate.now().plusDays(9), false, "Offices closed", owner.userId()));
        holidayRepository.save(new com.calyvora.people.Holiday(UUID.randomUUID(), owner.companyId(),
                "Volunteering Day (optional)", LocalDate.now().plusDays(24), true,
                "Take it if you'd like to", owner.userId()));

        // --- Expenses: claims in every state, so the queue and the totals both have something to say ---
        seedExpense(owner, emp, priya.getEmail(), "Client visit — flights to Berlin", "TRAVEL", 48500, null);
        seedExpense(owner, emp, priya.getEmail(), "Team dinner after the launch", "MEALS", 6200, "approve");
        seedExpense(owner, emp, leo.getEmail(), "Figma annual seat", "SUPPLIES", 13800, "reimburse");
        seedExpense(owner, emp, sara.getEmail(), "Support conference ticket", "TRAINING", 22000, null);
        seedExpense(owner, emp, tom.getEmail(), "Hotel — customer QBR", "ACCOMMODATION", 9400, "approve");

        // --- Feed: a few posts so the wall has a voice, including a team-only one ---
        seedFeed(owner, emp, marcus, priya, engineering.id());

        // --- Performance: an annual cycle mid-flight, so the review loop has something to show ---
        seedReviews(owner, emp, priya, marcus, sara, tom);
    }

    /**
     * An annual review cycle in progress: Priya's manager has submitted her review with a hike (it
     * sits in the owner's approval queue), and Sara's is fully approved — the raise already landed in
     * compensation. The rest stay pending, so the "still to do" state is visible too.
     */
    private void seedReviews(AuthPrincipal owner, Map<String, EmployeeResponse> emp,
                             User priya, User marcus, User sara, User tom) {
        LocalDate end = LocalDate.now().minusDays(1);
        LocalDate start = end.minusYears(1).plusDays(1);
        var cycle = performanceReviewService.createCycle(
                new com.calyvora.performance.dto.CreateCycleRequest(
                        "Annual Review " + start.getYear(), start.toString(), end.toString()),
                owner);
        UUID cycleId = UUID.fromString(cycle.id());

        java.util.Map<String, String> reviewByEmployee = new java.util.HashMap<>();
        for (var r : performanceReviewService.cycleReviews(cycleId)) {
            reviewByEmployee.put(r.employeeId(), r.id());
        }

        // Priya (→ Marcus): self submitted, manager submitted a 12% hike → awaits owner approval.
        UUID priyaReview = UUID.fromString(reviewByEmployee.get(emp.get(priya.getEmail()).id()));
        performanceReviewService.saveSelf(priyaReview,
                new com.calyvora.performance.dto.SelfAssessmentRequest(
                        "Led the RS256 rollout and wrote the key-rotation runbook; mentored a junior engineer. "
                                + "Shipped the security work ahead of schedule.", true),
                principalFor(owner, emp, priya.getEmail()));
        performanceReviewService.saveManager(priyaReview,
                new com.calyvora.performance.dto.ManagerReviewRequest(5,
                        "Outstanding year — owned security end to end and lifted the whole team with her.",
                        "Security depth, ownership, mentoring", "Delegate more of the on-call load",
                        "PERCENT", new java.math.BigDecimal("12"), null,
                        "Top performer this cycle; strong retention case.", true),
                principalFor(owner, emp, marcus.getEmail()));

        // Sara (→ Tom): self + manager submitted, and the owner approved — an 8% raise lands in comp.
        UUID saraReview = UUID.fromString(reviewByEmployee.get(emp.get(sara.getEmail()).id()));
        performanceReviewService.saveSelf(saraReview,
                new com.calyvora.performance.dto.SelfAssessmentRequest(
                        "Cut average ticket response time and kept CSAT high through the launch spike.", true),
                principalFor(owner, emp, sara.getEmail()));
        performanceReviewService.saveManager(saraReview,
                new com.calyvora.performance.dto.ManagerReviewRequest(4,
                        "Reliable and calm under load; customers genuinely like working with Sara.",
                        "Responsiveness, empathy", "Start owning the support knowledge base",
                        "PERCENT", new java.math.BigDecimal("8"), null, "Steady, dependable contributor.", true),
                principalFor(owner, emp, tom.getEmail()));
        performanceReviewService.approve(saraReview, owner);
    }

    /** A short, believable wall: an announcement, a birthday, a question, and one team-only post. */
    private void seedFeed(AuthPrincipal owner, Map<String, EmployeeResponse> emp, User marcus, User priya,
                          String engineeringId) {
        var pinned = feedService.create(new PostPayload(
                "We hit 100 customers this month. Thank you, all of you — this is the whole team's win. 🎉",
                "ANNOUNCEMENT", "COMPANY", null), owner);
        feedService.setPinned(UUID.fromString(pinned.id()), true, owner);

        feedService.create(new PostPayload(
                "Happy birthday, Sara! Cake in the kitchen at 4pm 🎂", "CELEBRATION", "COMPANY", null),
                principalFor(owner, emp, marcus.getEmail()));

        feedService.create(new PostPayload(
                "Does anyone have the latest customer onboarding deck? Can't find it in Knowledge.",
                "QUESTION", "COMPANY", null), principalFor(owner, emp, priya.getEmail()));

        feedService.create(new PostPayload(
                "Engineering only: RLS rollout is done on all tenant tables. Please re-run your local migrations.",
                "UPDATE", "DEPARTMENT", engineeringId), principalFor(owner, emp, marcus.getEmail()));
    }

    /** Acts as another member — services read the principal to decide authorship. */
    private AuthPrincipal principalFor(AuthPrincipal owner, Map<String, EmployeeResponse> emp, String email) {
        EmployeeResponse e = emp.get(email);
        return new AuthPrincipal(UUID.fromString(e.userId()), owner.companyId(), e.role(), e.email());
    }

    /**
     * Submits a claim <em>as the employee</em> — the service reads the principal to decide whose claim
     * it is — then optionally walks it forward to approved or reimbursed as the owner.
     */
    private void seedExpense(AuthPrincipal owner, Map<String, EmployeeResponse> emp, String email,
                             String title, String category, long amount, String advanceTo) {
        EmployeeResponse e = emp.get(email);
        if (e == null) {
            return;
        }
        AuthPrincipal claimant = new AuthPrincipal(UUID.fromString(e.userId()), owner.companyId(),
                e.role(), e.email());
        ExpenseResponse claim = expenseService.submit(new ExpensePayload(
                title, category, java.math.BigDecimal.valueOf(amount), "INR",
                LocalDate.now().minusDays(5).toString(), null, null), claimant);

        if (advanceTo == null) {
            return;
        }
        expenseService.decide(UUID.fromString(claim.id()), true, null, owner);
        if ("reimburse".equals(advanceTo)) {
            expenseService.reimburse(UUID.fromString(claim.id()), owner);
        }
    }

    /**
     * Marks the last 14 days for everyone. Mostly present with a believable scatter of WFH, a half
     * day and one absence — a demo where everybody is perfectly present looks fake, and the month
     * summary needs variety to show anything.
     */
    private void seedAttendance(AuthPrincipal owner, Map<String, EmployeeResponse> emp) {
        LocalDate today = LocalDate.now();
        int person = 0;
        for (EmployeeResponse e : emp.values()) {
            person++;
            for (int back = 14; back >= 0; back--) {
                LocalDate date = today.minusDays(back);
                if (date.getDayOfWeek() == java.time.DayOfWeek.SATURDAY
                        || date.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
                    continue;   // weekends resolve to WEEK_OFF on their own
                }
                int mix = (person * 7 + back) % 11;
                String status = switch (mix) {
                    case 0, 1 -> "WORK_FROM_HOME";
                    case 2 -> back == 3 ? "HALF_DAY" : "PRESENT";
                    case 5 -> back == 6 ? "ABSENT" : "PRESENT";
                    default -> "PRESENT";
                };
                String in = status.equals("HALF_DAY") ? "09:15" : (mix % 2 == 0 ? "09:05" : "09:40");
                String out = status.equals("HALF_DAY") ? "13:30" : (mix % 2 == 0 ? "18:10" : "18:45");
                boolean off = status.equals("ABSENT");
                attendanceService.mark(new com.calyvora.people.dto.MarkAttendanceRequest(
                        e.id(), date.toString(), status, off ? null : in, off ? null : out,
                        off ? "Unplanned absence" : null), owner);
            }
        }
    }

    /**
     * Seeds the starter templates (by listing them — the same first-open path a real company takes)
     * and issues a joining letter for a couple of recent hires, so the module opens with history.
     */
    private void seedDocuments(AuthPrincipal owner, Map<String, EmployeeResponse> emp, List<String> emails) {
        var templates = documentService.listTemplates(owner);
        var joining = templates.stream()
                .filter(t -> "JOINING_LETTER".equals(t.kind())).findFirst().orElse(null);
        if (joining == null) {
            return;
        }
        for (String email : emails) {
            EmployeeResponse e = emp.get(email);
            if (e == null) {
                continue;
            }
            documentService.generate(new com.calyvora.document.dto.GenerateRequest(
                    joining.id(), e.id(), null, Map.of()), owner);
        }
    }

    private void seedClient(AuthPrincipal owner, String name, String contact, String email, String status, String[] requests) {
        var client = clientService.create(
                new com.calyvora.client.dto.ClientPayload(name, contact, email, null, null, status, null), owner);
        UUID clientId = UUID.fromString(client.id());
        for (String r : requests) {
            int i = r.lastIndexOf(':');
            clientService.addRequest(clientId, new com.calyvora.client.dto.ClientRequestPayload(
                    r.substring(0, i), null, r.substring(i + 1)));
        }
    }

    // ---- provisioning primitives ------------------------------------------

    private Company provisionCompany() {
        Company company = new Company(UUID.randomUUID(), COMPANY, uniqueSlug(COMPANY), CompanyStatus.ACTIVE);
        companyRepository.save(company);
        companySettingsRepository.save(new CompanySettings(company.getId()));
        return company;
    }

    private User createUser(UUID companyId, String email, String first, String last, Role role) {
        User user = new User(UUID.randomUUID(), companyId, email, first, last, role, UserStatus.ACTIVE);
        user.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
        user.setEmailVerifiedAt(Instant.now());
        return userRepository.save(user);
    }

    private Map<String, EmployeeResponse> employeesByEmail() {
        Map<String, EmployeeResponse> byEmail = new LinkedHashMap<>();
        for (EmployeeResponse e : employeeService.directory()) {   // auto-provisions profiles
            byEmail.put(e.email(), e);
        }
        return byEmail;
    }

    private void profile(Map<String, EmployeeResponse> emp, String email, String employeeNo, String title,
                         String departmentId, String managerId, String startDate,
                         java.util.List<String> skills, Integer rating) {
        EmployeeResponse e = emp.get(email);
        employeeService.update(UUID.fromString(e.id()), new UpdateEmployeeRequest(
                employeeNo, title, "FULL_TIME", "ACTIVE", managerId, departmentId, "Remote", null, startDate,
                null, skills, rating));
    }

    private void seedComp(Map<String, EmployeeResponse> emp, String email, long currentAnnual, AuthPrincipal owner) {
        UUID employeeId = UUID.fromString(emp.get(email).id());
        long initial = Math.round(currentAnnual / 1.11);   // ~11% review hike a year ago
        compensationRepository.save(new com.calyvora.people.CompensationRecord(
                UUID.randomUUID(), owner.companyId(), employeeId, LocalDate.now().minusYears(2),
                java.math.BigDecimal.valueOf(initial), "USD",
                com.calyvora.people.CompensationChangeType.INITIAL, "Starting salary", owner.userId()));
        compensationRepository.save(new com.calyvora.people.CompensationRecord(
                UUID.randomUUID(), owner.companyId(), employeeId, LocalDate.now().minusYears(1),
                java.math.BigDecimal.valueOf(currentAnnual), "USD",
                com.calyvora.people.CompensationChangeType.HIKE, "Annual review raise", owner.userId()));
    }

    private void seedGoal(Map<String, EmployeeResponse> emp, String email, String title, int progress, AuthPrincipal owner) {
        UUID employeeId = UUID.fromString(emp.get(email).id());
        com.calyvora.people.Goal g = new com.calyvora.people.Goal(
                UUID.randomUUID(), owner.companyId(), employeeId, title, null,
                LocalDate.now().plusMonths(2), owner.userId());
        g.setProgress(progress);
        goalRepository.save(g);
    }

    private static CreateDepartmentRequest dept(String name, UUID leadUserId) {
        return new CreateDepartmentRequest(name, null, leadUserId.toString());
    }

    private TaskResponse task(UUID projectId, AuthPrincipal owner, String title, String assigneeId,
                              String priority, String description) {
        return task(projectId, owner, title, assigneeId, priority, description, null);
    }

    /** Overload that sizes the task, so velocity and the burndown have real numbers to work with. */
    private TaskResponse task(UUID projectId, AuthPrincipal owner, String title, String assigneeId,
                              String priority, String description, Integer storyPoints) {
        return taskService.create(projectId,
                new CreateTaskRequest(title, description, priority, assigneeId, null, storyPoints), owner);
    }

    private void inSprint(TaskResponse task, String sprintId, String status) {
        taskService.update(UUID.fromString(task.id()),
                new UpdateTaskRequest(null, null, status, null, null, sprintId, null, null));
    }

    private static CreateTicketRequest ticket(String subject, String requester, String assigneeId,
                                              String priority, String description) {
        return new CreateTicketRequest(subject, description, requester, null, priority, assigneeId);
    }

    private PageResponse page(UUID spaceId, AuthPrincipal owner, String title, String linkedTaskId, String body) {
        return pageService.create(spaceId, new CreatePageRequest(title, body, null, linkedTaskId), owner);
    }

    private String uniqueSlug(String name) {
        String base = Slugs.slugify(name);
        String slug = base;
        int n = 2;
        while (companyRepository.existsBySlug(slug)) {
            slug = base + "-" + n++;
        }
        return slug;
    }

    // ---- page bodies (Markdown) -------------------------------------------

    private static String onboardingBody() {
        return """
                # Welcome to Northwind Robotics 👋

                Glad you're here. This guide gets you productive in your first week.

                ## Day 1
                - Get your laptop and sign in to **Atlas**.
                - Read the [architecture principles](#) and the security docs in this space.
                - Say hi in the team channel.

                ## Your first week
                1. Pair with your buddy on a starter task in the **Atlas Platform** project.
                2. Ship one small PR — however tiny.
                3. Book 1:1s with your manager and the leads.

                > We optimize for **depth over breadth**. Build the foundation once, deeply.
                """;
    }

    private static String authDocBody() {
        return """
                # Authentication: RS256, JWKS & Key Rotation

                Atlas issues short-lived access tokens signed with **RS256** (asymmetric). Verifiers hold
                only the public key — there is no shared signing secret to leak.

                ## Key rotation
                Every key has a `kid`. One key is *active* for signing; all configured keys stay trusted
                for verification. To rotate with zero downtime:
                1. Publish the new public key.
                2. Flip the active `kid`.
                3. Retire the old key once its last token has expired.

                Public keys are discoverable at `/.well-known/jwks.json` (RFC 7517).
                """;
    }

    private static String rlsDocBody() {
        return """
                # Multi-Tenant Isolation & Row-Level Security

                Isolation is defense-in-depth. The application always filters by `company_id`, **and**
                Postgres **Row-Level Security** enforces it beneath the app so a forgotten filter can't
                leak another tenant's data.

                ## How it works
                - Every request binds its tenant to the connection as the `calyvora.company_id` GUC.
                - Each tenant table has a policy: a row is visible only when its `company_id` matches.
                - No tenant bound ⇒ **deny by default** (nothing is visible).

                > The app's database role must be `NOSUPERUSER` — superusers bypass RLS by design.
                """;
    }

    private static String runbookBody() {
        return """
                # Incident Response Runbook

                ## Severity levels
                - **SEV-1** — customer-facing outage. Page on-call immediately.
                - **SEV-2** — degraded experience; fix within the business day.
                - **SEV-3** — minor; schedule in the next sprint.

                ## First 15 minutes
                1. Acknowledge and open an incident channel.
                2. Assign an incident commander.
                3. Post a status update every 30 minutes until resolved.
                4. Write the post-mortem within 48 hours — blameless.
                """;
    }
}
