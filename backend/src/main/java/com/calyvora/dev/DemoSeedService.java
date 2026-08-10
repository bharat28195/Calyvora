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
    // The platform owner is not seeded here any more — PlatformOwnerBootstrap creates it at startup so
    // it exists in prod too, where this dev-only seeder never runs.
    private static final String AGENCY_OWNER_EMAIL = "owner@vertexgroup.demo";

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
    private final com.calyvora.recruit.RecruitService recruitService;
    private final com.calyvora.shift.ShiftService shiftService;
    private final com.calyvora.platform.PlatformService platformService;
    private final com.calyvora.billing.SubscriptionRepository subscriptionRepository;
    private final com.calyvora.platform.SeatRequestRepository seatRequestRepository;
    private final com.calyvora.helpdesk.HelpdeskService helpdeskService;
    private final com.calyvora.people.RegularizationService regularizationService;
    private final com.calyvora.people.EmployeeFinanceService employeeFinanceService;

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
                           com.calyvora.performance.PerformanceReviewService performanceReviewService,
                           com.calyvora.recruit.RecruitService recruitService,
                           com.calyvora.shift.ShiftService shiftService,
                           com.calyvora.platform.PlatformService platformService,
                           com.calyvora.billing.SubscriptionRepository subscriptionRepository,
                           com.calyvora.platform.SeatRequestRepository seatRequestRepository,
                           com.calyvora.helpdesk.HelpdeskService helpdeskService,
                           com.calyvora.people.RegularizationService regularizationService,
                           com.calyvora.people.EmployeeFinanceService employeeFinanceService) {
        this.employeeFinanceService = employeeFinanceService;
        this.helpdeskService = helpdeskService;
        this.regularizationService = regularizationService;
        this.performanceReviewService = performanceReviewService;
        this.recruitService = recruitService;
        this.shiftService = shiftService;
        this.platformService = platformService;
        this.subscriptionRepository = subscriptionRepository;
        this.seatRequestRepository = seatRequestRepository;
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
            log.info("Demo company already seeded — topping up any newly-added demo data for {}", OWNER_EMAIL);
            topUpExistingDemo();
            return new DemoCredentials(COMPANY, OWNER_EMAIL, DEMO_PASSWORD, true);
        }

        Company company = provisionCompany();
        // Ava runs Northwind as its ADMIN — OWNER is now the platform vendor above all companies (PD-10).
        User owner = createUser(company.getId(), OWNER_EMAIL, "Ava", "Chen", Role.ADMIN);
        User marcus = createUser(company.getId(), "marcus.reed@northwind.demo", "Marcus", "Reed", Role.ADMIN);
        User priya = createUser(company.getId(), "priya.nair@northwind.demo", "Priya", "Nair", Role.MEMBER);
        User leo = createUser(company.getId(), "leo.martins@northwind.demo", "Leo", "Martins", Role.HR);
        User sara = createUser(company.getId(), "sara.okoro@northwind.demo", "Sara", "Okoro", Role.MEMBER);
        User tom = createUser(company.getId(), "tom.becker@northwind.demo", "Tom", "Becker", Role.MANAGER);

        TenantContext.setCompanyId(company.getId());
        try {
            AuthPrincipal principal = new AuthPrincipal(owner.getId(), company.getId(), "OWNER", OWNER_EMAIL);
            seedTenant(principal, marcus, priya, leo, sara, tom);
        } finally {
            TenantContext.clear();
        }

        // Northwind's own subscription, so it shows up live in the owner console. Sold direct, so it
        // has no agency — the vendor account itself is bootstrapped at startup, not seeded.
        activeSubscription(company.getId(), 10, 10);

        log.info("Seeded demo company '{}' ({} users) — owner {}", COMPANY, 6, OWNER_EMAIL);
        return new DemoCredentials(COMPANY, OWNER_EMAIL, DEMO_PASSWORD, false);
    }

    /**
     * Bring an already-seeded demo up to date with demo content added since it was first seeded.
     *
     * <p>Without this, a long-lived environment silently misses every new feature's sample data: the
     * seeder short-circuits on "already seeded", so payslip branding and the finance records added in
     * V34 would stay blank on exactly the deployment being demoed.
     *
     * <p>Only ever <em>fills gaps</em> — anything already set is left alone, so re-seeding can't
     * overwrite edits made by hand while testing.
     */
    private void topUpExistingDemo() {
        User owner = userRepository.findByEmail(OWNER_EMAIL).orElse(null);
        if (owner == null) {
            return;
        }
        UUID companyId = owner.getCompanyId();
        TenantContext.setCompanyId(companyId);
        try {
            // Payslip branding — only if the company hasn't set its own.
            companySettingsRepository.findById(companyId).ifPresent(settings -> {
                boolean changed = false;
                if (isBlank(settings.getLegalName())) {
                    settings.setLegalName("Northwind Robotics Private Limited");
                    changed = true;
                }
                if (isBlank(settings.getAddress())) {
                    settings.setAddress("704-705, Sankalp Square 3A, Beside Taj Skyline Hotel, "
                            + "PRL Colony, Thaltej, Ahmedabad, Gujarat, 380059.");
                    changed = true;
                }
                if (changed) {
                    companySettingsRepository.save(settings);
                }
            });

            // Bank / PF / ESI / PAN for the demo staff, if they have none yet.
            Map<String, EmployeeResponse> emp = employeesByEmail();
            boolean anyMissing = emp.values().stream().anyMatch(e ->
                    employeeFinanceService.rawOrNull(UUID.fromString(e.id())) == null
                            || isBlank(employeeFinanceService.rawOrNull(UUID.fromString(e.id())).getBankName()));
            if (anyMissing) {
                seedFinance(emp);
            }
        } finally {
            TenantContext.clear();
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * One sample agency with two companies under it, so the owner console shows both ways of selling:
     * five companies sold direct, and a group whose companies sit underneath it. Idempotent.
     *
     * <p>The platform owner itself is no longer seeded here — it is bootstrapped at startup by
     * {@code PlatformOwnerBootstrap}, so it exists in prod too, where this seeder does not run.
     */
    private void seedSampleAgency(java.util.List<String> logins) {
        if (userRepository.existsByEmail(AGENCY_OWNER_EMAIL)) {
            return;
        }
        var agency = platformService.createAgency(new com.calyvora.platform.dto.CreateAgencyRequest(
                "Vertex Group", "Meera", "Kapoor", AGENCY_OWNER_EMAIL, DEMO_PASSWORD));
        UUID agencyId = UUID.fromString(agency.agencyId());

        record Member(String name, String email, String first, String last, int seats) {}
        for (Member m : java.util.List.of(
                new Member("Vertex Labs", "admin@vertexlabs.demo", "Ishaan", "Bose", 10),
                new Member("Meridian Care", "admin@meridian.demo", "Tara", "Menon", 6))) {
            var company = platformService.createCompany(new com.calyvora.platform.dto.CreateCompanyRequest(
                    m.name(), m.first(), m.last(), m.email(), DEMO_PASSWORD, m.seats(), 6,
                    agencyId.toString()));
            UUID cid = UUID.fromString(company.companyId());
            for (int i = 1; i <= Math.max(1, m.seats() - 4); i++) {
                createUser(cid, "emp" + i + "@" + company.slug() + ".demo", "Employee",
                        String.valueOf(i), Role.MEMBER);
            }
            logins.add(m.email() + " / " + DEMO_PASSWORD + "  (" + m.name() + ", admin — under Vertex Group)");
        }
        logins.add(AGENCY_OWNER_EMAIL + " / " + DEMO_PASSWORD + "  (Vertex Group, agency owner)");
        log.info("Seeded sample agency Vertex Group with 2 companies");
    }

    /** Give a company an ACTIVE subscription with a seat limit ending {@code months} out. */
    private void activeSubscription(UUID companyId, int seats, int months) {
        com.calyvora.billing.Subscription sub = subscriptionRepository.findByCompanyId(companyId)
                .orElseGet(() -> new com.calyvora.billing.Subscription(UUID.randomUUID(), companyId,
                        java.math.BigDecimal.valueOf(100), "INR", null));
        sub.setStatus(com.calyvora.billing.SubscriptionStatus.ACTIVE);
        sub.setStartedAt(Instant.now());
        sub.setSeats(seats);
        sub.setEndsAt(LocalDate.now().plusMonths(months));
        subscriptionRepository.save(sub);
    }

    /**
     * Provision 5 sample customer companies with varied states so the owner console tells a story:
     * healthy, near-expiry (triggers the admin's renewal nudge), near seat-limit, one with a pending
     * seat request to approve, and one whose subscription has ended (its app is locked). Idempotent.
     *
     * <p>Plus one agency running two more companies, so the console shows both ways of selling at
     * once: direct customers standing alone, and a group with its companies underneath.
     */
    public java.util.List<String> seedPlatformSamples() {
        java.util.List<String> logins = new java.util.ArrayList<>();
        seedSampleAgency(logins);
        if (companyRepository.existsBySlug("acme-logistics")) {
            return logins; // already seeded
        }
        // name, adminEmail, first, last, seats, months, members, extra
        record Spec(String name, String email, String first, String last, int seats, int months,
                    int members, int price, String extra) {}
        java.util.List<Spec> specs = java.util.List.of(
                new Spec("Acme Logistics", "admin@acme.demo", "Arjun", "Mehta", 12, 8, 8, 100, "ok"),
                new Spec("Verdant Foods", "admin@verdant.demo", "Divya", "Rao", 15, 0, 11, 150, "expiring"),
                new Spec("Sterling Finance", "admin@sterling.demo", "Kabir", "Shah", 25, 6, 23, 300, "full"),
                new Spec("Lumen Studios", "admin@lumen.demo", "Nisha", "Iyer", 10, 5, 4, 200, "request"),
                new Spec("Orbit Retail", "admin@orbit.demo", "Rohan", "Gupta", 8, 6, 6, 100, "ended"));

        for (Spec s : specs) {
            // Sold direct — no agency. The agency-run companies are seeded separately below, so the
            // console shows both kinds side by side, which is how it will really look.
            var summary = platformService.createCompany(new com.calyvora.platform.dto.CreateCompanyRequest(
                    s.name(), s.first(), s.last(), s.email(), DEMO_PASSWORD, s.seats(),
                    Math.max(1, s.months()), null));
            UUID cid = UUID.fromString(summary.companyId());
            String slug = summary.slug();
            platformService.setPrice(cid, java.math.BigDecimal.valueOf(s.price()));
            for (int i = 1; i <= s.members(); i++) {
                createUser(cid, "emp" + i + "@" + slug + ".demo", "Employee", String.valueOf(i), Role.MEMBER);
            }
            switch (s.extra()) {
                case "expiring" -> subscriptionRepository.findByCompanyId(cid).ifPresent(sub -> {
                    sub.setEndsAt(LocalDate.now().plusDays(9));
                    subscriptionRepository.save(sub);
                });
                case "request" -> seatRequestRepository.save(new com.calyvora.platform.SeatRequest(
                        UUID.randomUUID(), cid, 20, "Hiring 6 more this quarter — need seats."));
                case "ended" -> platformService.endSubscription(cid);
                default -> { /* healthy */ }
            }
            logins.add(s.email() + " / " + DEMO_PASSWORD + "  (" + s.name() + ", admin)");
        }
        log.info("Seeded {} sample companies for the owner console", specs.size());
        return logins;
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

        // Bank / PF / ESI / PAN, so "My Finances" and the payslip header aren't a page of dashes.
        seedFinance(emp);

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

        // Two finished sprints before the current one, so the velocity chart has real history. These
        // must be created and completed before Sprint 12 starts (a project has one active sprint at a time).
        seedCompletedSprint(atlasId, owner, "Sprint 10 — Onboarding revamp",
                LocalDate.now().minusDays(46), LocalDate.now().minusDays(32),
                emp.get(priya.getEmail()).id(), 8, 5, 8);
        seedCompletedSprint(atlasId, owner, "Sprint 11 — Billing & invoices",
                LocalDate.now().minusDays(32), LocalDate.now().minusDays(18),
                emp.get(marcus.getEmail()).id(), 13, 8, 5, 3);

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

        // --- Recruitment: a couple of open roles with candidates spread across the pipeline ---
        seedRecruitment(owner, engineering.id(), design.id());

        // --- Shifts: a few shift templates + this week's roster for the support team ---
        seedShifts(emp, sara, tom);

        // --- Helpdesk: a few tickets across statuses, with a reply and a resolution ---
        seedHelpdesk(owner, emp, priya, sara, tom, leo);

        // --- Regularization: Sara forgot to punch a recent day; pending for her manager Tom ---
        LocalDate missed = LocalDate.now().minusDays(LocalDate.now().getDayOfWeek().getValue() >= 6 ? 3 : 1);
        regularizationService.raise(new com.calyvora.people.dto.RegularizationRequest(
                missed.toString(), "09:30", "18:15", "Forgot to check in — was in office all day."),
                principalFor(owner, emp, sara.getEmail()));
    }

    /**
     * A handful of helpdesk tickets raised by employees, spread across statuses — one fresh, one being
     * worked with an HR reply, and one resolved — so the queue and thread open with a real story.
     */
    private void seedHelpdesk(AuthPrincipal owner, Map<String, EmployeeResponse> emp,
                              User priya, User sara, User tom, User leo) {
        AuthPrincipal pPriya = principalFor(owner, emp, priya.getEmail());
        AuthPrincipal pSara = principalFor(owner, emp, sara.getEmail());
        AuthPrincipal pTom = principalFor(owner, emp, tom.getEmail());
        AuthPrincipal pLeo = principalFor(owner, emp, leo.getEmail());   // HR agent
        String leoUserId = emp.get(leo.getEmail()).userId();

        // 1) Fresh, unassigned.
        helpdeskService.raise(new com.calyvora.helpdesk.dto.RaiseTicketRequest(
                "IT", "Laptop won't connect to office Wi-Fi", "Since this morning my laptop keeps dropping the AsInt-Secure network.", "HIGH"), pTom);

        // 2) In progress, assigned to HR, with a back-and-forth.
        var payroll = helpdeskService.raise(new com.calyvora.helpdesk.dto.RaiseTicketRequest(
                "PAYROLL", "PF not reflecting in last payslip", "My July payslip doesn't show the provident fund deduction.", "MEDIUM"), pPriya);
        UUID payrollId = UUID.fromString(payroll.id());
        helpdeskService.update(payrollId, new com.calyvora.helpdesk.dto.UpdateTicketRequest(
                "IN_PROGRESS", leoUserId, null, null), pLeo);
        helpdeskService.addComment(payrollId, new com.calyvora.helpdesk.dto.CommentPayload(
                "Thanks Priya — checking with finance, will update you by EOD."), pLeo);
        helpdeskService.addComment(payrollId, new com.calyvora.helpdesk.dto.CommentPayload(
                "Appreciate it!"), pPriya);

        // 3) Resolved.
        var hr = helpdeskService.raise(new com.calyvora.helpdesk.dto.RaiseTicketRequest(
                "HR", "Need employment verification letter", "Applying for a home loan — need a verification letter addressed to the bank.", "LOW"), pSara);
        UUID hrId = UUID.fromString(hr.id());
        helpdeskService.update(hrId, new com.calyvora.helpdesk.dto.UpdateTicketRequest(
                "IN_PROGRESS", leoUserId, null, null), pLeo);
        helpdeskService.addComment(hrId, new com.calyvora.helpdesk.dto.CommentPayload(
                "Generated and emailed to you — closing this out."), pLeo);
        helpdeskService.update(hrId, new com.calyvora.helpdesk.dto.UpdateTicketRequest(
                "RESOLVED", null, null, null), pLeo);
    }

    /**
     * Shift templates plus a rostered current week for the customer-support team, so the roster grid
     * opens with real coverage rather than an empty timetable.
     */
    private void seedShifts(Map<String, EmployeeResponse> emp, User sara, User tom) {
        var morning = shiftService.createShift(
                new com.calyvora.shift.dto.ShiftPayload("Morning", "09:00", "17:00", "#22d3ee"));
        var evening = shiftService.createShift(
                new com.calyvora.shift.dto.ShiftPayload("Evening", "13:00", "21:00", "#8b5cf6"));
        shiftService.createShift(
                new com.calyvora.shift.dto.ShiftPayload("Night", "21:00", "05:00", "#fbbf24"));

        UUID saraId = UUID.fromString(emp.get(sara.getEmail()).id());
        UUID tomId = UUID.fromString(emp.get(tom.getEmail()).id());
        LocalDate monday = LocalDate.now().minusDays(LocalDate.now().getDayOfWeek().getValue() - 1L);
        // Sara covers mornings Mon–Wed; Tom the evenings Mon–Fri, so both rows and several columns fill in.
        for (int d = 0; d < 3; d++) {
            shiftService.assign(saraId, monday.plusDays(d), UUID.fromString(morning.id()));
        }
        for (int d = 0; d < 5; d++) {
            shiftService.assign(tomId, monday.plusDays(d), UUID.fromString(evening.id()));
        }
    }

    /** Two open roles with candidates at different pipeline stages, so the ATS board looks alive. */
    private void seedRecruitment(AuthPrincipal owner, String engineeringId, String designId) {
        var eng = recruitService.createJob(new com.calyvora.recruit.dto.JobOpeningPayload(
                "Senior Backend Engineer", engineeringId, "Bangalore / Remote", "FULL_TIME",
                "Own core platform services: identity, tenancy, and the APIs every app builds on.", 2, "OPEN"), owner);
        candidate(eng.id(), "Aditya Rao", "aditya.rao@example.com", "INTERVIEW", 4, "LinkedIn");
        candidate(eng.id(), "Meera Krishnan", "meera.k@example.com", "SCREENING", 4, "Referral");
        candidate(eng.id(), "Daniel Cho", "daniel.cho@example.com", "APPLIED", null, "Careers page");
        candidate(eng.id(), "Fatima Sheikh", "fatima.s@example.com", "OFFER", 5, "Referral");
        candidate(eng.id(), "Rohit Verma", "rohit.v@example.com", "REJECTED", 2, "Careers page");

        var design = recruitService.createJob(new com.calyvora.recruit.dto.JobOpeningPayload(
                "Product Designer", designId, "Remote (India)", "FULL_TIME",
                "Shape the product's look and feel end to end — from flows to a polished design system.", 1, "OPEN"), owner);
        candidate(design.id(), "Nadia Osei", "nadia.osei@example.com", "HIRED", 5, "Portfolio");
        candidate(design.id(), "Sam Patel", "sam.patel@example.com", "INTERVIEW", 3, "Dribbble");
        candidate(design.id(), "Grace Lin", "grace.lin@example.com", "APPLIED", null, "Careers page");
    }

    private void candidate(String jobId, String name, String email, String stage, Integer rating, String source) {
        recruitService.addCandidate(java.util.UUID.fromString(jobId),
                new com.calyvora.recruit.dto.CandidatePayload(name, email, null, null, source, stage, rating, null));
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
            // Seed history up to *yesterday* and leave today open, so the demo can show a real
            // check-in → check-out on today's date instead of finding the day already filled.
            for (int back = 14; back >= 1; back--) {
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

        // Branded out of the box, so a demo payslip looks like a real document rather than a form
        // with the fields left blank.
        CompanySettings settings = new CompanySettings(company.getId());
        settings.setLegalName("Northwind Robotics Private Limited");
        settings.setAddress("704-705, Sankalp Square 3A, Beside Taj Skyline Hotel, "
                + "PRL Colony, Thaltej, Ahmedabad, Gujarat, 380059.");
        companySettingsRepository.save(settings);
        return company;
    }

    /**
     * Bank / statutory / identity records for the demo staff, so "My Finances" and the payslip
     * header have something real to show. Values are obviously fictional but correctly shaped —
     * a UAN is 12 digits and a PAN matches its format, so the validation rules are exercised too.
     */
    private void seedFinance(Map<String, EmployeeResponse> emp) {
        finance(emp, "ava.chen@northwind.demo", "HDFC Bank", "50100424268412", "HDFC0003939",
                "ENABLED", "GJVAT35530670000010105", "101794989961", "2021-01-04",
                "AVCPC1234A", "1988-04-12", "Wei Chen");
        finance(emp, "leo.martins@northwind.demo", "ICICI Bank", "002401527788", "ICIC0000024",
                "ENABLED", "GJVAT35530670000010106", "101794989962", "2022-03-15",
                "BXTPM5678B", "1990-09-02", "Rosa Martins");
        finance(emp, "tom.becker@northwind.demo", "Axis Bank", "918010049211334", "UTIB0001234",
                "ENABLED", "GJVAT35530670000010107", "101794989963", "2022-07-01",
                "CQRPB9012C", "1987-11-23", "Hans Becker");
        finance(emp, "sara.okoro@northwind.demo", "State Bank of India", "38240015566", "SBIN0011513",
                "NOT_ELIGIBLE", null, null, null,
                "DLMPO3456D", "1996-01-28", "Chidi Okoro");
    }

    private void finance(Map<String, EmployeeResponse> emp, String email, String bank, String account,
                         String ifsc, String pfStatus, String pfNumber, String uan, String pfJoinDate,
                         String pan, String dob, String parent) {
        EmployeeResponse e = emp.get(email);
        if (e == null) {
            return;
        }
        UUID employeeId = UUID.fromString(e.id());
        employeeFinanceService.update(employeeId, new com.calyvora.people.dto.UpdateEmployeeFinanceRequest(
                "BANK_TRANSFER", bank, account, ifsc, e.firstName() + " " + e.lastName(), "Thaltej",
                // No honorific — the seeder has no idea what any of these people use.
                pfStatus, pfNumber, uan, pfJoinDate, (e.firstName() + " " + e.lastName()).toUpperCase(),
                "NOT_ELIGIBLE", null,
                "Gujarat", "Gujarat",
                pan, true, dob, parent));
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

    /** A finished sprint whose done, sized tasks give the velocity chart real bars to plot. */
    private void seedCompletedSprint(UUID projectId, AuthPrincipal owner, String name,
                                     LocalDate start, LocalDate end, String assigneeId, int... points) {
        int capacity = 0;
        for (int p : points) capacity += p;
        SprintResponse s = sprintService.create(projectId,
                new CreateSprintRequest(name, null, start.toString(), end.toString(), capacity));
        sprintService.start(UUID.fromString(s.id()));
        int i = 1;
        for (int p : points) {
            TaskResponse t = task(projectId, owner, name + " · task " + i++, assigneeId, "MEDIUM", null, p);
            inSprint(t, s.id(), "DONE");
        }
        sprintService.complete(UUID.fromString(s.id()));
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
