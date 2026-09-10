package com.calyvora.expense;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.error.ForbiddenException;
import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.TenantContext;
import com.calyvora.expense.dto.ExpensePayload;
import com.calyvora.expense.dto.ExpenseResponse;
import com.calyvora.expense.dto.ExpenseSummaryResponse;
import com.calyvora.identity.User;
import com.calyvora.identity.UserRepository;
import com.calyvora.notification.NotificationService;
import com.calyvora.notification.NotificationType;
import com.calyvora.people.Employee;
import com.calyvora.people.EmployeeRepository;
import com.calyvora.people.EmployeeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Expense claims (founder request: "claim expenses for official travel"). An employee submits;
 * their manager — or any Owner/Admin — approves, rejects, and later marks it reimbursed.
 *
 * <p>Deliberate: a claim can only be edited while it's still {@code SUBMITTED}. Once someone has
 * decided on it, changing the amount would make the decision a lie.
 */
@Service
public class ExpenseService {

    private final ExpenseClaimRepository claimRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeService employeeService;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final com.calyvora.people.OrgScope orgScope;

    public ExpenseService(ExpenseClaimRepository claimRepository, EmployeeRepository employeeRepository,
                          EmployeeService employeeService, UserRepository userRepository,
                          NotificationService notificationService,
                          com.calyvora.people.OrgScope orgScope) {
        this.orgScope = orgScope;
        this.claimRepository = claimRepository;
        this.employeeRepository = employeeRepository;
        this.employeeService = employeeService;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    // ---- mine ----

    @Transactional
    public ExpenseSummaryResponse mine(AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        Employee me = self(companyId, principal);
        List<ExpenseClaim> claims = claimRepository.findByEmployeeIdOrderByCreatedAtDesc(me.getId());
        return summarize(claims, nameCache(companyId));
    }

    @Transactional
    public ExpenseResponse submit(ExpensePayload p, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        Employee me = self(companyId, principal);

        if (p.title() == null || p.title().isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "What was the expense for?");
        }
        if (p.amount() == null || p.amount().signum() <= 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Amount must be more than zero");
        }
        LocalDate spentOn = p.spentOn() == null || p.spentOn().isBlank()
                ? LocalDate.now() : LocalDate.parse(p.spentOn());
        if (spentOn.isAfter(LocalDate.now())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "You can't claim for a future date");
        }

        ExpenseClaim claim = new ExpenseClaim(UUID.randomUUID(), companyId, me.getId(), p.title().trim(),
                p.category() == null ? ExpenseCategory.OTHER : ExpenseCategory.valueOf(p.category()),
                p.amount(), currencyOf(p), spentOn);
        claim.setDescription(blankToNull(p.description()));
        claim.setReceiptUrl(blankToNull(p.receiptUrl()));
        claimRepository.save(claim);

        String who = nameOf(companyId, me);
        notificationService.sendAll(companyId, approversFor(companyId, me), principal.userId(),
                NotificationType.ANNOUNCEMENT,
                who + " claimed " + claim.getCurrency() + " " + claim.getAmount(),
                claim.getTitle() + " · " + claim.getCategory().name().toLowerCase(),
                "/expenses", "EXPENSE_CLAIM", claim.getId());

