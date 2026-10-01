package com.calyvora.team;

import com.calyvora.common.error.ForbiddenException;
import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.CurrentUser;
import com.calyvora.expense.dto.ExpenseResponse;
import com.calyvora.people.AttendanceService;
import com.calyvora.people.OrgScope;
import com.calyvora.people.dto.AttendanceMonthResponse;
import com.calyvora.people.dto.LeaveRequestResponse;
import com.calyvora.performance.PerformanceReviewService;
import com.calyvora.performance.dto.PerformanceReviewResponse;
import com.calyvora.team.dto.TeamSummaryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * "My team" — readable by anyone who leads people, whatever their role is called.
 *
 * <p>There is no {@code @PreAuthorize} on these routes on purpose. A role check here would be the very
 * mistake this module exists to correct: it would give every MANAGER a team and deny one to the senior
 * engineer with three interns under them. The authorisation is the reporting tree, applied inside
 * {@link TeamService} — an empty downline yields an empty roster, so a caller who leads nobody gets
 * nothing back rather than an error.
 *
 * <p>The per-person routes are the exception and DO fail loudly with 403, because there the caller has
 * named somebody: silently returning an empty month for a colleague they are not entitled to read
 * would look like the colleague never came to work.
 */
@RestController
@RequestMapping("/api/v1/team")
public class TeamController {

    private final TeamService teamService;
    private final AttendanceService attendanceService;
    private final PerformanceReviewService performanceReviewService;
    private final OrgScope orgScope;

    public TeamController(TeamService teamService, AttendanceService attendanceService,
                          PerformanceReviewService performanceReviewService,
                          OrgScope orgScope) {
        this.orgScope = orgScope;
        this.teamService = teamService;
        this.attendanceService = attendanceService;
        this.performanceReviewService = performanceReviewService;
    }

    /**
     * The roster and its headline counts.
     *
     * @param direct when true, only people reporting straight to the caller. The default is the whole
     *               subtree: a lead whose reports are themselves leads would otherwise open "my team"
     *               and see three names out of thirty.
     */
    @GetMapping
    public TeamSummaryResponse summary(@CurrentUser AuthPrincipal principal,
                                       @RequestParam(defaultValue = "false") boolean direct,
                                       @RequestParam(required = false) String month) {
        return teamService.summary(principal, direct, parseMonth(month));
    }

    /**
     * Whether the caller leads anybody, and how many — what decides if the "My team" section appears.
     *
     * <p>Separate from {@link #summary} because the app shell asks this on every page load and the
     * roster is a dozen queries. Two counts and a boolean instead.
     */
    @GetMapping("/mine")
    public TeamStanding mine(@CurrentUser AuthPrincipal principal) {
        OrgScope.Standing s = orgScope.standing(principal);
        return new TeamStanding(s.leadsTeam(), s.directCount(), s.totalCount());
    }

    public record TeamStanding(boolean leadsTeam, int directCount, int totalCount) {}

    @GetMapping("/leave")
    public List<LeaveRequestResponse> leave(@CurrentUser AuthPrincipal principal,
                                            @RequestParam(defaultValue = "false") boolean direct) {
        return teamService.leave(principal, direct);
    }

    @GetMapping("/expenses")
    public List<ExpenseResponse> expenses(@CurrentUser AuthPrincipal principal,
                                          @RequestParam(defaultValue = "false") boolean direct) {
        return teamService.expenses(principal, direct);
    }

    /** Reviews for everyone below the caller. Pay figures are stripped unless the caller is HR. */
    @GetMapping("/performance")
    public List<PerformanceReviewResponse> performance(@CurrentUser AuthPrincipal principal) {
        return performanceReviewService.teamReviews(principal);
    }

    /** One person's month. 403 rather than an empty month when they are not on the caller's team. */
    @GetMapping("/attendance/{employeeId}")
    public AttendanceMonthResponse attendance(@CurrentUser AuthPrincipal principal,
                                              @PathVariable UUID employeeId,
                                              @RequestParam(required = false) String month) {
        if (!teamService.isOnRoster(principal, employeeId, false)) {
            throw new ForbiddenException("That person is not on your team.");
        }
        return attendanceService.month(employeeId, parseMonth(month));
    }

    /**
     * A blank, absent or unparseable month means this month rather than a 400. The parameter is only
     * ever set by the month picker on the page, so a bad value is a bug in our own URL, and answering
     * it with the current month keeps the screen usable instead of blank.
     */
    private static YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(month);
        } catch (RuntimeException e) {
            return YearMonth.now();
        }
    }
}
