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
 * Onboarding checklists (People OS slice P3). Admins manage tasks; the assignee (or an admin) can
 * tick items off. All tenant-scoped.
 */
@Service
public class OnboardingService {

    private static final List<String> DEFAULT_TASKS = List.of(
            "Sign employment paperwork",
            "Set up laptop & accounts",
            "Complete IT security training",
            "Meet your team",
            "Read the company handbook");

    private final OnboardingTaskRepository taskRepository;
    private final EmployeeRepository employeeRepository;

    public OnboardingService(OnboardingTaskRepository taskRepository, EmployeeRepository employeeRepository) {
        this.taskRepository = taskRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional(readOnly = true)
    public List<OnboardingTaskResponse> list(UUID employeeId, AuthPrincipal principal) {
        Employee employee = requireEmployee(employeeId);
        requireSelfOrAdmin(employee, principal);
        return taskRepository.findByEmployeeIdOrderBySortOrderAscCreatedAtAsc(employeeId).stream()
                .map(OnboardingTaskResponse::of)
                .toList();
    }

    @Transactional
    public OnboardingTaskResponse add(UUID employeeId, String title) {
        Employee employee = requireEmployee(employeeId);
        int order = (int) taskRepository.countByEmployeeId(employeeId);
        OnboardingTask task = new OnboardingTask(UUID.randomUUID(), employee.getCompanyId(),
                employeeId, title.trim(), order);
        taskRepository.save(task);
        return OnboardingTaskResponse.of(task);
    }

    @Transactional
    public List<OnboardingTaskResponse> seedDefaults(UUID employeeId) {
        Employee employee = requireEmployee(employeeId);
        int order = (int) taskRepository.countByEmployeeId(employeeId);
        for (String title : DEFAULT_TASKS) {
            taskRepository.save(new OnboardingTask(UUID.randomUUID(), employee.getCompanyId(),
                    employeeId, title, order++));
        }
        return taskRepository.findByEmployeeIdOrderBySortOrderAscCreatedAtAsc(employeeId).stream()
                .map(OnboardingTaskResponse::of)
                .toList();
    }

    @Transactional
    public OnboardingTaskResponse toggle(UUID taskId, boolean completed, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        OnboardingTask task = taskRepository.findByIdAndCompanyId(taskId, companyId)
                .orElseThrow(() -> new NotFoundException("Task not found"));
        requireSelfOrAdmin(requireEmployee(task.getEmployeeId()), principal);
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

    // ---- helpers ----

    private Employee requireEmployee(UUID employeeId) {
        UUID companyId = TenantContext.getCompanyId();
        return employeeRepository.findByIdAndCompanyId(employeeId, companyId)
                .orElseThrow(() -> new NotFoundException("Employee not found"));
    }

    private void requireSelfOrAdmin(Employee employee, AuthPrincipal principal) {
        boolean isAdmin = Role.OWNER.name().equals(principal.role()) || Role.ADMIN.name().equals(principal.role());
        boolean isSelf = employee.getUserId().equals(principal.userId());
        if (!isAdmin && !isSelf) {
            throw new ForbiddenException("You cannot access this checklist");
        }
    }
}
