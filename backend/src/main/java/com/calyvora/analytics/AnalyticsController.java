package com.calyvora.analytics;

import com.calyvora.analytics.dto.AnalyticsOverviewResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Company-wide analytics for the Insights dashboard. Owner/Admin only. Base {@code /api/v1/analytics}. */
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final AnalyticsService service;

    public AnalyticsController(AnalyticsService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public AnalyticsOverviewResponse overview() {
        return service.overview();
    }
}
