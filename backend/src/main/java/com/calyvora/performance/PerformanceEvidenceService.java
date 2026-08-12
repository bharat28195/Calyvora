package com.calyvora.performance;

import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.TenantContext;
import com.calyvora.identity.User;
import com.calyvora.identity.UserRepository;
import com.calyvora.people.AttendanceRecord;
import com.calyvora.people.AttendanceRepository;
import com.calyvora.people.AttendanceStatus;
import com.calyvora.people.Employee;
import com.calyvora.people.EmployeeRepository;
import com.calyvora.performance.dto.ReviewEvidenceResponse;
import com.calyvora.work.ContributionService;
import com.calyvora.work.dto.ContributionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Assembles the evidence pack behind a review (PD-22, D1): what the employee actually delivered
 * between the cycle's period dates, plus how present they were.
 *
 * <p>Authorization is delegated wholesale to {@link PerformanceReviewService#requireViewable} — the
 * evidence is exactly as sensitive as the review it belongs to, so the two must never diverge.
 */
@Service
public class PerformanceEvidenceService {

    private final PerformanceReviewService reviewService;
    private final ReviewCycleRepository cycleRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final ContributionService contributionService;
    private final AttendanceRepository attendanceRepository;

    public PerformanceEvidenceService(PerformanceReviewService reviewService,
                                      ReviewCycleRepository cycleRepository,
                                      EmployeeRepository employeeRepository,
                                      UserRepository userRepository,
                                      ContributionService contributionService,
                                      AttendanceRepository attendanceRepository) {
        this.reviewService = reviewService;
        this.cycleRepository = cycleRepository;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.contributionService = contributionService;
        this.attendanceRepository = attendanceRepository;
    }

    @Transactional(readOnly = true)
    public ReviewEvidenceResponse forReview(UUID reviewId, AuthPrincipal principal) {
        PerformanceReview review = reviewService.requireViewable(reviewId, principal);

        ReviewCycle cycle = cycleRepository.findByIdAndCompanyId(review.getCycleId(), TenantContext.getCompanyId())
                .orElseThrow(() -> new NotFoundException("Review cycle not found"));
        LocalDate from = cycle.getPeriodStart();
        LocalDate to = cycle.getPeriodEnd();

        Employee employee = employeeRepository.findByIdAndCompanyId(review.getEmployeeId(),
                TenantContext.getCompanyId()).orElse(null);
        String employeeName = employee == null ? "Employee"
                : userRepository.findById(employee.getUserId()).map(User::fullName).orElse("Employee");

        ContributionResponse work = contributionService.forEmployee(review.getEmployeeId(), from, to);
        ReviewEvidenceResponse.AttendanceSummary attendance = summarise(review.getEmployeeId(), from, to);

        // "Empty" is a first-class answer, not a failure: a new hire, or someone whose work lives
        // outside Work OS, genuinely has no delivery record — the UI says so rather than implying
        // they did nothing.
        boolean empty = work.tasksDelivered() == 0 && work.ticketsResolved() == 0;

        return new ReviewEvidenceResponse(review.getId().toString(), employeeName,
                from.toString(), to.toString(), work, attendance, empty);
    }

    private ReviewEvidenceResponse.AttendanceSummary summarise(UUID employeeId, LocalDate from, LocalDate to) {
        List<AttendanceRecord> records =
                attendanceRepository.findByEmployeeIdAndDateBetweenOrderByDateAsc(employeeId, from, to);
        if (records.isEmpty()) {
            return ReviewEvidenceResponse.AttendanceSummary.empty();
        }
        UUID companyId = TenantContext.getCompanyId();
        int present = 0, wfh = 0, half = 0, leave = 0, absent = 0, counted = 0;
        for (AttendanceRecord r : records) {
            if (!companyId.equals(r.getCompanyId())) {
                continue;   // belt-and-suspenders alongside RLS, as elsewhere
            }
            AttendanceStatus status = r.getStatus();
            if (status == null || status.isNonWorkingDay()) {
                continue;   // holidays and week-offs are not a presence signal
            }
            counted++;
            switch (status) {
                case PRESENT -> present++;
                case WORK_FROM_HOME -> wfh++;
                case HALF_DAY -> half++;
                case ON_LEAVE -> leave++;
                case ABSENT -> absent++;
                default -> { }
            }
        }
        return new ReviewEvidenceResponse.AttendanceSummary(counted, present, wfh, half, leave, absent);
    }
}
