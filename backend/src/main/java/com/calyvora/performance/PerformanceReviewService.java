package com.calyvora.performance;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.TenantContext;
import com.calyvora.identity.Role;
import com.calyvora.identity.User;
import com.calyvora.identity.UserRepository;
import com.calyvora.notification.NotificationService;
import com.calyvora.notification.NotificationType;
import com.calyvora.people.CompensationService;
import com.calyvora.people.Employee;
import com.calyvora.people.EmployeeRepository;
import com.calyvora.people.EmploymentStatus;
import com.calyvora.people.Goal;
import com.calyvora.people.GoalRepository;
import com.calyvora.people.GoalStatus;
import com.calyvora.people.dto.AddCompensationRequest;
import com.calyvora.people.dto.CompensationResponse;
import com.calyvora.people.dto.GoalResponse;
import com.calyvora.performance.dto.CreateCycleRequest;
import com.calyvora.performance.dto.ManagerReviewRequest;
import com.calyvora.performance.dto.PerformanceReviewResponse;
import com.calyvora.performance.dto.ReviewCycleResponse;
import com.calyvora.performance.dto.SelfAssessmentRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Performance review cycles (founder request C.7). An Owner/Admin opens a cycle, which fans out one
 * review per active employee. The member writes a self-assessment, their manager writes the review
 * and a hike recommendation, and an admin approves — approval applies the raise to compensation.
 *
 * <p>Authorization is by relationship, not just role: a review is visible/editable by the employee
 * (self side), their reporting manager (manager side), or any admin. That mirrors {@code GoalService}.
 */
@Service
public class PerformanceReviewService {

    private final ReviewCycleRepository cycleRepository;
    private final PerformanceReviewRepository reviewRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final GoalRepository goalRepository;
    private final CompensationService compensationService;
    private final NotificationService notificationService;

    public PerformanceReviewService(ReviewCycleRepository cycleRepository,
                                    PerformanceReviewRepository reviewRepository,
                                    EmployeeRepository employeeRepository, UserRepository userRepository,
                                    GoalRepository goalRepository, CompensationService compensationService,
                                    NotificationService notificationService) {
        this.cycleRepository = cycleRepository;
        this.reviewRepository = reviewRepository;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.goalRepository = goalRepository;
        this.compensationService = compensationService;
        this.notificationService = notificationService;
    }

    // ---------- cycles (admin) ----------

    @Transactional
    public ReviewCycleResponse createCycle(CreateCycleRequest req, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        LocalDate start = LocalDate.parse(req.periodStart());
        LocalDate end = LocalDate.parse(req.periodEnd());
        if (end.isBefore(start)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Period end must be on or after the start");
        }
        ReviewCycle cycle = new ReviewCycle(UUID.randomUUID(), companyId, req.name().trim(), start, end,
                principal.userId());
        cycleRepository.save(cycle);

        // Fan out one review per active employee, snapshotting their current manager.
        for (Employee emp : employeeRepository.findByCompanyId(companyId)) {
            if (emp.getEmploymentStatus() != EmploymentStatus.ACTIVE) continue;
            PerformanceReview review = new PerformanceReview(UUID.randomUUID(), companyId, cycle.getId(),
                    emp.getId(), emp.getManagerId());
            reviewRepository.save(review);
            // Ask the member for their self-assessment.
            notificationService.send(companyId, emp.getUserId(), principal.userId(),
                    NotificationType.REVIEW_STARTED, "Review started: " + cycle.getName(),
                    "Add your self-assessment for " + cycle.getName(), "/me/review", "REVIEW", review.getId());
        }
        return rollup(cycle);
    }

    @Transactional(readOnly = true)
    public List<ReviewCycleResponse> cycles() {
        UUID companyId = TenantContext.getCompanyId();
        return cycleRepository.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .map(this::rollup).toList();
    }

    /** All reviews in a cycle — the admin's "who achieved what" view feeding hike decisions. */
    @Transactional(readOnly = true)
    public List<PerformanceReviewResponse> cycleReviews(UUID cycleId) {
        UUID companyId = TenantContext.getCompanyId();
        requireCycle(cycleId, companyId);
        return reviewRepository.findByCycleIdOrderByCreatedAtAsc(cycleId).stream()
                .map(this::toResponse).toList();
    }

