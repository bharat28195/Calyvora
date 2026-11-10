package com.calyvora.people;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.TenantContext;
import com.calyvora.document.DocumentKind;
import com.calyvora.document.DocumentService;
import com.calyvora.document.GeneratedDocument;
import com.calyvora.document.GeneratedDocumentRepository;
import com.calyvora.identity.UserRepository;
import com.calyvora.people.dto.ExitResponse;
import com.calyvora.people.dto.OnboardingTaskResponse;
import com.calyvora.people.dto.StartExitRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Exit formalities (PD-20): the sequence that runs when somebody resigns, so it stops living in an
 * HR person's memory.
 *
 * <p>Starting an exit does three things at once — records the last working day, moves the employee to
 * {@code NOTICE}, and raises the clearance checklist their manager works through. Completing it
 * issues the relieving letter and experience certificate off the company letterpad and marks them
 * {@code TERMINATED}.
 *
 * <p>The one rule worth stating: completion is refused while clearance is outstanding. A relieving
 * letter says dues are settled and property returned, and issuing it before that is true is how a
 * company ends up having certified something it cannot stand behind. It can be overridden
 * deliberately ({@code force}), because reality has exceptions — but not by accident.
 */
@Service
public class ExitService {

    /** Statuses an exit can be started from. Someone already leaving is not started again. */
    private static final Set<EmploymentStatus> CAN_START =
            EnumSet.of(EmploymentStatus.ACTIVE, EmploymentStatus.ONBOARDING);

    private final EmployeeRepository employeeRepository;
    private final OnboardingTaskRepository taskRepository;
    private final OnboardingService onboardingService;
    private final DocumentService documentService;
    private final GeneratedDocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final OrgScope orgScope;
    private final com.calyvora.access.PermissionService permissions;
    private final com.calyvora.notification.NotificationService notifications;

    public ExitService(EmployeeRepository employeeRepository,
                       OnboardingTaskRepository taskRepository,
                       OnboardingService onboardingService,
                       DocumentService documentService,
                       GeneratedDocumentRepository documentRepository,
                       UserRepository userRepository,
                       OrgScope orgScope,
                       com.calyvora.access.PermissionService permissions,
                       com.calyvora.notification.NotificationService notifications) {
        this.orgScope = orgScope;
        this.permissions = permissions;
        this.notifications = notifications;
        this.employeeRepository = employeeRepository;
        this.taskRepository = taskRepository;
        this.onboardingService = onboardingService;
        this.documentService = documentService;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
    }