        return ExpenseResponse.of(claim, who);
    }

    @Transactional
    public ExpenseResponse update(UUID claimId, ExpensePayload p, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        ExpenseClaim claim = require(claimId, companyId);
        Employee me = self(companyId, principal);
        if (!claim.getEmployeeId().equals(me.getId())) {
            throw new ForbiddenException("You can only edit your own claims");
        }
        if (claim.getStatus() != ExpenseStatus.SUBMITTED) {
            throw new ApiException(ErrorCode.CONFLICT, "This claim has already been decided");
        }
        if (p.title() != null && !p.title().isBlank()) claim.setTitle(p.title().trim());
        if (p.amount() != null && p.amount().signum() > 0) claim.setAmount(p.amount());
        if (p.category() != null) claim.setCategory(ExpenseCategory.valueOf(p.category()));
        if (p.spentOn() != null && !p.spentOn().isBlank()) claim.setSpentOn(LocalDate.parse(p.spentOn()));
        if (p.description() != null) claim.setDescription(blankToNull(p.description()));
        if (p.receiptUrl() != null) claim.setReceiptUrl(blankToNull(p.receiptUrl()));
        return ExpenseResponse.of(claim, nameOf(companyId, me));
    }

    @Transactional
    public void withdraw(UUID claimId, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        ExpenseClaim claim = require(claimId, companyId);
        Employee me = self(companyId, principal);
        if (!claim.getEmployeeId().equals(me.getId())) {
            throw new ForbiddenException("You can only withdraw your own claims");
        }
        if (claim.getStatus() != ExpenseStatus.SUBMITTED) {
            throw new ApiException(ErrorCode.CONFLICT, "This claim has already been decided");
        }
        claimRepository.delete(claim);
    }

    // ---- approving (Owner/Admin) ----

    @Transactional(readOnly = true)
    public ExpenseSummaryResponse all() {
        UUID companyId = TenantContext.getCompanyId();
        return summarize(claimRepository.findByCompanyIdOrderByCreatedAtDesc(companyId), nameCache(companyId));
    }

    @Transactional
    public ExpenseResponse decide(UUID claimId, boolean approve, String note, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        ExpenseClaim claim = require(claimId, companyId);
        // This class has always documented itself as "their manager — or any Owner/Admin — approves",
        // but the endpoint was gated on the OWNER/ADMIN roles alone, so a lead could watch their
        // team's claims sit there and do nothing about them.
        //
        // The role check cannot be the whole answer: it can say "a lead may decide expenses", never
        // "this lead may decide THIS claim". Opening the endpoint by role alone would let any lead
        // approve anyone's spending — a wider hole than the one being closed. The tree decides, which
        // is the same rule leave already applies (PD-32).
        requireCanDecide(companyId, claim, principal);
        if (claim.getStatus() != ExpenseStatus.SUBMITTED) {
            throw new ApiException(ErrorCode.CONFLICT, "This claim has already been decided");
        }
        claim.decide(approve ? ExpenseStatus.APPROVED : ExpenseStatus.REJECTED, principal.userId(),
                blankToNull(note));
        notifyClaimant(companyId, claim, principal,
                "Your expense claim was " + (approve ? "approved" : "declined"));
        return ExpenseResponse.of(claim, nameOfEmployeeId(companyId, claim.getEmployeeId()));
    }

    /** Marks an approved claim as actually paid — the step that closes the loop for the claimant. */
    @Transactional
    public ExpenseResponse reimburse(UUID claimId, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        ExpenseClaim claim = require(claimId, companyId);
        if (claim.getStatus() != ExpenseStatus.APPROVED) {
            throw new ApiException(ErrorCode.CONFLICT, "Only an approved claim can be reimbursed");
        }
        claim.reimburse();
        notifyClaimant(companyId, claim, principal, "Your expense claim was reimbursed");
        return ExpenseResponse.of(claim, nameOfEmployeeId(companyId, claim.getEmployeeId()));
    }

    // ---- helpers ----

    private ExpenseSummaryResponse summarize(List<ExpenseClaim> claims, Map<UUID, String> names) {
        BigDecimal pending = BigDecimal.ZERO;
        BigDecimal awaiting = BigDecimal.ZERO;
        BigDecimal reimbursed = BigDecimal.ZERO;
        int year = LocalDate.now().getYear();

        for (ExpenseClaim c : claims) {
            switch (c.getStatus()) {
                case SUBMITTED -> pending = pending.add(c.getAmount());
                case APPROVED -> awaiting = awaiting.add(c.getAmount());
                case REIMBURSED -> {
                    if (c.getReimbursedAt() != null
                            && c.getReimbursedAt().atZone(ZoneId.systemDefault()).getYear() == year) {
                        reimbursed = reimbursed.add(c.getAmount());
                    }
                }
                default -> { /* rejected claims count towards nothing */ }
            }
        }
        String currency = claims.isEmpty() ? "INR" : claims.get(0).getCurrency();
        List<ExpenseResponse> rows = claims.stream()
                .map(c -> ExpenseResponse.of(c, names.get(c.getEmployeeId())))
                .toList();
        return new ExpenseSummaryResponse(rows, pending, awaiting, reimbursed, currency);
    }

    /** Who decides this person's claims: their manager, else every Owner/Admin (mirrors leave). */
    private List<UUID> approversFor(UUID companyId, Employee employee) {
        if (employee.getManagerId() != null) {
            var manager = employeeRepository.findByIdAndCompanyId(employee.getManagerId(), companyId);
            if (manager.isPresent()) {
                return List.of(manager.get().getUserId());
            }
        }
        return userRepository.findByCompanyIdOrderByCreatedAtAsc(companyId).stream()
                .filter(u -> u.getRole() == com.calyvora.identity.Role.OWNER
                        || u.getRole() == com.calyvora.identity.Role.ADMIN)
                .map(User::getId)
                .toList();
    }

    private void notifyClaimant(UUID companyId, ExpenseClaim claim, AuthPrincipal principal, String title) {
        employeeRepository.findById(claim.getEmployeeId()).ifPresent(employee ->
                notificationService.send(companyId, employee.getUserId(), principal.userId(),
                        NotificationType.ANNOUNCEMENT, title,
                        claim.getTitle() + " · " + claim.getCurrency() + " " + claim.getAmount(),
                        "/me/expenses", "EXPENSE_CLAIM", claim.getId()));
    }

    private Employee self(UUID companyId, AuthPrincipal principal) {
        UUID employeeId = employeeService.ensureEmployeeId(companyId, principal.userId());
        return employeeRepository.findByIdAndCompanyId(employeeId, companyId)
                .orElseThrow(() -> new NotFoundException("No employee profile for this user"));
    }

    /**
     * Who may approve or reject this claim: whoever the claimant reports to, at any depth, plus the
     * people whose job is the whole company.
     *
     * <p>Transitive on purpose. A head of department with four leads under them should be able to
     * clear a claim from one of the thirty people beneath those leads — which is exactly what has to
     * happen when the direct manager is the one on holiday. Leave and regularization already work
     * this way; expenses were the last approval still stuck at the role.
     *
     * <p>Note what this deliberately does <em>not</em> use: {@code OrgScope.seesWholeCompany}, which
     * the leave and performance checks do use. That set includes HR, and HR has never been able to
     * approve spending here. {@code seesWholeCompany} answers "who may this person <em>see</em>",
     * and reusing it for authority would quietly hand HR a power over money that nobody granted them.
     * Visibility and authority are different questions and this class needs the second one.
     *
     * <p>A lead cannot approve their own claim, because {@code downlineOf} never contains the person
     * it is asked about. An Owner/Admin still can, and that is left alone deliberately: in a
     * five-person company the admin is frequently the only approver, and forbidding self-approval
     * would leave them unable to claim expenses at all. Separation of duties there is a policy
     * decision with real consequences for small customers, not a defect to fix in passing.
     */
    private void requireCanDecide(UUID companyId, ExpenseClaim claim, AuthPrincipal principal) {
        if ("OWNER".equals(principal.role()) || "ADMIN".equals(principal.role())) {
            return;
        }
        UUID mine = employeeRepository.findByUserId(principal.userId())
                .filter(e -> companyId.equals(e.getCompanyId()))
                .map(Employee::getId)
                .orElse(null);
        if (mine == null || !orgScope.downlineOf(mine, false).contains(claim.getEmployeeId())) {
            throw new ForbiddenException("You can only decide expenses for people who report to you");
        }
    }

    private ExpenseClaim require(UUID claimId, UUID companyId) {
        return claimRepository.findByIdAndCompanyId(claimId, companyId)
                .orElseThrow(() -> new NotFoundException("Claim not found"));
    }

    private Map<UUID, String> nameCache(UUID companyId) {
        Map<UUID, String> names = new HashMap<>();
        for (Employee e : employeeRepository.findByCompanyId(companyId)) {
            names.put(e.getId(), nameOf(companyId, e));
        }
        return names;
    }

    private String nameOf(UUID companyId, Employee employee) {
        return userRepository.findByIdAndCompanyId(employee.getUserId(), companyId)
                .map(u -> (u.getFirstName() + " " + u.getLastName()).trim())
                .orElse("Employee");
    }

    private String nameOfEmployeeId(UUID companyId, UUID employeeId) {
        return employeeRepository.findByIdAndCompanyId(employeeId, companyId)
                .map(e -> nameOf(companyId, e)).orElse("Employee");
    }

    private static String currencyOf(ExpensePayload p) {
        return p.currency() == null || p.currency().isBlank() ? "INR" : p.currency().toUpperCase();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
