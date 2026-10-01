package com.calyvora.people;

import com.calyvora.common.error.ForbiddenException;
import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.TenantContext;
import com.calyvora.identity.Role;
import com.calyvora.people.dto.OnboardingTaskResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Checklists (People OS slice P3; exits added in PD-20). One service runs both, because a joining
 * checklist and an exit checklist are the same object — a list somebody works through.
 *
 * <p>Who may tick is where they part company, and it is not a detail:
 * <ul>
 *   <li><b>Onboarding</b> — the new joiner themselves, or an admin. These are their tasks.</li>
 *   <li><b>Exit</b> — the leaver's <em>manager</em>, HR or an admin, never the leaver. Confirming
 *       that a laptop came back and access was revoked is clearance, and someone on their way out
 *       cannot sign their own.</li>
 * </ul>
 */
@Service
public class OnboardingService {

    private static final List<String> DEFAULT_TASKS = List.of(
            "Sign employment paperwork",
            "Set up laptop & accounts",
            "Complete IT security training",
            "Meet your team",
            "Read the company handbook");

    /**
     * The exit checklist a manager works through. Ordered the way an exit actually runs: agree the
     * handover before chasing the laptop, and settle the money before issuing the letter that says
     * dues are settled.
     */
    private static final List<String> DEFAULT_EXIT_TASKS = List.of(
            "Acknowledge the resignation in writing",
            "Agree the last working day and notice period",
            "Agree a knowledge-transfer plan",
            "Hand over live work and responsibilities",
            "Collect company assets (laptop, ID card, SIM, keys)",
            "Revoke system, email and building access",
            "Confirm final attendance and leave balance",
            "Process full and final settlement",
            "Hold the exit interview",
            "Issue the relieving letter and experience certificate");

    private final OnboardingTaskRepository taskRepository;
    private final EmployeeRepository employeeRepository;

    public OnboardingService(OnboardingTaskRepository taskRepository, EmployeeRepository employeeRepository) {
        this.taskRepository = taskRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional(readOnly = true)
    public List<OnboardingTaskResponse> list(UUID employeeId, ChecklistKind kind, AuthPrincipal principal) {
        Employee employee = requireEmployee(employeeId);
        requireAccess(employee, kind, principal);
        return taskRepository.findByEmployeeIdAndKindOrderBySortOrderAscCreatedAtAsc(employeeId, kind).stream()
                .map(OnboardingTaskResponse::of)
                .toList();
    }

    @Transactional
    public OnboardingTaskResponse add(UUID employeeId, ChecklistKind kind, String title) {
        Employee employee = requireEmployee(employeeId);
        int order = (int) taskRepository.countByEmployeeIdAndKind(employeeId, kind);
        OnboardingTask task = new OnboardingTask(UUID.randomUUID(), employee.getCompanyId(),
                employeeId, kind, title.trim(), order);
        taskRepository.save(task);
        return OnboardingTaskResponse.of(task);
    }

    @Transactional
    public List<OnboardingTaskResponse> seedDefaults(UUID employeeId, ChecklistKind kind) {
        Employee employee = requireEmployee(employeeId);
        int order = (int) taskRepository.countByEmployeeIdAndKind(employeeId, kind);
        for (String title : defaultsFor(kind)) {
            taskRepository.save(new OnboardingTask(UUID.randomUUID(), employee.getCompanyId(),
                    employeeId, kind, title, order++));
        }
        return taskRepository.findByEmployeeIdAndKindOrderBySortOrderAscCreatedAtAsc(employeeId, kind).stream()
                .map(OnboardingTaskResponse::of)
                .toList();
    }

    /** Seeds only if the checklist is empty — safe to call from the hire and exit flows. */
    @Transactional
    public void seedDefaultsIfEmpty(UUID employeeId, ChecklistKind kind) {
        if (taskRepository.countByEmployeeIdAndKind(employeeId, kind) == 0) {
            seedDefaults(employeeId, kind);
        }
    }

    @Transactional
    public OnboardingTaskResponse toggle(UUID taskId, boolean completed, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        OnboardingTask task = taskRepository.findByIdAndCompanyId(taskId, companyId)
                .orElseThrow(() -> new NotFoundException("Task not found"));
        requireAccess(requireEmployee(task.getEmployeeId()), task.getKind(), principal);
        task.setCompleted(completed);
        return OnboardingTaskResponse.of(task);
    }

    @Transactional
    public void delete(UUID taskId) {
        UUID companyId = TenantContext.getCompanyId();
        OnboardingTask task = taskRepository.findByIdAndCompanyId(taskId, companyId)
                .orElseThrow(() -> new NotFoundException("Task not found"));
        taskRepository.delete(task);
    }

    /** How far through a checklist an employee is — used by the exit screen's progress. */
    @Transactional(readOnly = true)
    public Progress progress(UUID employeeId, ChecklistKind kind) {
        return new Progress((int) taskRepository.countByEmployeeIdAndKindAndCompletedTrue(employeeId, kind),
                (int) taskRepository.countByEmployeeIdAndKind(employeeId, kind));
    }

    /** Completed out of total for one checklist. */
    public record Progress(int done, int total) {
        public boolean complete() {
            return total > 0 && done == total;
        }
    }

    // ---- helpers ----

    private static List<String> defaultsFor(ChecklistKind kind) {
        return kind == ChecklistKind.EXIT ? DEFAULT_EXIT_TASKS : DEFAULT_TASKS;
    }

    private Employee requireEmployee(UUID employeeId) {
        UUID companyId = TenantContext.getCompanyId();
        return employeeRepository.findByIdAndCompanyId(employeeId, companyId)
                .orElseThrow(() -> new NotFoundException("Employee not found"));
    }

    private void requireAccess(Employee employee, ChecklistKind kind, AuthPrincipal principal) {
        if (kind == ChecklistKind.EXIT) {
            requireManagerOrAdmin(employee, principal);
        } else {
            requireSelfOrAdmin(employee, principal);
        }
    }

    private void requireSelfOrAdmin(Employee employee, AuthPrincipal principal) {
        if (!isAdmin(principal) && !employee.getUserId().equals(principal.userId())) {
            throw new ForbiddenException("You cannot access this checklist");
        }
    }

    /**
     * Exit clearance: the leaver's own manager, or HR/admin. Explicitly <em>not</em> the leaver —
     * they would be signing off that they returned their own laptop.
     */
    private void requireManagerOrAdmin(Employee employee, AuthPrincipal principal) {
        if (isAdmin(principal) || Role.HR.name().equals(principal.role())) {
            return;
        }
        boolean isTheirManager = employee.getManagerId() != null
                && employeeRepository.findByIdAndCompanyId(employee.getManagerId(), TenantContext.getCompanyId())
                        .map(m -> m.getUserId().equals(principal.userId()))
                        .orElse(false);
        if (!isTheirManager) {
            throw new ForbiddenException("Only this employee's manager or HR can work the exit checklist");
        }
    }

    private static boolean isAdmin(AuthPrincipal principal) {
        return Role.OWNER.name().equals(principal.role()) || Role.ADMIN.name().equals(principal.role());
    }
}
