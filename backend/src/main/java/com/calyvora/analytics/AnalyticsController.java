package com.calyvora.analytics;

import com.calyvora.analytics.dto.AnalyticsOverviewResponse;
import com.calyvora.analytics.dto.ProfitabilityResponse;
import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/** Company-wide analytics for the Insights dashboard. Owner/Admin only. Base {@code /api/v1/analytics}. */
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final AnalyticsService service;
    private final ProfitabilityService profitabilityService;

    public AnalyticsController(AnalyticsService service, ProfitabilityService profitabilityService) {
        this.service = service;
        this.profitabilityService = profitabilityService;
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','HR')")
    public AnalyticsOverviewResponse overview() {
        return service.overview();
    }

    /**
     * What delivery cost and what it was sold for (PD-22, D2). Defaults to the current month.
     *
     * <p>Same audience as the rest of Insights, and deliberately no wider: the figures are derived
     * from individual salaries, so anyone who can read this can already read pay.
     */
    @GetMapping("/profitability")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','HR')")
    public ProfitabilityResponse profitability(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        YearMonth thisMonth = YearMonth.now();
        return profitabilityService.forPeriod(
                parseOr(from, thisMonth.atDay(1)),
                parseOr(to, thisMonth.atEndOfMonth()));
    }

    private static LocalDate parseOr(String value, LocalDate fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Dates must be YYYY-MM-DD");
        }
    }
}
