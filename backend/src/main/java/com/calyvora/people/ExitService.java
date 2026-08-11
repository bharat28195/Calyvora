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

    public ExitService(EmployeeRepository employeeRepository,
                       OnboardingTaskRepository taskRepository,
                       OnboardingService onboardingService,
                       DocumentService documentService,
                       GeneratedDocumentRepository documentRepository,
                       UserRepository userRepository) {
        this.employeeRepository = employeeRepository;
        this.taskRepository = taskRepository;
        this.onboardingService = onboardingService;
        this.documentService = documentService;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
    }

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
        employee.setEndDate(request.lastWorkingDay());
        employee.setExitReason(request.reason());
        employee.setExitStartedAt(Instant.now());
        employee.setEmploymentStatus(EmploymentStatus.NOTICE);
        employeeRepository.save(employee);

        if (request.shouldSeedChecklist()) {
            onboardingService.seedDefaultsIfEmpty(employeeId, ChecklistKind.EXIT);
        }
        return view(employee, principal);
    }

    /** Resignation withdrawn. Clears the exit and the clearance list — none of it happened. */
    @Transactional
    public ExitResponse cancel(UUID employeeId, AuthPrincipal principal) {
        Employee employee = requireEmployee(employeeId);
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
        return view(requireEmployee(employeeId), principal);
    }

    /** Everyone currently serving notice — the exits screen. */
    @Transactional(readOnly = true)
    public List<ExitResponse> leaving(AuthPrincipal principal) {
        return employeeRepository.findByCompanyIdAndEmploymentStatus(
                        TenantContext.getCompanyId(), EmploymentStatus.NOTICE).stream()
                .map(e -> view(e, principal))
                .toList();
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
                letters);
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
