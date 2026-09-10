package com.calyvora.people;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.TenantContext;
import com.calyvora.identity.User;
import com.calyvora.identity.UserRepository;
import com.calyvora.people.dto.EmployeeResponse;
import com.calyvora.people.dto.UpdateEmployeeRequest;
import com.calyvora.people.dto.UpdateMyProfileRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Employee directory & profiles (People OS slice P1). Every company member has exactly one employee
 * profile; profiles are <em>auto-provisioned</em> for users that don't have one yet, so the directory
 * always reflects the company's members without coupling the auth flow to People OS.
 * All reads/writes are tenant-scoped via {@link TenantContext} (SD-2).
 */
@Service
public class EmployeeService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EmployeeService.class);

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final com.calyvora.invitation.InvitationRepository invitationRepository;
    private final OnboardingTaskRepository onboardingTaskRepository;
    private final DesignationRepository designationRepository;
    private final com.calyvora.common.security.TenantBinder tenantBinder;

    public EmployeeService(EmployeeRepository employeeRepository, UserRepository userRepository,
                           DepartmentRepository departmentRepository,
                           com.calyvora.invitation.InvitationRepository invitationRepository,
                           OnboardingTaskRepository onboardingTaskRepository,
                           DesignationRepository designationRepository,
                           com.calyvora.common.security.TenantBinder tenantBinder) {
        this.tenantBinder = tenantBinder;
        this.designationRepository = designationRepository;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.invitationRepository = invitationRepository;
        this.onboardingTaskRepository = onboardingTaskRepository;
    }

    /**
     * Create the profile for a user who does not have one yet — the single place a profile comes into
     * existence, so the hire details agreed in recruitment are applied exactly once (PD-20).
     *
     * <p>Why here and not at invitation-accept time: accepting is a public, unauthenticated call with
     * no tenant bound, and {@code employees} is under Row-Level Security, so an insert there has no
     * company to belong to. The first authenticated read of the directory does have one.
     */
    /**
     * Give a brand-new user their employee profile, at the moment the user is created.
     *
     * <p>This is where provisioning belongs, and it is worth saying why it did not start here. The
     * comment on {@link #provision} explains the original reasoning: accepting an invitation is a
     * public, unauthenticated call, {@code employees} is under forced Row-Level Security, and an
     * insert with no tenant bound is refused. All true. The conclusion drawn from it — provision on
     * the first authenticated <em>read</em> instead — is what made every directory read a potential
     * write, which in turn is why the day sheet could not be {@code readOnly} and why Hibernate
     * dirty-checked a thousand entities to answer a GET.
     *
     * <p>The premise was wrong in one place: the tenant is not unknown at accept time, it is simply
     * not bound. The invitation names the company. Binding it around the insert states which tenant
     * the row belongs to, which is exactly what RLS wants to be told — it is not a way around the
     * policy, it is the policy being used as intended.
     *
     * <p>Goes through {@link TenantBinder} rather than simply setting {@link TenantContext}: the
     * caller is usually already inside a transaction, and by then the connection has been borrowed
     * and bound. Setting the context at that point changes nothing, and the insert is refused. That
     * mistake has already been made twice here.
     */
    @Transactional
    public Employee provisionFor(UUID companyId, UUID userId) {
        return tenantBinder.callAs(companyId, () -> employeeRepository.findByUserId(userId)
                .orElseGet(() -> provision(companyId, userId)));
    }

    private Employee provision(UUID companyId, UUID userId) {
        Employee employee = new Employee(UUID.randomUUID(), companyId, userId);
        userRepository.findByIdAndCompanyId(userId, companyId)
                .flatMap(u -> invitationRepository.findByCompanyIdAndEmailAndStatus(companyId, u.getEmail(),
                        com.calyvora.invitation.InvitationStatus.ACCEPTED))
                .filter(com.calyvora.invitation.Invitation::hasHireDetails)
                .ifPresent(invitation -> {
                    employee.setJobTitle(invitation.getJobTitle());
                    employee.setStartDate(invitation.getStartDate());
                    employee.setDepartmentId(invitation.getDepartmentId());
                    employee.setEmploymentStatus(EmploymentStatus.ONBOARDING);
                    employeeRepository.save(employee);
                    if (!invitation.isOnboardingSeeded()) {
                        seedJoiningChecklist(companyId, employee.getId());
                        invitation.setOnboardingSeeded(true);
                        invitationRepository.save(invitation);
                    }
                });
        return employeeRepository.save(employee);
    }

    /**
     * The joining checklist, seeded inline rather than through {@code OnboardingService} to keep the
     * dependency one-way — that service already depends on this package's repositories.
     */
    private void seedJoiningChecklist(UUID companyId, UUID employeeId) {
        int order = 0;
        for (String title : List.of(
                "Sign employment paperwork",
                "Set up laptop & accounts",
                "Complete IT security training",
                "Meet your team",
                "Read the company handbook")) {
            onboardingTaskRepository.save(new OnboardingTask(UUID.randomUUID(), companyId, employeeId,
                    ChecklistKind.ONBOARDING, title, order++));
        }
    }

    /**
     * Everyone in the company as entities, with missing profiles provisioned — users, employees and
     * the mapping between them, from one load of each table.
     *
     * <p>Exists because callers that needed the raw rows were going through {@link #directory()} and
     * then re-loading both tables themselves. The day sheet did exactly that: it asked for the
     * directory (users + employees, plus a response object built and sorted for every person, all of
     * it discarded), then loaded employees again, then loaded users again. Three loads of the whole
     * company to answer one question, and at a thousand people that is most of what the screen cost.
     *
     * <p>Returns entities, not DTOs, on purpose. A caller that wants presentation asks for
     * {@link #directory()}; a caller that wants to compute over the company should not pay to build a
     * thousand response objects it is going to throw away.
     */
    @Transactional
    public Roster roster() {
        UUID companyId = TenantContext.getCompanyId();
        List<User> users = userRepository.findByCompanyIdOrderByCreatedAtAsc(companyId);
        List<Employee> employees = new ArrayList<>(employeeRepository.findByCompanyId(companyId));
        Map<UUID, Employee> byUser = new HashMap<>();
        for (Employee e : employees) {
            byUser.put(e.getUserId(), e);
        }
        for (User u : users) {
            byUser.computeIfAbsent(u.getId(), uid -> {
                Employee provisioned = provision(companyId, uid);
                employees.add(provisioned);
                return provisioned;
            });
        }
        return new Roster(users, employees, byUser);
    }

    /**
     * The same roster, for a read that must not write.
     *
     * <p>Profiles are created with their user now ({@link #provisionFor}) and everyone who predates
     * that was backfilled in V50, so by the time a read happens there is nothing left to provision.
     * That is what lets a screen like the attendance day sheet run in a genuinely read-only
     * transaction — which is worth more than it sounds: a read-only transaction does not dirty-check
     * the thousand entities it just loaded, can be routed to a replica, and cannot surprise anyone by
     * writing during a GET.
     *
     * <p>If the invariant is ever broken, this says so and carries on without that person rather than
     * silently writing. A missing profile is then a visible bug in whatever created the user, which is
     * where it should be fixed — not papered over on every read for the life of the product.
     */
    @Transactional(readOnly = true)
    public Roster rosterForRead() {
        UUID companyId = TenantContext.getCompanyId();
        List<User> users = userRepository.findByCompanyIdOrderByCreatedAtAsc(companyId);
        List<Employee> employees = employeeRepository.findByCompanyId(companyId);
        Map<UUID, Employee> byUser = new HashMap<>();
        for (Employee e : employees) {
            byUser.put(e.getUserId(), e);
        }
        for (User u : users) {
            if (!byUser.containsKey(u.getId())) {
                log.warn("User {} in company {} has no employee profile; leaving them out of this read. "
                        + "Whatever created this user should have called provisionFor.", u.getId(), companyId);
            }
        }
        return new Roster(users, employees, byUser);
    }

    /**
     * The company's people, loaded once.
     *
     * <p>{@code employees} is not simply {@code byUser.values()}: a profile can outlive the user it
     * was provisioned for, and attendance still has to account for that person. Keeping both means a
     * caller never has to decide which list it wanted.
     */
    public record Roster(List<User> users, List<Employee> employees, Map<UUID, Employee> byUser) {}

    @Transactional
    public List<EmployeeResponse> directory() {
        Roster roster = roster();
        return roster.users().stream()
                .map(u -> EmployeeResponse.of(u, roster.byUser().get(u.getId())))
                .sorted(Comparator.comparing(EmployeeResponse::firstName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * Paged, searchable directory — the scalable path for large companies. Only the requested page of
     * users is loaded and profile-resolved (missing profiles are provisioned for that page only), so
     * this stays cheap whether the company has 5 employees or 5,000.
     */
    @Transactional
    public com.calyvora.common.dto.PageResponse<EmployeeResponse> directoryPage(String q, int page, int size) {
        UUID companyId = TenantContext.getCompanyId();
        String query = q == null ? "" : q.trim();
        int safeSize = Math.min(Math.max(size, 1), 200);
        var pageable = org.springframework.data.domain.PageRequest.of(Math.max(page, 0), safeSize,
                org.springframework.data.domain.Sort.by("firstName").ascending()
                        .and(org.springframework.data.domain.Sort.by("lastName").ascending()));
        var userPage = userRepository.directoryPage(companyId, query, pageable);

        List<UUID> userIds = userPage.getContent().stream().map(User::getId).toList();
        Map<UUID, Employee> byUser = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (Employee e : employeeRepository.findByCompanyIdAndUserIdIn(companyId, userIds)) {
                byUser.put(e.getUserId(), e);
            }
            for (User u : userPage.getContent()) {
                byUser.computeIfAbsent(u.getId(), uid -> provision(companyId, uid));
            }
        }
        return com.calyvora.common.dto.PageResponse.of(userPage, u -> EmployeeResponse.of(u, byUser.get(u.getId())));
    }

    /**
     * The top few people matching a typed fragment — what a person picker actually needs.
     *
     * <p>Five screens were loading the entire company to fill a dropdown. Two of them genuinely need
     * everyone (the org chart draws the whole tree; payroll pays everyone), but a dropdown never does:
     * nobody scrolls a thousand names, they type three letters. Paginating the dropdown would have
     * been the wrong fix for the same reason — the interaction is search, so the endpoint is search.
     *
     * <p>Reuses the directory's own query, so a picker and the directory agree on what "matches"
     * means. Read-only and provisions nothing: a user with no profile cannot be assigned work, so
     * skipping them is the right answer rather than writing a row on a keystroke.
     */
    @Transactional(readOnly = true)
    public List<com.calyvora.people.dto.EmployeeOption> search(String q, int limit) {
        UUID companyId = TenantContext.getCompanyId();
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        var pageable = org.springframework.data.domain.PageRequest.of(0, safeLimit,
                org.springframework.data.domain.Sort.by("firstName").ascending()
                        .and(org.springframework.data.domain.Sort.by("lastName").ascending()));
        var matches = userRepository.directoryPage(companyId, q == null ? "" : q.trim(), pageable);

        List<UUID> userIds = matches.getContent().stream().map(User::getId).toList();
        if (userIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, Employee> byUser = new HashMap<>();
        for (Employee e : employeeRepository.findByCompanyIdAndUserIdIn(companyId, userIds)) {
            byUser.put(e.getUserId(), e);
        }
        List<com.calyvora.people.dto.EmployeeOption> out = new ArrayList<>();
        for (User u : matches.getContent()) {
            Employee e = byUser.get(u.getId());
            if (e == null) {
                continue;
            }
            out.add(new com.calyvora.people.dto.EmployeeOption(e.getId().toString(),
                    (u.getFirstName() + " " + u.getLastName()).trim(), u.getEmail(), e.getJobTitle()));
        }
        return out;
    }

    @Transactional
    public EmployeeResponse get(UUID employeeId) {
        UUID companyId = TenantContext.getCompanyId();
        Employee employee = employeeRepository.findByIdAndCompanyId(employeeId, companyId)
                .orElseThrow(() -> new NotFoundException("Employee not found"));
        User user = userRepository.findById(employee.getUserId())
                .orElseThrow(() -> new NotFoundException("User not found"));
        return EmployeeResponse.of(user, employee);
    }

    @Transactional
    public EmployeeResponse me(UUID userId) {
        UUID companyId = TenantContext.getCompanyId();
        User user = userRepository.findByIdAndCompanyId(userId, companyId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        Employee employee = getOrCreate(companyId, userId);
        return EmployeeResponse.of(user, employee);
    }

    @Transactional
    public EmployeeResponse update(UUID employeeId, UpdateEmployeeRequest request) {
        UUID companyId = TenantContext.getCompanyId();
        Employee employee = employeeRepository.findByIdAndCompanyId(employeeId, companyId)
                .orElseThrow(() -> new NotFoundException("Employee not found"));

        if (request.employeeNo() != null) employee.setEmployeeNo(blankToNull(request.employeeNo()));
        if (request.jobTitle() != null) employee.setJobTitle(blankToNull(request.jobTitle()));
        if (request.employmentType() != null) {
            employee.setEmploymentType(EmploymentType.valueOf(request.employmentType()));
        }
        if (request.employmentStatus() != null) {
            employee.setEmploymentStatus(EmploymentStatus.valueOf(request.employmentStatus()));
        }
        if (request.workLocation() != null) employee.setWorkLocation(blankToNull(request.workLocation()));
        if (request.phone() != null) employee.setPhone(blankToNull(request.phone()));
        if (request.startDate() != null) {
            employee.setStartDate(request.startDate().isBlank() ? null : LocalDate.parse(request.startDate()));
        }
        if (request.endDate() != null) {
            employee.setEndDate(request.endDate().isBlank() ? null : LocalDate.parse(request.endDate()));
        }
        if (request.skills() != null) {
            String joined = request.skills().stream()
                    .map(String::trim).filter(s -> !s.isBlank()).distinct()
                    .reduce((a, b) -> a + ", " + b).orElse(null);
            employee.setSkills(joined);
        }
        if (request.rating() != null) {
            employee.setRating(request.rating() == 0 ? null : request.rating());
        }
        if (request.managerId() != null) {
            employee.setManagerId(resolveManager(companyId, employeeId, request.managerId()));
        }
        if (request.departmentId() != null) {
            employee.setDepartmentId(resolveDepartment(companyId, request.departmentId()));
        }
        if (request.designationId() != null) {
            employee.setDesignationId(resolveDesignation(companyId, request.designationId()));
        }

        User user = userRepository.findById(employee.getUserId())
                .orElseThrow(() -> new NotFoundException("User not found"));
        return EmployeeResponse.of(user, employee);
    }

    @Transactional
    public EmployeeResponse updateMe(UUID userId, UpdateMyProfileRequest request) {
        UUID companyId = TenantContext.getCompanyId();
        Employee employee = getOrCreate(companyId, userId);
        if (request.phone() != null) employee.setPhone(blankToNull(request.phone()));
        if (request.workLocation() != null) employee.setWorkLocation(blankToNull(request.workLocation()));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return EmployeeResponse.of(user, employee);
    }

    // ---- provisioning helpers ----

    /**
     * The employee id for a user, provisioning the profile if this user doesn't have one yet.
     * Lets other OS-apps (e.g. Knowledge OS authorship) attach to the People org graph without
     * duplicating the provisioning rule that People owns.
     */
    @Transactional
    public UUID ensureEmployeeId(UUID companyId, UUID userId) {
        return getOrCreate(companyId, userId).getId();
    }

    private Employee getOrCreate(UUID companyId, UUID userId) {
        return employeeRepository.findByUserId(userId)
                .orElseGet(() -> provision(companyId, userId));
    }

    private UUID resolveManager(UUID companyId, UUID employeeId, String managerId) {
        if (managerId.isBlank()) {
            return null;
        }
        UUID mgr;
        try {
            mgr = UUID.fromString(managerId);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Invalid manager id");
        }
        if (mgr.equals(employeeId)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "An employee cannot manage themselves");
        }
        employeeRepository.findByIdAndCompanyId(mgr, companyId)
                .orElseThrow(() -> new NotFoundException("Manager not found"));
        requireNoCycle(companyId, employeeId, mgr);
        return mgr;
    }

    /**
     * Refuse a manager who already reports to this employee, directly or through a chain.
     *
     * <p>Only self-management was blocked before, which was enough when the tree was decoration. It is
     * not now: the tree decides who can read whose attendance, leave and reviews (see {@code OrgScope}),
     * so A reporting to B while B reports to A would make each of them the other's subordinate and hand
     * them each other's data — a privilege escalation two profile edits deep, available to anybody who
     * can edit an org chart.
     *
     * <p>Walks upward from the proposed manager, which is at most the depth of the org, rather than
     * expanding the employee's whole subtree.
     */
    private void requireNoCycle(UUID companyId, UUID employeeId, UUID proposedManagerId) {
        java.util.Set<UUID> seen = new java.util.HashSet<>();
        UUID cursor = proposedManagerId;
        while (cursor != null && seen.add(cursor)) {
            if (cursor.equals(employeeId)) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "That person already reports to this employee, so making them the manager "
                                + "would create a loop in the org chart.");
            }
            cursor = employeeRepository.findByIdAndCompanyId(cursor, companyId)
                    .map(Employee::getManagerId)
                    .orElse(null);
        }
    }

    /** A rung on the company ladder. Blank clears it; an unknown id is an error, not a silent null. */
    private UUID resolveDesignation(UUID companyId, String designationId) {
        if (designationId.isBlank()) {
            return null;
        }
        UUID id;
        try {
            id = UUID.fromString(designationId);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Invalid designation id");
        }
        designationRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("Designation not found"));
        return id;
    }

    private UUID resolveDepartment(UUID companyId, String departmentId) {
        if (departmentId.isBlank()) {
            return null;
        }
        UUID dept;
        try {
            dept = UUID.fromString(departmentId);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Invalid department id");
        }
        departmentRepository.findByIdAndCompanyId(dept, companyId)
                .orElseThrow(() -> new NotFoundException("Department not found"));
        return dept;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
