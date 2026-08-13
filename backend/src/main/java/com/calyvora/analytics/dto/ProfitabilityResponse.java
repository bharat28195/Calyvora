package com.calyvora.analytics.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * What each project cost to deliver, and what it was sold for (PD-22, D2).
 *
 * <p>No HR product answers this, because none of them hold both halves: Zoho and BambooHR have no
 * work management at all, and Keka's PSA starts from timesheets a human had to fill in. We hold
 * compensation, attendance, delivery and clients on one schema, so the number falls out of data
 * people already generate by doing their jobs.
 *
 * <p><b>Cost here is allocated, not measured.</b> We do not have timesheets, so a person's salary
 * cost for the period is spread across the projects they delivered into, weighted by the work they
 * closed there. That is an honest model, not a precise one — see {@link #basis()}, which is echoed
 * to the UI so nobody mistakes it for billing-grade truth.
 */
public record ProfitabilityResponse(
        String periodStart,
        String periodEnd,
        String currency,
        String basis,
        BigDecimal totalCost,
        BigDecimal totalContractValue,
        BigDecimal totalMargin,
        int projectsCosted,
        int peopleCosted,
        BigDecimal unallocatedCost,
        List<ProjectLine> projects,
        List<PersonLine> people) {

    /** One project's economics for the period. */
    public record ProjectLine(
            String projectId,
            String projectKey,
            String projectName,
            String clientId,
            String clientName,
            boolean billable,
            /** The agreed fee for the whole engagement, or null when nothing has been agreed. */
            BigDecimal contractValue,
            /** Allocated delivery cost for the requested period only. */
            BigDecimal cost,
            /**
             * Allocated cost since the engagement began. The fee is a whole-engagement figure, so this
             * is the only thing it can honestly be compared against — measuring a year's fee against
             * one month's cost would report a margin near 100% on every young project.
             */
            BigDecimal costToDate,
            /** contractValue − costToDate, or null when there is no fee to compare against. */
            BigDecimal margin,
            /** Margin as a percentage of the fee, or null when there is no fee. */
            BigDecimal marginPercent,
            int itemsDelivered,
            int pointsDelivered,
            int contributors) {
    }

    /** One person's cost for the period, and how much of it landed on billable work. */
    public record PersonLine(
            String employeeId,
            String employeeName,
            BigDecimal cost,
            /** Share of their cost allocated to billable projects, 0–100. */
            BigDecimal billablePercent,
            double daysWorked,
            int itemsDelivered,
            int pointsDelivered,
            int projectsTouched) {
    }
}
