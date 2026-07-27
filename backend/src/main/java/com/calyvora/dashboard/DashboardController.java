package com.calyvora.dashboard;

import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.CurrentUser;
import com.calyvora.dashboard.dto.DashboardSummaryResponse;
import com.calyvora.dashboard.dto.TeamOverviewResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final TeamOverviewService teamOverviewService;

    public DashboardController(DashboardService dashboardService, TeamOverviewService teamOverviewService) {
        this.dashboardService = dashboardService;
        this.teamOverviewService = teamOverviewService;
    }

    @GetMapping("/summary")
    public DashboardSummaryResponse summary(@CurrentUser AuthPrincipal principal) {
        return dashboardService.summary(principal.role());
    }

    /** Owner/Admin team overview: headcount, present vs on-leave today, reasons, month leave calendar. */
    @GetMapping("/team")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'HR')")
    public TeamOverviewResponse team() {
        return teamOverviewService.overview();
    }
}
