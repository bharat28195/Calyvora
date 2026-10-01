package com.calyvora.dashboard;

import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.CurrentUser;
import com.calyvora.dashboard.dto.DashboardSummaryResponse;
import com.calyvora.dashboard.dto.TeamOverviewResponse;
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

    /**
     * Team overview: headcount, present vs on-leave today, reasons, month leave calendar — for the
     * people the caller may see (PD-32). The whole company for Owner/Admin/HR, their own downline
     * for anyone with reports, and 403 for someone with neither.
     */
    @GetMapping("/team")
    public TeamOverviewResponse team(@CurrentUser AuthPrincipal principal) {
        return teamOverviewService.overview(principal);
    }
}
