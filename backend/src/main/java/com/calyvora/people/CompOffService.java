package com.calyvora.people;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.error.ForbiddenException;
import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.TenantContext;
import com.calyvora.identity.User;
import com.calyvora.identity.UserRepository;
import com.calyvora.notification.NotificationService;
import com.calyvora.notification.NotificationType;
import com.calyvora.people.dto.CompOffPayload;
import com.calyvora.people.dto.CompOffResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Comp-off: a day worked that was not owed, earning a day off later.
 *
 * <p>Approval is scoped exactly as leave and attendance regularizations are — HR and admins see the
 * whole company, a manager sees their own reports. Three features now share that rule; it is written
 * out three times because extracting it would need a shared "who reports to whom" service that does
 * not exist yet, and a fourth copy is the point at which that becomes worth building.
 */
@Service
public class CompOffService {

    private final CompOffCreditRepository repository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final LeavePolicyService policyService;
    private final NotificationService notificationService;

    public CompOffService(CompOffCreditRepository repository, EmployeeRepository employeeRepository,
                          UserRepository userRepository, LeavePolicyService policyService,
                          NotificationService notificationService) {
        this.repository = repository;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.policyService = policyService;
        this.notificationService = notificationService;
    }

    @Transactional
    public CompOffResponse claim(AuthPrincipal principal, CompOffPayload payload) {
        UUID companyId = TenantContext.getCompanyId();
        Employee employee = employeeForUser(companyId, principal.userId());
        LocalDate workedOn = LocalDate.parse(payload.workedOn());

        if (workedOn.isAfter(LocalDate.now())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "You cannot claim a comp-off for a day you have not worked yet");
        }
        // The database also has a unique constraint on (employee, day). This check exists to answer
        // with a sentence instead of a constraint violation — claiming the same Saturday twice is an
        // ordinary mistake, not an exceptional one.
        if (repository.existsByEmployeeIdAndWorkedOn(employee.getId(), workedOn)) {
            throw new ApiException(ErrorCode.CONFLICT, "You have already claimed a comp-off for " + workedOn);
        }

        CompOffCredit credit = repository.save(new CompOffCredit(
                UUID.randomUUID(), companyId, employee.getId(), workedOn, blankToNull(payload.reason())));

