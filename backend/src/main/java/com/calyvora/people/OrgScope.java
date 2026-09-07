package com.calyvora.people;

import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Who may see whose data — the single answer, used by every screen that lists more than one person.
 *
 * <p>Before this existed each module decided for itself, and they disagreed: the directory was open to
 * everyone, attendance and expenses were HR-only, and leave had its own manager check. A MANAGER
 * therefore saw either the whole company or nothing at all depending on which page they opened, which
 * is the defect this class exists to close.
 *
 * <p><b>The rule is the reporting tree, never the job title.</b> A company can call its people whatever
 * it likes — Lead, Principal, Intern — and none of it grants access; being somebody's manager, directly
 * or through a chain, is what does. That is deliberate: titles are customer-editable free text, and a
 * permission you can grant yourself by renaming your own row is not a permission.
 *
 * <p>Three roles still see the whole company, because their job is the company rather than a team:
 * ADMIN runs it, HR does people-ops for all of it, and OWNER is the vendor. Everyone else — MANAGER
 * and MEMBER alike — sees themselves plus their downline. MEMBER is not a separate case: a member with
 * no reports has an empty downline and the same code gives them only themselves.
 */
@Service
public class OrgScope {

    /**
     * Roles whose scope is the company rather than a team. Note MANAGER is deliberately absent: a
     * manager's reach comes from having reports, so a manager of nobody sees nobody — which is correct,
     * and was not true before.
     */
    private static final Set<String> WHOLE_COMPANY = Set.of("OWNER", "ADMIN", "HR");

    /**
     * Depth cap for the downline walk. `manager_id` is a plain self-referencing column with no database
     * constraint against a cycle (A reports to B reports to A), and two people pointing at each other
     * would otherwise spin forever inside a request thread. The visited set already prevents that; this
     * is the second belt, and 64 is far deeper than any real org.
     */
    private static final int MAX_DEPTH = 64;

    private final EmployeeRepository employeeRepository;

    public OrgScope(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    /** Whether this caller's scope is the whole company rather than their own branch of the tree. */
    public boolean seesWholeCompany(AuthPrincipal principal) {
        return principal != null && WHOLE_COMPANY.contains(principal.role());
    }

    /** The caller's own employee row, if they have one. The platform OWNER typically does not. */
    @Transactional(readOnly = true)
    public Optional<Employee> selfOf(AuthPrincipal principal) {
        return principal == null ? Optional.empty() : employeeRepository.findByUserId(principal.userId());
    }

    /**
     * Everyone the caller may read, as employee ids — themselves included.
     *
     * <p>An empty result means "nobody", never "everybody". Call sites must not treat empty as an
     * absent filter: a query built as `where id in ()` returning nothing is the correct answer for a
     * caller with no profile, whereas skipping the filter would hand them the company.
     */
    @Transactional(readOnly = true)
    public Set<UUID> visibleEmployeeIds(AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        if (seesWholeCompany(principal)) {
            return employeeRepository.findByCompanyId(companyId).stream()
                    .map(Employee::getId)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        }
        Set<UUID> ids = new LinkedHashSet<>();
        selfOf(principal).map(Employee::getId).ifPresent(ids::add);
        ids.addAll(downline(principal, false));
        return ids;
    }

    /**
     * The caller's reports — direct only, or the whole subtree beneath them. Excludes the caller.
     *
     * <p>Transitive by default because a manager whose reports are themselves leads would otherwise see
     * three people and none of the thirty under them, which is not what "my team" means to anyone who
     * has run one. The direct-only form is offered as a filter on the screen rather than as the
     * default.
     */
    @Transactional(readOnly = true)
    public Set<UUID> downline(AuthPrincipal principal, boolean directOnly) {
        return selfOf(principal)
                .map(self -> downlineOf(self.getId(), directOnly))
                .orElseGet(Set::of);
    }

    /** As {@link #downline}, from an employee id already in hand. */
    @Transactional(readOnly = true)
    public Set<UUID> downlineOf(UUID managerEmployeeId, boolean directOnly) {
        if (managerEmployeeId == null) {
            return Set.of();
        }
        return walk(childrenByManager(), managerEmployeeId, directOnly);
    }

    /** Whether the caller leads anybody at all — what makes the "My team" section appear. */
    @Transactional(readOnly = true)
    public boolean leadsAnyone(AuthPrincipal principal) {
        return !downline(principal, false).isEmpty();
    }

    /** How many report to the caller directly, and how many sit beneath them altogether. */
    public record Standing(int directCount, int totalCount) {
        public boolean leadsTeam() {
            return totalCount > 0;
        }
    }

    /**
     * Both counts from a single walk.
     *
     * <p>Exists because the app shell asks "do I lead anybody" on every page load, and answering that
     * as two separate {@link #downline} calls loads the company's employees twice per navigation. One
     * query, one tree, two numbers.
     */
    @Transactional(readOnly = true)
    public Standing standing(AuthPrincipal principal) {
        UUID self = selfOf(principal).map(Employee::getId).orElse(null);
        if (self == null) {
            return new Standing(0, 0);
        }
        Map<UUID, List<UUID>> childrenOf = childrenByManager();
        return new Standing(childrenOf.getOrDefault(self, List.of()).size(),
                walk(childrenOf, self, false).size());
    }

    /**
     * The company's reporting tree as manager → reports.
     *
     * <p>One query, then walked in memory. The alternative — a recursive CTE — is a native query that
     * would have to re-state the tenant predicate Row-Level Security already applies to this finder,
     * and companies here are thousands of rows rather than millions.
     */
    private Map<UUID, List<UUID>> childrenByManager() {
        Map<UUID, List<UUID>> childrenOf = new HashMap<>();
        for (Employee e : employeeRepository.findByCompanyId(TenantContext.getCompanyId())) {
            if (e.getManagerId() != null) {
                childrenOf.computeIfAbsent(e.getManagerId(), k -> new ArrayList<>()).add(e.getId());
            }
        }
        return childrenOf;
    }

    /** Breadth-first down the tree from one person, excluding them. Cycle-safe. */
    private static Set<UUID> walk(Map<UUID, List<UUID>> childrenOf, UUID rootId, boolean directOnly) {
        List<UUID> direct = childrenOf.getOrDefault(rootId, List.of());
        if (directOnly) {
            return new LinkedHashSet<>(direct);
        }
        Set<UUID> found = new LinkedHashSet<>();
        Set<UUID> seen = new HashSet<>();
        seen.add(rootId);
        Deque<UUID> frontier = new ArrayDeque<>(direct);
        int depth = 0;
        while (!frontier.isEmpty() && depth++ < MAX_DEPTH) {
            for (int remaining = frontier.size(); remaining > 0; remaining--) {
                UUID id = frontier.poll();
                if (!seen.add(id)) {
                    continue; // already walked — a cycle, or two paths to the same person
                }
                found.add(id);
                frontier.addAll(childrenOf.getOrDefault(id, List.of()));
            }
        }
        return found;
    }

    /**
     * Whether the caller may read this employee's data, by the rule above.
     *
     * <p>Self is always included: every "my team" screen is also reachable by the person themselves for
     * their own row, and excluding self here would make a lead unable to see their own attendance on
     * the team page they otherwise own.
     */
    @Transactional(readOnly = true)
    public boolean canSee(AuthPrincipal principal, UUID employeeId) {
        return employeeId != null && visibleEmployeeIds(principal).contains(employeeId);
    }
}
