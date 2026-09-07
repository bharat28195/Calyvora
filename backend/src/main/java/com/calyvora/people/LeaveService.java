package com.calyvora.people;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.error.ForbiddenException;
import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.TenantContext;
import com.calyvora.identity.Role;
import com.calyvora.identity.User;
import com.calyvora.identity.UserRepository;
import com.calyvora.notification.NotificationService;
import com.calyvora.notification.NotificationType;
import com.calyvora.people.dto.CreateLeaveRequest;
import com.calyvora.people.dto.LeaveBalanceResponse;
import com.calyvora.people.dto.LeaveRequestResponse;
import com.calyvora.people.dto.LeaveTypeBalanceResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Time-off / leave (People OS slice P4): request → approve/reject/cancel, plus a simple vacation
 * balance. Members act on their own requests; OWNER/ADMIN approve and see the whole company.
 */
@Service
public class LeaveService {

    private final LeaveRequestRepository leaveRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final LeavePolicyService policyService;
    private final CompOffService compOffService;

    private final OrgScope orgScope;

    public LeaveService(LeaveRequestRepository leaveRepository, EmployeeRepository employeeRepository,
                        UserRepository userRepository, NotificationService notificationService,
                        LeavePolicyService policyService, CompOffService compOffService,
                        OrgScope orgScope) {
        this.orgScope = orgScope;
        this.leaveRepository = leaveRepository;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.policyService = policyService;
        this.compOffService = compOffService;
    }

    @Transactional
    public LeaveRequestResponse request(AuthPrincipal principal, CreateLeaveRequest dto) {
        UUID companyId = TenantContext.getCompanyId();
        Employee employee = employeeForUser(companyId, principal.userId());

        LocalDate start = LocalDate.parse(dto.startDate());
        LocalDate end = LocalDate.parse(dto.endDate());
        if (end.isBefore(start)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "End date must be on or after the start date");
        }
        int days = (int) (ChronoUnit.DAYS.between(start, end) + 1);
        LeaveType type = LeaveType.valueOf(dto.type());

        // Comp-off is spent from earned credits, not from an annual entitlement. Checking here rather
        // than only at approval means the person finds out while they are still choosing dates —
        // being told at approval time that they never had the days is the worse of the two.
        if (type == LeaveType.COMP_OFF) {
            int available = compOffService.spendable(employee.getId()).size();
            if (available < days) {
                throw new ApiException(ErrorCode.CONFLICT, available == 0
                        ? "You have no comp-off days available"
                        : "You have " + available + " comp-off day(s) available; this request needs " + days);
            }
        }

        LeaveRequest req = new LeaveRequest(UUID.randomUUID(), companyId, employee.getId(),
                type, start, end, days,
                dto.reason() == null || dto.reason().isBlank() ? null : dto.reason().trim());
        leaveRepository.save(req);

        // Route it to whoever has to act on it (D4): the requester's manager, or every Owner/Admin
        // when nobody manages them — a request with no approver would just sit there unseen.
        String who = nameOf(employee);
        notificationService.sendAll(companyId, approversFor(companyId, employee), principal.userId(),
                NotificationType.LEAVE_REQUESTED,
                who + " requested " + days + (days == 1 ? " day" : " days") + " off",
                dto.type().toLowerCase() + " · " + start + " → " + end
                        + (req.getReason() == null ? "" : " · " + req.getReason()),
                "/people/time-off", "LEAVE_REQUEST", req.getId());

