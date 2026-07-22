package com.calyvora.people;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.TenantContext;
import com.calyvora.people.dto.CreateGoalRequest;
import com.calyvora.people.dto.GoalResponse;
import com.calyvora.people.dto.UpdateGoalRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Employee goals (feedback C8). Anyone in the tenant can view; a goal is editable by an admin or by the
 * employee who owns it (self-service). Tenant-scoped.
 */
@Service
public class GoalService {

    private final GoalRepository goalRepository;
    private final EmployeeRepository employeeRepository;

    public GoalService(GoalRepository goalRepository, EmployeeRepository employeeRepository) {
        this.goalRepository = goalRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional(readOnly = true)
    public List<GoalResponse> list(UUID employeeId) {
        UUID companyId = TenantContext.getCompanyId();
        requireEmployee(employeeId, companyId);
        return goalRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId).stream()
                .map(GoalResponse::of).toList();
    }

    @Transactional
    public GoalResponse create(UUID employeeId, CreateGoalRequest req, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        Employee employee = requireEmployee(employeeId, companyId);
        requireCanManage(employee, principal);
        Goal goal = new Goal(UUID.randomUUID(), companyId, employeeId, req.title().trim(),
                blankToNull(req.description()), parseDate(req.targetDate()), principal.userId());
        goalRepository.save(goal);
        return GoalResponse.of(goal);
    }

    @Transactional
    public GoalResponse update(UUID goalId, UpdateGoalRequest req, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        Goal goal = goalRepository.findByIdAndCompanyId(goalId, companyId)
                .orElseThrow(() -> new NotFoundException("Goal not found"));
        requireCanManage(requireEmployee(goal.getEmployeeId(), companyId), principal);

        if (req.title() != null && !req.title().isBlank()) goal.setTitle(req.title().trim());
        if (req.description() != null) goal.setDescription(blankToNull(req.description()));
        if (req.targetDate() != null) goal.setTargetDate(parseDate(req.targetDate()));
        if (req.progress() != null) {
            goal.setProgress(req.progress());
            // Reaching 100% completes an open goal automatically.
            if (req.progress() == 100 && goal.getStatus() == GoalStatus.OPEN) goal.setStatus(GoalStatus.ACHIEVED);
        }
        if (req.status() != null) {
            GoalStatus status = GoalStatus.valueOf(req.status());
            goal.setStatus(status);
            if (status == GoalStatus.ACHIEVED) goal.setProgress(100);
        }
        return GoalResponse.of(goal);
    }

    @Transactional
    public void delete(UUID goalId, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        Goal goal = goalRepository.findByIdAndCompanyId(goalId, companyId)
                .orElseThrow(() -> new NotFoundException("Goal not found"));
        requireCanManage(requireEmployee(goal.getEmployeeId(), companyId), principal);
        goalRepository.delete(goal);
    }

    private Employee requireEmployee(UUID employeeId, UUID companyId) {
        return employeeRepository.findByIdAndCompanyId(employeeId, companyId)
                .orElseThrow(() -> new NotFoundException("Employee not found"));
    }

    /** Admins manage anyone's goals; everyone else may manage only their own. */
    private void requireCanManage(Employee employee, AuthPrincipal principal) {
        boolean admin = "OWNER".equals(principal.role()) || "ADMIN".equals(principal.role());
        boolean self = employee.getUserId().equals(principal.userId());
        if (!admin && !self) {
            throw new ApiException(ErrorCode.FORBIDDEN, "You can only manage your own goals");
        }
    }

    private static LocalDate parseDate(String s) {
        return s == null || s.isBlank() ? null : LocalDate.parse(s);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
