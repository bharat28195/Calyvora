package com.calyvora.payroll;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.TenantContext;
import com.calyvora.people.CompensationService;
import com.calyvora.people.dto.PayslipResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Finalising (locking) a payroll month, and reopening one.
 *
 * <p>Finalising computes every payslip for the month exactly as an open run would, and stores each
 * one. From then on the month is read back, never recomputed: a raise in November does not change
 * October's payslip, and the PF, ESI and TDS figures filed for October stay the figures Orbit shows.
 *
 * <p>Reopening exists because mistakes happen before the money moves — but only the most recent
 * locked month can be reopened. Reopening an earlier one would leave later months' income tax
 * resting on withholding that no longer exists.
 */
@Service
public class PayrollLockService {

    private final CompensationService compensationService;
    private final PayrollMonthRepository monthRepository;
    private final PayslipSnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;

    public PayrollLockService(CompensationService compensationService, PayrollMonthRepository monthRepository,
                              PayslipSnapshotRepository snapshotRepository, ObjectMapper objectMapper) {
        this.compensationService = compensationService;
        this.monthRepository = monthRepository;
        this.snapshotRepository = snapshotRepository;
        this.objectMapper = objectMapper;
    }

    /** @param finalizedAt null for a month still open */
    public record MonthStatus(String month, boolean finalized, String finalizedAt, Integer employees,
                              BigDecimal totalGross, BigDecimal totalNet, BigDecimal totalEmployer) {
        static MonthStatus of(PayrollMonth m) {
            return new MonthStatus(m.getMonth(), true, m.getFinalizedAt().toString(), m.getEmployees(),
                    m.getTotalGross(), m.getTotalNet(), m.getTotalEmployer());
        }

        static MonthStatus open(String month) {
            return new MonthStatus(month, false, null, null, null, null, null);
        }
    }

    @Transactional(readOnly = true)
    public List<MonthStatus> finalizedMonths() {
        return monthRepository.findByCompanyIdOrderByMonthDesc(TenantContext.getCompanyId()).stream()
                .map(MonthStatus::of).toList();
    }

    @Transactional(readOnly = true)
    public MonthStatus status(String month) {
        String ym = parse(month).toString();
        return monthRepository.findByCompanyIdAndMonth(TenantContext.getCompanyId(), ym)
                .map(MonthStatus::of).orElse(MonthStatus.open(ym));
    }

    @Transactional
    public MonthStatus finalizeMonth(String month, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        YearMonth ym = parse(month);
        if (ym.isAfter(YearMonth.now())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "A month can't be finalised before it starts");
        }
        if (monthRepository.existsByCompanyIdAndMonth(companyId, ym.toString())) {
            throw new ApiException(ErrorCode.CONFLICT, ym + " is already finalised");
        }

        List<PayslipResponse> slips = compensationService.computeRunPayslips(ym);
        if (slips.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Nobody has a salary on record for " + ym);
        }

        BigDecimal gross = BigDecimal.ZERO, net = BigDecimal.ZERO, employer = BigDecimal.ZERO;
        for (PayslipResponse p : slips) {
            snapshotRepository.save(snapshotOf(companyId, ym.toString(), p));
            gross = gross.add(p.gross());
            net = net.add(p.net());
            employer = employer.add(p.statutory() == null ? BigDecimal.ZERO : p.statutory().employerTotal());
        }
        PayrollMonth saved = monthRepository.save(new PayrollMonth(companyId, ym.toString(),
                principal.userId(), slips.size(), gross, net, employer));
        return MonthStatus.of(saved);
    }

    @Transactional
    public MonthStatus reopen(String month) {
        UUID companyId = TenantContext.getCompanyId();
        String ym = parse(month).toString();
        PayrollMonth m = monthRepository.findByCompanyIdAndMonth(companyId, ym)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, ym + " is not finalised"));
        List<PayrollMonth> all = monthRepository.findByCompanyIdOrderByMonthDesc(companyId);
        if (!all.get(0).getMonth().equals(ym)) {
            throw new ApiException(ErrorCode.CONFLICT, "Only the latest finalised month (" + all.get(0).getMonth()
                    + ") can be reopened — later months' income tax depends on this one");
        }
        snapshotRepository.deleteByCompanyIdAndMonth(companyId, ym);
        monthRepository.delete(m);
        return MonthStatus.open(ym);
    }

    private PayslipSnapshot snapshotOf(UUID companyId, String month, PayslipResponse p) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(p);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not store the payslip for " + p.employeeName(), e);
        }
        PayslipSnapshot s = new PayslipSnapshot(companyId, month, UUID.fromString(p.employeeId()), payload);
        s.setGross(p.gross());
        s.setNet(p.net());
        s.setWorkingDays(p.workingDays());
        s.setLopDays(BigDecimal.valueOf(p.lopDays()));
        s.setIncomeTax(p.incomeTax());
        var st = p.statutory();
        if (st != null) {
            s.setPfWages(st.pfWages());
            s.setEmployeePf(st.employeePf());
            s.setEmployerEps(st.employerEps());
            s.setEmployerEpf(st.employerEpf());
            s.setEsiWages(st.esiWages());
            s.setEmployeeEsi(st.employeeEsi());
            s.setEmployerEsi(st.employerEsi());
            s.setProfessionalTax(st.professionalTax());
            s.setPtState(st.ptState());
        }
        return s;
    }

    private static YearMonth parse(String month) {
        try {
            return month == null || month.isBlank() ? YearMonth.now() : YearMonth.parse(month);
        } catch (RuntimeException e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Month must look like 2026-10");
        }
    }
}