        return LeaveRequestResponse.of(req, who);
    }

    /**
     * Who should approve this person's leave: their manager if they have one, otherwise every
     * Owner/Admin in the company. The org chart lives in People, so the routing rule does too.
     */
    private List<UUID> approversFor(UUID companyId, Employee employee) {
        if (employee.getManagerId() != null) {
            Optional<UUID> manager = employeeRepository.findByIdAndCompanyId(employee.getManagerId(), companyId)
                    .map(Employee::getUserId);
            if (manager.isPresent()) {
                return List.of(manager.get());
            }
        }
        return userRepository.findByCompanyIdOrderByCreatedAtAsc(companyId).stream()
                .filter(u -> u.getRole() == Role.OWNER || u.getRole() == Role.ADMIN)
                .map(User::getId)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> listMine(AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        Employee employee = employeeForUser(companyId, principal.userId());
        String name = nameOf(employee);
        return leaveRepository.findByEmployeeIdOrderByCreatedAtDesc(employee.getId()).stream()
                .map(r -> LeaveRequestResponse.of(r, name))
                .toList();
    }

    /**
     * The approvals inbox: every request in the company for HR and admins, and a manager's own
     * reports for everybody else who can reach this.
     *
     * <p>This used to be {@code listAll()}, restricted to OWNER/ADMIN/HR — which meant a manager could
     * not see, let alone decide, their own team's leave, and every holiday in the company funnelled
     * through HR. The product already contradicted itself here: attendance regularizations have always
     * been scoped to the caller's reports ({@code RegularizationService.pending}), so the same manager
     * could approve a missed punch but not a day off. This is that same scoping, applied to leave.
     */
    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> listForApprover(AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        Map<UUID, String> names = new HashMap<>();
        List<LeaveRequest> all = leaveRepository.findByCompanyIdOrderByCreatedAtDesc(companyId);
        List<LeaveRequest> visible = seesEveryone(principal)
                ? all
                : all.stream().filter(r -> isMyReport(companyId, r.getEmployeeId(), principal.userId())).toList();
        return visible.stream()
                .map(r -> LeaveRequestResponse.of(r,
                        names.computeIfAbsent(r.getEmployeeId(), this::nameOfEmployeeId)))
                .toList();
    }

    @Transactional
    public LeaveRequestResponse approve(UUID id, AuthPrincipal principal) {
        return decide(id, LeaveStatus.APPROVED, principal);
    }

    @Transactional
    public LeaveRequestResponse reject(UUID id, AuthPrincipal principal) {
        return decide(id, LeaveStatus.REJECTED, principal);
    }

    @Transactional
    public LeaveRequestResponse cancel(UUID id, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        LeaveRequest req = leaveRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("Request not found"));
        Employee employee = employeeRepository.findById(req.getEmployeeId())
                .orElseThrow(() -> new NotFoundException("Employee not found"));
        if (!employee.getUserId().equals(principal.userId())) {
            throw new ForbiddenException("You can only cancel your own requests");
        }
        if (req.getStatus() != LeaveStatus.PENDING) {
            throw new ApiException(ErrorCode.CONFLICT, "Only pending requests can be cancelled");
        }
        req.cancel();
        return LeaveRequestResponse.of(req, nameOf(employee));
    }

    /**
     * The vacation balance, in the shape this endpoint has always returned.
     *
     * <p>Kept whole-day and vacation-only because it is what {@code /leave/balance} promised and what
     * the Me screen renders. The richer per-type view lives at {@code /leave/balances} rather than
     * being forced through this record — changing a response shape to add a feature breaks every
     * caller that was happy, and the two can disagree only if someone lets them, which is why both
     * are computed by {@link #balanceFor}.
     */
    @Transactional(readOnly = true)
    public LeaveBalanceResponse balance(AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        Employee employee = employeeForUser(companyId, principal.userId());
        LeaveTypeBalanceResponse vacation = balanceFor(employee,
                policyService.effective(LeaveType.VACATION), LocalDate.now());
        return new LeaveBalanceResponse(
                vacation.entitlementPerYear().setScale(0, RoundingMode.HALF_UP).intValue(),
                vacation.usedDays().setScale(0, RoundingMode.HALF_UP).intValue(),
                vacation.availableDays().setScale(0, RoundingMode.HALF_UP).intValue(),
                vacation.pendingDays().setScale(0, RoundingMode.HALF_UP).intValue());
    }

    /** Every leave type, with the accrual and carry-forward behind each number. */
    @Transactional(readOnly = true)
    public List<LeaveTypeBalanceResponse> balances(AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        Employee employee = employeeForUser(companyId, principal.userId());
        LocalDate today = LocalDate.now();
        Map<LeaveType, LeavePolicy> policies = policyService.effectivePolicies();
        List<LeaveTypeBalanceResponse> out = new ArrayList<>();
        for (LeaveType type : LeaveType.values()) {
            out.add(balanceFor(employee, policies.get(type), today));
        }
        return out;
    }

    /**
     * One type's balance for one employee.
     *
     * <p>Pending days are subtracted from what is available, not just reported alongside it. Two
     * requests submitted before either is approved would otherwise both look affordable, and the
     * second would be rejected at approval time by an approver who has no idea why.
     *
     * <p>Comp-off ignores accrual entirely: its entitlement is a pile of earned credits, so
     * "available" is however many are approved and unexpired today.
     */
    private LeaveTypeBalanceResponse balanceFor(Employee employee, LeavePolicy policy, LocalDate today) {
        int year = today.getYear();
        Map<Integer, BigDecimal> usedByYear = new HashMap<>();
        BigDecimal usedThisYear = BigDecimal.ZERO;
        BigDecimal pendingThisYear = BigDecimal.ZERO;

        for (LeaveRequest r : leaveRepository.findByEmployeeIdOrderByCreatedAtDesc(employee.getId())) {
            if (r.getType() != policy.getType()) {
                continue;
            }
            int y = r.getStartDate().getYear();
            BigDecimal days = BigDecimal.valueOf(r.getDays());
            if (r.getStatus() == LeaveStatus.APPROVED) {
                usedByYear.merge(y, days, BigDecimal::add);
                if (y == year) {
                    usedThisYear = usedThisYear.add(days);
                }
            } else if (r.getStatus() == LeaveStatus.PENDING && y == year) {
                pendingThisYear = pendingThisYear.add(days);
            }
        }

        if (policy.getType() == LeaveType.COMP_OFF) {
            BigDecimal credits = BigDecimal.valueOf(compOffService.spendable(employee.getId()).size());
            return new LeaveTypeBalanceResponse(policy.getType().name(), policy.isPaid(),
                    policy.getAccrual().name(), BigDecimal.ZERO, credits, BigDecimal.ZERO,
                    usedThisYear, pendingThisYear, credits.subtract(pendingThisYear).max(BigDecimal.ZERO));
        }

        LeaveEntitlement.Accrued accrued = LeaveEntitlement.accrued(
                policy, employee.getStartDate(), year, today, usedByYear);
        BigDecimal available = accrued.total().subtract(usedThisYear).subtract(pendingThisYear);
        return new LeaveTypeBalanceResponse(policy.getType().name(), policy.isPaid(),
                policy.getAccrual().name(), policy.getDaysPerYear(),
                accrued.earnedThisYear(), accrued.carriedIn(),
                usedThisYear, pendingThisYear, available.max(BigDecimal.ZERO));
    }

    // ---- helpers ----

    private LeaveRequestResponse decide(UUID id, LeaveStatus decision, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        LeaveRequest req = leaveRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("Request not found"));
        // The role check on the endpoint says "a manager may decide leave"; it cannot say "this
        // manager may decide THIS request". Without the line below, opening the endpoint to managers
        // would let any manager approve any employee's leave in the company — a wider hole than the
        // one being fixed.
        if (!seesEveryone(principal) && !isMyReport(companyId, req.getEmployeeId(), principal.userId())) {
            throw new ForbiddenException("You can only decide leave for people who report to you");
        }
        if (req.getStatus() != LeaveStatus.PENDING) {
            throw new ApiException(ErrorCode.CONFLICT, "This request has already been decided");
        }
        // Spend the credits before recording the decision, so a shortfall aborts the whole approval
        // rather than leaving leave approved and paid for by credits that were never there. The
        // request-time check makes this rare, but credits can expire between asking and approving.
        if (req.getType() == LeaveType.COMP_OFF && decision == LeaveStatus.APPROVED) {
            compOffService.spend(req.getEmployeeId(), req.getDays(), req.getId());
        }

        req.decide(decision, principal.userId());

        // Tell the requester what was decided.
        boolean approved = decision == LeaveStatus.APPROVED;
        employeeRepository.findById(req.getEmployeeId()).ifPresent(employee ->
                notificationService.send(companyId, employee.getUserId(), principal.userId(),
                        approved ? NotificationType.LEAVE_APPROVED : NotificationType.LEAVE_REJECTED,
                        "Your leave was " + (approved ? "approved" : "declined"),
                        req.getType().name().toLowerCase() + " · " + req.getStartDate() + " → " + req.getEndDate(),
                        "/people/time-off", "LEAVE_REQUEST", req.getId()));

        return LeaveRequestResponse.of(req, nameOfEmployeeId(req.getEmployeeId()));
    }

    private Employee employeeForUser(UUID companyId, UUID userId) {
        return employeeRepository.findByUserId(userId)
                .orElseGet(() -> employeeRepository.save(new Employee(UUID.randomUUID(), companyId, userId)));
    }

    /** Roles whose approvals inbox is the whole company rather than their own org. */
    private boolean seesEveryone(AuthPrincipal principal) {
        return orgScope.seesWholeCompany(principal);
    }

    /**
     * Whether {@code employeeId} sits anywhere beneath the caller in the reporting tree.
     *
     * <p>Was a single-level check against {@code manager_id}. That is wrong at the first company with
     * three layers: a head of department whose leads are away could not approve anything, because
     * nobody in the queue reported to them <em>directly</em>. Anyone up the chain can now decide, which
     * is what happens in practice when the direct manager is the one on holiday.
     *
     * <p>The signature still takes the caller's <em>user</em> id and resolves the employee row inside,
     * so a call site cannot pass the user id it happens to hold where an employee id is meant and
     * silently match nobody.
     */
    private boolean isMyReport(UUID companyId, UUID employeeId, UUID managerUserId) {
        UUID managerEmployeeId = employeeRepository.findByUserId(managerUserId)
                .filter(e -> companyId.equals(e.getCompanyId()))
                .map(Employee::getId)
                .orElse(null);
        if (managerEmployeeId == null) {
            return false;
        }
        return orgScope.downlineOf(managerEmployeeId, false).contains(employeeId);
    }

    private String nameOf(Employee employee) {
        return userRepository.findById(employee.getUserId()).map(User::fullName).orElse("Unknown");
    }

    private String nameOfEmployeeId(UUID employeeId) {
        return employeeRepository.findById(employeeId).map(this::nameOf).orElse("Unknown");
    }
}