    @Transactional
    public ReviewCycleResponse closeCycle(UUID cycleId) {
        UUID companyId = TenantContext.getCompanyId();
        ReviewCycle cycle = requireCycle(cycleId, companyId);
        cycle.setStatus(ReviewCycleStatus.CLOSED);
        return rollup(cycle);
    }

    // ---------- my reviews (self) ----------

    @Transactional(readOnly = true)
    public List<PerformanceReviewResponse> myReviews(AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        Employee me = employeeRepository.findByUserId(principal.userId())
                .filter(e -> e.getCompanyId().equals(companyId))
                .orElse(null);
        if (me == null) return List.of();
        return reviewRepository.findByEmployeeIdOrderByCreatedAtDesc(me.getId()).stream()
                .map(this::toResponse).toList();
    }

    // ---------- team reviews (manager) ----------

    @Transactional(readOnly = true)
    public List<PerformanceReviewResponse> teamReviews(AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        Employee me = employeeRepository.findByUserId(principal.userId())
                .filter(e -> e.getCompanyId().equals(companyId))
                .orElse(null);
        if (me == null) return List.of();
        return reviewRepository.findByManagerIdOrderByCreatedAtDesc(me.getId()).stream()
                .map(this::toResponse).toList();
    }

    // ---------- a single review ----------

    @Transactional(readOnly = true)
    public PerformanceReviewResponse get(UUID reviewId, AuthPrincipal principal) {
        PerformanceReview review = requireReview(reviewId);
        requireCanView(review, principal);
        return toResponse(review);
    }

