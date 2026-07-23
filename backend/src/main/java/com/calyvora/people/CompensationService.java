package com.calyvora.people;

import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.TenantContext;
import com.calyvora.identity.User;
import com.calyvora.identity.UserRepository;
import com.calyvora.people.dto.AddCompensationRequest;
import com.calyvora.people.dto.CompensationResponse;
import com.calyvora.people.dto.CompensationResponse.Entry;
import com.calyvora.people.dto.PayslipResponse;
import com.calyvora.people.dto.PayslipResponse.Line;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Compensation history (salary + hikes) and payslip generation (feedback C1–C3). Owner/Admin-only;
 * the controller enforces the role. Tenant-scoped; every lookup verifies the employee's company.
 */
@Service
public class CompensationService {

    private final CompensationRepository compensationRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;

    public CompensationService(CompensationRepository compensationRepository,
                               EmployeeRepository employeeRepository, UserRepository userRepository) {
        this.compensationRepository = compensationRepository;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public CompensationResponse forEmployee(UUID employeeId) {
        UUID companyId = TenantContext.getCompanyId();
        Employee employee = requireEmployee(employeeId, companyId);
        String name = nameOf(employee);
        List<CompensationRecord> records = compensationRepository
                .findByEmployeeIdOrderByEffectiveDateDescCreatedAtDesc(employeeId);

        List<Entry> history = new ArrayList<>();
        for (int i = 0; i < records.size(); i++) {
            CompensationRecord r = records.get(i);
            CompensationRecord older = i + 1 < records.size() ? records.get(i + 1) : null;
            BigDecimal hikeAmount = null;
            Double hikePercent = null;
            if (older != null && older.getAnnualAmount().signum() > 0) {
                hikeAmount = r.getAnnualAmount().subtract(older.getAnnualAmount());
                hikePercent = hikeAmount.multiply(BigDecimal.valueOf(100))
                        .divide(older.getAnnualAmount(), 1, RoundingMode.HALF_UP).doubleValue();
            }
            history.add(new Entry(r.getId().toString(), r.getEffectiveDate().toString(),
                    r.getAnnualAmount(), r.getChangeType().name(), r.getReason(), hikeAmount, hikePercent));
        }

        CompensationRecord current = records.isEmpty() ? null : records.get(0);
        String currency = current == null ? "USD" : current.getCurrency();
        BigDecimal annual = current == null ? null : current.getAnnualAmount();
        BigDecimal monthly = annual == null ? null : annual.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
        return new CompensationResponse(employeeId.toString(), name, currency, annual, monthly,
                current == null ? null : current.getEffectiveDate().toString(), history);
    }

    @Transactional
    public CompensationResponse add(UUID employeeId, AddCompensationRequest req, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        requireEmployee(employeeId, companyId);
        List<CompensationRecord> existing = compensationRepository
                .findByEmployeeIdOrderByEffectiveDateDescCreatedAtDesc(employeeId);

        CompensationChangeType type;
        if (existing.isEmpty()) {
            type = CompensationChangeType.INITIAL;
        } else {
            int cmp = req.annualAmount().compareTo(existing.get(0).getAnnualAmount());
            type = cmp > 0 ? CompensationChangeType.HIKE : CompensationChangeType.ADJUSTMENT;
        }
        LocalDate effective = req.effectiveDate() == null || req.effectiveDate().isBlank()
                ? LocalDate.now() : LocalDate.parse(req.effectiveDate());
        String currency = req.currency() == null || req.currency().isBlank() ? "USD" : req.currency().toUpperCase();

        compensationRepository.save(new CompensationRecord(UUID.randomUUID(), companyId, employeeId,
                effective, req.annualAmount(), currency, type, blankToNull(req.reason()), principal.userId()));
        return forEmployee(employeeId);
    }

    /** My own compensation — self-service, so an employee can see their salary and hikes. */
    @Transactional(readOnly = true)
    public CompensationResponse forSelf(UUID userId) {
        return forEmployee(selfEmployeeId(userId));
    }

    /** My own payslip — self-service. */
    @Transactional(readOnly = true)
    public PayslipResponse payslipForSelf(UUID userId, String month) {
        return payslip(selfEmployeeId(userId), month);
    }

    private UUID selfEmployeeId(UUID userId) {
        UUID companyId = TenantContext.getCompanyId();
        return employeeRepository.findByUserId(userId)
                .filter(e -> e.getCompanyId().equals(companyId))
                .map(Employee::getId)
                .orElseThrow(() -> new NotFoundException("No employee profile for this user"));
    }

    @Transactional(readOnly = true)
    public PayslipResponse payslip(UUID employeeId, String month) {
        UUID companyId = TenantContext.getCompanyId();
        Employee employee = requireEmployee(employeeId, companyId);
        String name = nameOf(employee);
        YearMonth ym = month == null || month.isBlank() ? YearMonth.now() : YearMonth.parse(month);

        List<CompensationRecord> records = compensationRepository
                .findByEmployeeIdOrderByEffectiveDateDescCreatedAtDesc(employeeId);
        if (records.isEmpty()) {
            throw new NotFoundException("No salary on record for this employee");
        }
        CompensationRecord current = records.get(0);
        String cur = current.getCurrency();
        BigDecimal gross = current.getAnnualAmount().divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);

        // A simple, transparent split: earnings sum to gross; deductions are standard components.
        BigDecimal basic = pct(gross, 50);
        BigDecimal hra = pct(gross, 25);
        BigDecimal special = gross.subtract(basic).subtract(hra); // remainder → no rounding drift
        List<Line> earnings = List.of(
                new Line("Basic", basic),
                new Line("House rent allowance", hra),
                new Line("Special allowance", special));

        BigDecimal pf = pct(basic, 12);
        BigDecimal tax = pct(gross, 10);
        List<Line> deductions = List.of(
                new Line("Provident fund", pf),
                new Line("Income tax", tax));
        BigDecimal totalDed = pf.add(tax);
        BigDecimal net = gross.subtract(totalDed);

        return new PayslipResponse(employeeId.toString(), name, ym.toString(), cur,
                earnings, deductions, gross, totalDed, net);
    }

    private Employee requireEmployee(UUID employeeId, UUID companyId) {
        return employeeRepository.findByIdAndCompanyId(employeeId, companyId)
                .orElseThrow(() -> new NotFoundException("Employee not found"));
    }

    private String nameOf(Employee employee) {
        return userRepository.findById(employee.getUserId()).map(User::fullName).orElse("Employee");
    }

    private static BigDecimal pct(BigDecimal amount, int percent) {
        return amount.multiply(BigDecimal.valueOf(percent)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