        notifyApprover(companyId, employee, principal, credit);
        return CompOffResponse.of(credit, nameOf(employee), LocalDate.now());
    }

    @Transactional(readOnly = true)
    public List<CompOffResponse> mine(AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        Employee employee = employeeForUser(companyId, principal.userId());
        String name = nameOf(employee);
        LocalDate today = LocalDate.now();
        return repository.findByEmployeeIdOrderByWorkedOnDesc(employee.getId()).stream()
                .map(c -> CompOffResponse.of(c, name, today))
                .toList();
    }

    /** Claims waiting on this caller: everyone's for HR and admins, their reports' for a manager. */
    @Transactional(readOnly = true)
    public List<CompOffResponse> pending(AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        LocalDate today = LocalDate.now();
        List<CompOffResponse> out = new ArrayList<>();
        for (CompOffCredit c : repository.findByCompanyIdAndStatusOrderByWorkedOnAsc(companyId, CompOffStatus.PENDING)) {
            if (seesEveryone(principal) || isMyReport(companyId, c.getEmployeeId(), principal.userId())) {
                out.add(CompOffResponse.of(c, nameOfEmployeeId(c.getEmployeeId()), today));
            }
        }
        return out;
    }

    @Transactional
    public CompOffResponse decide(UUID id, boolean approve, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        CompOffCredit credit = repository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("Comp-off request not found"));

        if (!seesEveryone(principal) && !isMyReport(companyId, credit.getEmployeeId(), principal.userId())) {
            throw new ForbiddenException("You can only decide comp-off for people who report to you");
        }
        if (credit.getStatus() != CompOffStatus.PENDING) {
            throw new ApiException(ErrorCode.CONFLICT, "This request has already been decided");
        }

        if (approve) {
            // Expiry runs from the day worked, not the day approved: an approver sitting on a request
            // for three weeks would otherwise silently extend the company's liability.
            int days = policyService.effective(LeaveType.COMP_OFF).getCompOffExpiryDays();
            credit.approve(principal.userId(), credit.getWorkedOn().plusDays(days));
        } else {
            credit.reject(principal.userId());
        }

        employeeRepository.findById(credit.getEmployeeId()).ifPresent(employee ->
                notificationService.send(companyId, employee.getUserId(), principal.userId(),
                        approve ? NotificationType.LEAVE_APPROVED : NotificationType.LEAVE_REJECTED,
                        "Your comp-off was " + (approve ? "approved" : "declined"),
                        "for working on " + credit.getWorkedOn(),
                        "/me/leave", "COMP_OFF", credit.getId()));

        return CompOffResponse.of(credit, nameOfEmployeeId(credit.getEmployeeId()), LocalDate.now());
    }

    // ---- used by LeaveService when COMP_OFF leave is requested and approved ----

    /** Credits this employee can still spend today, oldest first so the nearest to expiry goes first. */
    @Transactional(readOnly = true)
    public List<CompOffCredit> spendable(UUID employeeId) {
        LocalDate today = LocalDate.now();
        return repository.findByEmployeeIdAndStatusOrderByWorkedOnAsc(employeeId, CompOffStatus.APPROVED)
                .stream()
                .filter(c -> c.isSpendable(today))
                .toList();
    }

    /**
     * Spend {@code days} credits on an approved comp-off leave request.
     *
     * <p>Oldest first, so the credit closest to expiring is used before one with months left. Taking
     * the newest first would let a perfectly good credit expire while a fresher one was spent, which
     * is the sort of thing nobody notices until someone loses a day they had earned.
     */
    @Transactional
    public void spend(UUID employeeId, int days, UUID leaveRequestId) {
        List<CompOffCredit> available = spendable(employeeId);
        if (available.size() < days) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Only " + available.size() + " comp-off day(s) available; this request needs " + days);
        }
        for (int i = 0; i < days; i++) {
            available.get(i).consume(leaveRequestId);
        }
    }

    // ---- helpers ----

    private void notifyApprover(UUID companyId, Employee employee, AuthPrincipal principal, CompOffCredit credit) {
        UUID managerUserId = employee.getManagerId() == null ? null
                : employeeRepository.findByIdAndCompanyId(employee.getManagerId(), companyId)
                        .map(Employee::getUserId).orElse(null);
        if (managerUserId == null) {
            return;   // no manager to tell; HR still sees it in the queue
        }
        notificationService.send(companyId, managerUserId, principal.userId(),
                NotificationType.LEAVE_REQUESTED,
                nameOf(employee) + " claimed a comp-off",
                "for working on " + credit.getWorkedOn(),
                "/comp-off", "COMP_OFF", credit.getId());
    }

    private boolean seesEveryone(AuthPrincipal principal) {
        String role = principal.role();
        return "OWNER".equals(role) || "ADMIN".equals(role) || "HR".equals(role);
    }

    private boolean isMyReport(UUID companyId, UUID employeeId, UUID managerUserId) {
        UUID managerEmployeeId = employeeRepository.findByUserId(managerUserId)
                .map(Employee::getId).orElse(null);
        if (managerEmployeeId == null) {
            return false;
        }
        return employeeRepository.findByIdAndCompanyId(employeeId, companyId)
                .map(Employee::getManagerId).filter(managerEmployeeId::equals).isPresent();
    }

    private Employee employeeForUser(UUID companyId, UUID userId) {
        return employeeRepository.findByUserId(userId)
                .orElseGet(() -> employeeRepository.save(new Employee(UUID.randomUUID(), companyId, userId)));
    }

    private String nameOf(Employee employee) {
        return userRepository.findById(employee.getUserId()).map(User::fullName).orElse("Unknown");
    }

    private String nameOfEmployeeId(UUID employeeId) {
        return employeeRepository.findById(employeeId).map(this::nameOf).orElse("Unknown");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