    /** Member saves/submits their self-assessment. */
    @Transactional
    public PerformanceReviewResponse saveSelf(UUID reviewId, SelfAssessmentRequest req, AuthPrincipal principal) {
        PerformanceReview review = requireReview(reviewId);
        Employee employee = requireEmployee(review.getEmployeeId());
        if (!employee.getUserId().equals(principal.userId())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Only you can write your own self-assessment");
        }
        requireCycleOpen(review);
        if (review.getStatus() != ReviewStatus.PENDING_SELF && review.getStatus() != ReviewStatus.PENDING_MANAGER) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "This review is no longer open for self-assessment");
        }
        review.setSelfAssessment(blankToNull(req.selfAssessment()));
        if (req.submit()) {
            review.setSelfSubmittedAt(java.time.Instant.now());
            if (review.getStatus() == ReviewStatus.PENDING_SELF) {
                review.setStatus(ReviewStatus.PENDING_MANAGER);
            }
            // Nudge the manager that the review is theirs now.
            notifyManager(review, principal.userId(), NotificationType.REVIEW_SELF_SUBMITTED,
                    "Self-assessment submitted", nameOf(employee) + " submitted their self-assessment");
        }
        return toResponse(review);
    }

    /** Manager saves/submits their review + hike recommendation. */
    @Transactional
    public PerformanceReviewResponse saveManager(UUID reviewId, ManagerReviewRequest req, AuthPrincipal principal) {
        PerformanceReview review = requireReview(reviewId);
        requireIsManagerOrAdmin(review, principal);
        requireCycleOpen(review);
        if (review.getStatus() == ReviewStatus.APPROVED) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "This review has already been approved");
        }

        if (req.rating() != null) review.setManagerRating(req.rating());
        if (req.summary() != null) review.setManagerSummary(blankToNull(req.summary()));
        if (req.strengths() != null) review.setStrengths(blankToNull(req.strengths()));
        if (req.improvements() != null) review.setImprovements(blankToNull(req.improvements()));
        if (req.hikeNote() != null) review.setHikeNote(blankToNull(req.hikeNote()));

        HikeType hikeType = parseHikeType(req.hikeType());
        if (hikeType != null) {
            review.setHikeType(hikeType);
            switch (hikeType) {
                case PERCENT -> {
                    review.setHikePercent(req.hikePercent());
                    review.setProposedSalary(null);
                }
                case NEW_SALARY -> {
                    review.setProposedSalary(req.proposedSalary());
                    review.setHikePercent(null);
                }
                case NONE -> {
                    review.setHikePercent(null);
                    review.setProposedSalary(null);
                }
            }
        }

        if (req.submit()) {
            if (review.getManagerRating() == null) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "Give a rating before submitting the review");
            }
            review.setStatus(ReviewStatus.SUBMITTED);
            review.setManagerSubmittedAt(java.time.Instant.now());
            // Let the member know their review is in, and admins that one awaits approval.
            Employee employee = requireEmployee(review.getEmployeeId());
            notificationService.send(review.getCompanyId(), employee.getUserId(), principal.userId(),
                    NotificationType.REVIEW_SUBMITTED, "Your review is ready",
                    "Your manager submitted your review", "/me/review", "REVIEW", review.getId());
            notificationService.sendAll(review.getCompanyId(), adminUserIds(review.getCompanyId()), principal.userId(),
                    NotificationType.REVIEW_SUBMITTED, "Review awaiting approval",
                    nameOf(employee) + "'s review is ready to approve", "/performance", "REVIEW", review.getId());
        }
        return toResponse(review);
    }

    /** Admin approves a submitted review; a recommended hike is written into compensation. */
    @Transactional
    public PerformanceReviewResponse approve(UUID reviewId, AuthPrincipal principal) {
        PerformanceReview review = requireReview(reviewId);
        if (review.getStatus() != ReviewStatus.SUBMITTED) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Only a submitted review can be approved");
        }
        UUID companyId = review.getCompanyId();
        Employee employee = requireEmployee(review.getEmployeeId());

        BigDecimal newAnnual = resolveNewSalary(review);
        if (newAnnual != null && newAnnual.signum() > 0) {
            CompensationResponse comp = compensationService.forEmployee(review.getEmployeeId());
            String reason = "Performance review: " + cycleName(review.getCycleId())
                    + (review.getManagerRating() == null ? "" : " (rating " + review.getManagerRating() + "/5)");
            String currency = comp.currency() == null ? "USD" : comp.currency();
            compensationService.add(review.getEmployeeId(),
                    new AddCompensationRequest(newAnnual, LocalDate.now().toString(), currency, reason), principal);
            // We record that a raise was applied; the exact comp row is the latest for this employee.
            review.setAppliedCompId(review.getId());
        }

        review.setStatus(ReviewStatus.APPROVED);
        review.setDecidedBy(principal.userId());
        review.setDecidedAt(java.time.Instant.now());

        notificationService.send(companyId, employee.getUserId(), principal.userId(),
                NotificationType.REVIEW_APPROVED, "Review approved",
                newAnnual == null ? "Your review is finalized" : "Your review is finalized and a raise was applied",
                "/me/review", "REVIEW", review.getId());
        return toResponse(review);
    }

    // ---------- helpers ----------

    /** The new annual salary a hike implies, or null when there's no raise to apply. */
    private BigDecimal resolveNewSalary(PerformanceReview review) {
        HikeType type = review.getHikeType();
        if (type == null || type == HikeType.NONE) return null;
        if (type == HikeType.NEW_SALARY) {
            return review.getProposedSalary();
        }
        // PERCENT — grow current pay; needs a current salary to grow from.
        if (review.getHikePercent() == null) return null;
        CompensationResponse comp = compensationService.forEmployee(review.getEmployeeId());
        if (comp.currentAnnual() == null || comp.currentAnnual().signum() <= 0) return null;
        BigDecimal factor = BigDecimal.ONE.add(review.getHikePercent().divide(BigDecimal.valueOf(100)));
        return comp.currentAnnual().multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    private ReviewCycleResponse rollup(ReviewCycle cycle) {
        int total = (int) reviewRepository.countByCycleId(cycle.getId());
        int submitted = (int) reviewRepository.countByCycleIdAndStatus(cycle.getId(), ReviewStatus.SUBMITTED);
        int approved = (int) reviewRepository.countByCycleIdAndStatus(cycle.getId(), ReviewStatus.APPROVED);
        return ReviewCycleResponse.of(cycle, total, submitted, approved);
    }

    private PerformanceReviewResponse toResponse(PerformanceReview review) {
        Employee employee = employeeRepository.findById(review.getEmployeeId()).orElse(null);
        String employeeName = employee == null ? "Employee" : nameOf(employee);
        String jobTitle = employee == null ? null : employee.getJobTitle();
        String managerName = review.getManagerId() == null ? null
                : employeeRepository.findById(review.getManagerId()).map(this::nameOf).orElse(null);

        ReviewCycle cycle = cycleRepository.findById(review.getCycleId()).orElse(null);
        String cycleName = cycle == null ? "" : cycle.getName();
        String periodStart = cycle == null ? null : cycle.getPeriodStart().toString();
        String periodEnd = cycle == null ? null : cycle.getPeriodEnd().toString();
        String cycleStatus = cycle == null ? null : cycle.getStatus().name();

        // Goals rollup — what they were working toward this period.
        List<Goal> goals = goalRepository.findByEmployeeIdOrderByCreatedAtDesc(review.getEmployeeId());
        int achieved = (int) goals.stream().filter(g -> g.getStatus() == GoalStatus.ACHIEVED).count();
        List<GoalResponse> goalDtos = goals.stream().map(GoalResponse::of).toList();

        String currency = "USD";
        BigDecimal currentSalary = null;
        try {
            CompensationResponse comp = compensationService.forEmployee(review.getEmployeeId());
            currency = comp.currency() == null ? "USD" : comp.currency();
            currentSalary = comp.currentAnnual();
        } catch (RuntimeException ignored) {
            // No salary on record yet — leave it null; the review still stands.
        }

        return PerformanceReviewResponse.of(review, cycleName, periodStart, periodEnd, cycleStatus,
                employeeName, jobTitle, managerName, currency, currentSalary, achieved, goals.size(), goalDtos);
    }

    private void notifyManager(PerformanceReview review, UUID actorId, NotificationType type,
                               String title, String body) {
        if (review.getManagerId() == null) return;
        employeeRepository.findById(review.getManagerId()).ifPresent(mgr ->
                notificationService.send(review.getCompanyId(), mgr.getUserId(), actorId, type, title, body,
                        "/performance/team", "REVIEW", review.getId()));
    }

    private ReviewCycle requireCycle(UUID cycleId, UUID companyId) {
        return cycleRepository.findByIdAndCompanyId(cycleId, companyId)
                .orElseThrow(() -> new NotFoundException("Review cycle not found"));
    }

    private PerformanceReview requireReview(UUID reviewId) {
        return reviewRepository.findByIdAndCompanyId(reviewId, TenantContext.getCompanyId())
                .orElseThrow(() -> new NotFoundException("Review not found"));
    }

    private Employee requireEmployee(UUID employeeId) {
        return employeeRepository.findByIdAndCompanyId(employeeId, TenantContext.getCompanyId())
                .orElseThrow(() -> new NotFoundException("Employee not found"));
    }

    private void requireCycleOpen(PerformanceReview review) {
        ReviewCycle cycle = cycleRepository.findById(review.getCycleId()).orElse(null);
        if (cycle != null && cycle.getStatus() == ReviewCycleStatus.CLOSED) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "This review cycle is closed");
        }
    }

    /** Anyone on the review: the employee, their manager, or an admin. */
    private void requireCanView(PerformanceReview review, AuthPrincipal principal) {
        if (isAdmin(principal) || isSelf(review, principal) || isManager(review, principal)) return;
        throw new ApiException(ErrorCode.FORBIDDEN, "You can't view this review");
    }

    private void requireIsManagerOrAdmin(PerformanceReview review, AuthPrincipal principal) {
        if (isAdmin(principal) || isManager(review, principal)) return;
        throw new ApiException(ErrorCode.FORBIDDEN, "Only the reporting manager or an admin can write this review");
    }

    private boolean isAdmin(AuthPrincipal principal) {
        return "OWNER".equals(principal.role()) || "ADMIN".equals(principal.role());
    }

    private boolean isSelf(PerformanceReview review, AuthPrincipal principal) {
        return employeeRepository.findById(review.getEmployeeId())
                .map(e -> e.getUserId().equals(principal.userId())).orElse(false);
    }

    private boolean isManager(PerformanceReview review, AuthPrincipal principal) {
        if (review.getManagerId() == null) return false;
        return employeeRepository.findById(review.getManagerId())
                .map(e -> e.getUserId().equals(principal.userId())).orElse(false);
    }

    private List<UUID> adminUserIds(UUID companyId) {
        return userRepository.findByCompanyIdOrderByCreatedAtAsc(companyId).stream()
                .filter(u -> u.getRole() == Role.OWNER || u.getRole() == Role.ADMIN)
                .map(User::getId)
                .toList();
    }

    private String cycleName(UUID cycleId) {
        return cycleRepository.findById(cycleId).map(ReviewCycle::getName).orElse("review");
    }

    private String nameOf(Employee employee) {
        return userRepository.findById(employee.getUserId()).map(User::fullName).orElse("Employee");
    }

    private static HikeType parseHikeType(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return HikeType.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Invalid hike type");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