    /**
     * Start an exit — or, for anyone who may not approve one, ask for it (PD-65).
     *
     * <p>Putting someone on notice changes their pay, their access and what the company tells them, so
     * it takes an admin's yes. HR and managers raise the request; it waits in the admins' inbox, and
     * the employee stays exactly as they were until it is approved. An admin starting one is the
     * approval, so theirs takes effect at once.
     */
    @Transactional
    public ExitResponse start(UUID employeeId, StartExitRequest request, AuthPrincipal principal) {
        Employee employee = requireEmployee(employeeId);
        if (!CAN_START.contains(employee.getEmploymentStatus())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "This employee is already leaving or has already left");
        }
        if (employee.getStartDate() != null && request.lastWorkingDay().isBefore(employee.getStartDate())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "The last working day cannot be before the start date");
        }
        if (!permissions.has(principal, com.calyvora.access.Permission.EXITS_APPROVE)) {
            if (employee.hasExitRequest()) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "An exit for this person is already waiting for approval");
            }
            employee.requestExit(request.lastWorkingDay(), request.reason(), principal.userId(), request.shouldSeedChecklist());
            employeeRepository.save(employee);
            String name = nameOf(employee.getUserId(), TenantContext.getCompanyId());
            notifications.sendAll(TenantContext.getCompanyId(), approverIds(), principal.userId(),
                    com.calyvora.notification.NotificationType.EXIT_REQUESTED,
                    "Exit to approve: " + name,
                    "Last working day " + request.lastWorkingDay() + (request.reason() == null ? "" : " · " + request.reason()),
                    "/inbox", "EMPLOYEE", employee.getId());
            return view(employee, principal);
        }
        employee.clearExitRequest();
        return begin(employee, request.lastWorkingDay(), request.reason(), request.shouldSeedChecklist(), principal);
    }

    /** Exits waiting for an admin. Admins (EXITS_APPROVE) only. */
    @Transactional(readOnly = true)
    public List<ExitResponse> requests(AuthPrincipal principal) {
        requireApprover(principal);
        return employeeRepository.findByCompanyIdAndExitRequestedAtIsNotNull(TenantContext.getCompanyId()).stream()
                .map(e -> view(e, principal)).toList();
    }

    /** Approve a requested exit: the person goes on notice, on the terms that were asked for. */
    @Transactional
    public ExitResponse approve(UUID employeeId, AuthPrincipal principal) {
        requireApprover(principal);
        Employee employee = requireEmployee(employeeId);
        if (!employee.hasExitRequest()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "There is no exit waiting for approval for this person");
        }
        UUID requestedBy = employee.getExitRequestedBy();
        LocalDate lastDay = employee.getExitRequestLastDay();
        String reason = employee.getExitRequestReason();
        boolean seed = employee.exitRequestSeedsChecklist();
        employee.clearExitRequest();
        ExitResponse out = begin(employee, lastDay, reason, seed, principal);
        notifications.send(TenantContext.getCompanyId(), requestedBy, principal.userId(),
                com.calyvora.notification.NotificationType.EXIT_DECIDED,
                "Exit approved: " + out.employeeName(), "Last working day " + lastDay, "/people/exits", "EMPLOYEE", employeeId);
        return out;
    }

    /** Turn a requested exit down. Nothing about the person changes. */
    @Transactional
    public ExitResponse reject(UUID employeeId, AuthPrincipal principal) {
        requireApprover(principal);
        Employee employee = requireEmployee(employeeId);
        if (!employee.hasExitRequest()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "There is no exit waiting for approval for this person");
        }
        UUID requestedBy = employee.getExitRequestedBy();
        employee.clearExitRequest();
        employeeRepository.save(employee);
        ExitResponse out = view(employee, principal);
        notifications.send(TenantContext.getCompanyId(), requestedBy, principal.userId(),
                com.calyvora.notification.NotificationType.EXIT_DECIDED,
                "Exit not approved: " + out.employeeName(), null, "/people/exits", "EMPLOYEE", employeeId);
        return out;
    }

    private ExitResponse begin(Employee employee, LocalDate lastDay, String reason, boolean seedChecklist,
                               AuthPrincipal principal) {
        employee.setEndDate(lastDay);
        employee.setExitReason(reason);
        employee.setExitStartedAt(Instant.now());
        employee.setEmploymentStatus(EmploymentStatus.NOTICE);
        employeeRepository.save(employee);
        if (seedChecklist) {
            onboardingService.seedDefaultsIfEmpty(employee.getId(), ChecklistKind.EXIT);
        }
        return view(employee, principal);
    }

    private void requireApprover(AuthPrincipal principal) {
        if (!permissions.has(principal, com.calyvora.access.Permission.EXITS_APPROVE)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Only an admin can approve an exit");
        }
    }

    /** Who decides: the company's admins (who hold every permission, EXITS_APPROVE included). */
    private List<UUID> approverIds() {
        return userRepository.findByCompanyIdOrderByCreatedAtAsc(TenantContext.getCompanyId()).stream()
                .filter(u -> u.getRole() == com.calyvora.identity.Role.ADMIN)
                .map(com.calyvora.identity.User::getId).toList();
    }

    /** Resignation withdrawn. Clears the exit and the clearance list — none of it happened. */
    @Transactional
    public ExitResponse cancel(UUID employeeId, AuthPrincipal principal) {
        Employee employee = requireEmployee(employeeId);
        if (employee.hasExitRequest() && employee.getEmploymentStatus() != EmploymentStatus.NOTICE) {
            // Withdrawn before an admin decided: just drop the request.
            employee.clearExitRequest();
            employeeRepository.save(employee);
            return view(employee, principal);
        }
        if (employee.getEmploymentStatus() != EmploymentStatus.NOTICE) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "This employee is not serving notice");
        }
        employee.setEndDate(null);
        employee.setExitReason(null);
        employee.setExitStartedAt(null);
        employee.setEmploymentStatus(EmploymentStatus.ACTIVE);
        employeeRepository.save(employee);
        taskRepository.deleteAll(
                taskRepository.findByEmployeeIdAndKindOrderBySortOrderAscCreatedAtAsc(employeeId, ChecklistKind.EXIT));
        return view(employee, principal);
    }

    /**
     * Finish the exit: mark them left and issue the closing letters.
     *
     * @param force skip the "clearance outstanding" guard — a deliberate act, never a default
     */
    @Transactional
    public ExitResponse complete(UUID employeeId, boolean force, AuthPrincipal principal) {
        Employee employee = requireEmployee(employeeId);
        if (employee.getEmploymentStatus() != EmploymentStatus.NOTICE) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Start the exit before completing it");
        }
        OnboardingService.Progress progress = onboardingService.progress(employeeId, ChecklistKind.EXIT);
        if (!force && progress.total() > 0 && !progress.complete()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "%d of %d clearance items are still open. Finish them, or complete the exit anyway."
                            .formatted(progress.total() - progress.done(), progress.total()));
        }
        if (employee.getEndDate() == null) {
            employee.setEndDate(LocalDate.now());
        }
        employee.setEmploymentStatus(EmploymentStatus.TERMINATED);
        employeeRepository.save(employee);

        // Best-effort: a company that deleted these templates has chosen not to issue them, and that
        // is not a reason to fail an exit that has otherwise completed.
        documentService.issueByKind(DocumentKind.RELIEVING_LETTER, employeeId, null, principal);
        documentService.issueByKind(DocumentKind.EXPERIENCE_LETTER, employeeId, null, principal);
        return view(employee, principal);
    }

    @Transactional(readOnly = true)
    public ExitResponse get(UUID employeeId, AuthPrincipal principal) {
        if (!orgScope.seesWholeCompany(principal) && !orgScope.canSee(principal, employeeId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "That person is not in your team.");
        }
        return view(requireEmployee(employeeId), principal);
    }

    /**
     * Who is serving notice — the whole company for HR and leadership, the caller's own org for
     * everyone else.
     *
     * <p>This was unfiltered, so a manager opening Exits saw every resignation in the business,
     * including departments they have nothing to do with and people well senior to them. Who is
     * leaving is among the most sensitive facts an HR system holds before it has been announced, and
     * the screen was already on a manager's nav.
     */
    @Transactional(readOnly = true)
    public List<ExitResponse> leaving(AuthPrincipal principal) {
        List<Employee> onNotice = new java.util.ArrayList<>(employeeRepository.findByCompanyIdAndEmploymentStatus(
                TenantContext.getCompanyId(), EmploymentStatus.NOTICE));
        // Exits still waiting for an admin are listed too, so whoever asked can see where it stands.
        for (Employee e : employeeRepository.findByCompanyIdAndExitRequestedAtIsNotNull(TenantContext.getCompanyId())) {
            if (e.getEmploymentStatus() != EmploymentStatus.NOTICE) onNotice.add(e);
        }
        if (!orgScope.seesWholeCompany(principal)) {
            Set<UUID> mine = orgScope.downline(principal, false);
            onNotice = onNotice.stream().filter(e -> mine.contains(e.getId())).toList();
        }
        return onNotice.stream().map(e -> view(e, principal)).toList();
    }

    // ---- helpers ----

    private ExitResponse view(Employee employee, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        List<OnboardingTaskResponse> checklist = taskRepository
                .findByEmployeeIdAndKindOrderBySortOrderAscCreatedAtAsc(employee.getId(), ChecklistKind.EXIT).stream()
                .map(OnboardingTaskResponse::of)
                .toList();
        int done = (int) checklist.stream().filter(OnboardingTaskResponse::completed).count();

        List<ExitResponse.IssuedLetter> letters = documentRepository
                .findByEmployeeIdOrderByCreatedAtDesc(employee.getId()).stream()
                .filter(d -> d.getKind() == DocumentKind.RELIEVING_LETTER
                        || d.getKind() == DocumentKind.EXPERIENCE_LETTER)
                .map(ExitService::letter)
                .toList();

        return new ExitResponse(
                employee.getId().toString(),
                nameOf(employee.getUserId(), companyId),
                employee.getEmploymentStatus().name(),
                employee.getEndDate() == null ? null : employee.getEndDate().toString(),
                employee.getExitReason(),
                employee.getExitStartedAt() == null ? null : employee.getExitStartedAt().toString(),
                managerName(employee, companyId),
                done,
                checklist.size(),
                !checklist.isEmpty() && done == checklist.size(),
                checklist,
                letters,
                employee.getExitRequestLastDay() == null ? null : employee.getExitRequestLastDay().toString(),
                employee.getExitRequestReason(),
                employee.getExitRequestedBy() == null ? null : nameOf(employee.getExitRequestedBy(), companyId),
                employee.getExitRequestedAt() == null ? null : employee.getExitRequestedAt().toString());
    }

    private static ExitResponse.IssuedLetter letter(GeneratedDocument d) {
        return new ExitResponse.IssuedLetter(d.getId().toString(), d.getKind().name(), d.getTitle(),
                d.getCreatedAt().toString());
    }

    private String managerName(Employee employee, UUID companyId) {
        if (employee.getManagerId() == null) {
            return null;
        }
        return employeeRepository.findByIdAndCompanyId(employee.getManagerId(), companyId)
                .map(m -> nameOf(m.getUserId(), companyId))
                .orElse(null);
    }

    private String nameOf(UUID userId, UUID companyId) {
        return userRepository.findByIdAndCompanyId(userId, companyId)
                .map(u -> u.getFirstName() + " " + u.getLastName())
                .orElse(null);
    }

    private Employee requireEmployee(UUID employeeId) {
        return employeeRepository.findByIdAndCompanyId(employeeId, TenantContext.getCompanyId())
                .orElseThrow(() -> new NotFoundException("Employee not found"));
    }
}
