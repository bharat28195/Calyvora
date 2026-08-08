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
    private final PayslipTemplateService payslipTemplateService;
    private final AttendanceService attendanceService;
    private final EmployeeService employeeService;
    private final com.calyvora.company.CompanyRepository companyRepository;
    private final com.calyvora.company.CompanySettingsRepository companySettingsRepository;
    private final EmployeeFinanceService financeService;
    private final DepartmentRepository departmentRepository;

    public CompensationService(CompensationRepository compensationRepository,
                               EmployeeRepository employeeRepository, UserRepository userRepository,
                               PayslipTemplateService payslipTemplateService,
                               AttendanceService attendanceService, EmployeeService employeeService,
                               com.calyvora.company.CompanyRepository companyRepository,
                               com.calyvora.company.CompanySettingsRepository companySettingsRepository,
                               EmployeeFinanceService financeService,
                               DepartmentRepository departmentRepository) {
        this.financeService = financeService;
        this.departmentRepository = departmentRepository;
        this.compensationRepository = compensationRepository;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.payslipTemplateService = payslipTemplateService;
        this.attendanceService = attendanceService;
        this.employeeService = employeeService;
        this.companyRepository = companyRepository;
        this.companySettingsRepository = companySettingsRepository;
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
        String currency = companyCurrency(companyId);
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
        String currency = req.currency() == null || req.currency().isBlank()
                ? companyCurrency(companyId) : req.currency().toUpperCase();

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

    /**
     * A month's payroll run for the whole company (HR "push payslips"): every employee with a salary on
     * record, their gross, LOP days and net after attendance. Read-write because listing the directory
     * may provision missing profiles (which a read-only transaction would silently swallow).
     */
    @Transactional
    public com.calyvora.people.dto.PayrollRunResponse payrollRun(String month) {
        java.time.YearMonth ym = month == null || month.isBlank()
                ? java.time.YearMonth.now() : java.time.YearMonth.parse(month);
        List<com.calyvora.people.dto.PayrollRunResponse.Row> rows = new java.util.ArrayList<>();
        BigDecimal totalGross = BigDecimal.ZERO, totalNet = BigDecimal.ZERO;
        double totalLop = 0;
        String currency = companyCurrency(TenantContext.getCompanyId());
        for (var e : employeeService.directory()) {
            try {
                PayslipResponse p = payslip(UUID.fromString(e.id()), ym.toString());
                rows.add(new com.calyvora.people.dto.PayrollRunResponse.Row(
                        e.id(), p.employeeName(), e.jobTitle(), p.gross(), p.lopDays(), p.net()));
                totalGross = totalGross.add(p.gross());
                totalNet = totalNet.add(p.net());
                totalLop += p.lopDays();
            } catch (NotFoundException noSalary) {
                // Employee has no salary on record yet — not part of this run.
            }
        }
        return new com.calyvora.people.dto.PayrollRunResponse(
                ym.toString(), currency, rows, totalGross, totalNet, totalLop, rows.size());
    }

    /**
     * The currency every amount in People/Payroll is denominated in — the one the company picked in
     * settings. Falls back to INR only when a company predates the settings row.
     */
    private String companyCurrency(UUID companyId) {
        return companySettingsRepository.findById(companyId)
                .map(com.calyvora.company.CompanySettings::getCurrency)
                .filter(c -> c != null && !c.isBlank())
                .orElse("INR");
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
        // The company's configured currency is the single source of truth for what money on a payslip
        // means. A salary row carries its own code only as history (and older rows default to USD), so
        // reading it here printed "USD" on an INR company's payslip.
        String cur = companyCurrency(companyId);
        BigDecimal gross = current.getAnnualAmount().divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);

        // Generate the lines from the company's configurable payslip template.
        PayslipTemplateService.Computed c = payslipTemplateService.compute(companyId, gross);

        // --- Attendance linkage: unpaid absences (LOP) reduce the month's pay -----------------
        var att = attendanceService.month(employeeId, ym);
        int workingDays = 0;
        double lopDays = 0;
        for (var d : att.days()) {
            if (d.status() == null) continue;   // future/unmarked working day — not counted as LOP
            AttendanceStatus s = AttendanceStatus.valueOf(d.status());
            if (s == AttendanceStatus.WEEK_OFF || s == AttendanceStatus.HOLIDAY) continue;
            workingDays++;
            if (s == AttendanceStatus.ABSENT) lopDays += 1;          // unpaid full day
            else if (s == AttendanceStatus.HALF_DAY) lopDays += 0.5;  // half unpaid
        }
        double payableDays = Math.max(0, workingDays - lopDays);

        List<PayslipResponse.Line> deductions = new java.util.ArrayList<>(c.deductions());
        BigDecimal totalDed = c.totalDeductions();
        BigDecimal net = c.net();
        if (lopDays > 0 && workingDays > 0) {
            BigDecimal perDay = gross.divide(BigDecimal.valueOf(workingDays), 2, RoundingMode.HALF_UP);
            BigDecimal lop = perDay.multiply(BigDecimal.valueOf(lopDays)).setScale(2, RoundingMode.HALF_UP);
            deductions.add(new PayslipResponse.Line(
                    "Loss of pay (" + trimNum(lopDays) + " day" + (lopDays == 1 ? "" : "s") + ")", lop));
            totalDed = totalDed.add(lop);
            net = net.subtract(lop);
        }

        // Payslip header — legal name (falling back to company name), address and logo.
        var settings = companySettingsRepository.findById(companyId).orElse(null);
        String companyName = settings != null && settings.getLegalName() != null && !settings.getLegalName().isBlank()
                ? settings.getLegalName()
                : companyRepository.findById(companyId).map(com.calyvora.company.Company::getName).orElse("");
        String companyAddress = settings == null ? null : settings.getAddress();
        String companyLogoUrl = settings == null ? null : settings.getLogoUrl();

        // Who it's for, and the statutory identifiers a payslip is expected to carry. All optional —
        // a company that hasn't filled in PF/PAN yet still gets a valid payslip, just a sparser one.
        EmployeeFinance finance = financeService.rawOrNull(employeeId);
        String department = employee.getDepartmentId() == null ? null
                : departmentRepository.findById(employee.getDepartmentId())
                        .map(Department::getName).orElse(null);

        return new PayslipResponse(employeeId.toString(), name, ym.toString(), cur,
                companyName, companyAddress, companyLogoUrl,
                employee.getEmployeeNo(),
                employee.getStartDate() == null ? null : employee.getStartDate().toString(),
                department,
                employee.getJobTitle(),
                finance == null ? null : finance.getPaymentMode(),
                finance == null ? null : finance.getUan(),
                finance == null ? null : finance.getPfNumber(),
                finance == null ? null : maskPan(finance.getPanNumber()),
                c.earnings(), deductions, c.gross(), totalDed, net,
                AmountInWords.of(net, cur),
                workingDays, lopDays, payableDays);
    }

    /** PAN as {@code XXXXXX894N} — a payslip identifies the PAN without reprinting it in full. */
    private static String maskPan(String pan) {
        if (pan == null || pan.isBlank()) {
            return null;
        }
        String p = pan.trim();
        return p.length() <= 4 ? p : "X".repeat(p.length() - 4) + p.substring(p.length() - 4);
    }

    /** "2" not "2.0", "1.5" kept — for the LOP line label. */
    private static String trimNum(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    private Employee requireEmployee(UUID employeeId, UUID companyId) {
        return employeeRepository.findByIdAndCompanyId(employeeId, companyId)
                .orElseThrow(() -> new NotFoundException("Employee not found"));
    }

    private String nameOf(Employee employee) {
        return userRepository.findById(employee.getUserId()).map(User::fullName).orElse("Employee");
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
