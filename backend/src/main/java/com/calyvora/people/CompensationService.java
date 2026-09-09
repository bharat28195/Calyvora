package com.calyvora.people;

import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.TenantContext;
import com.calyvora.identity.User;
import com.calyvora.identity.UserRepository;
import com.calyvora.people.dto.AddCompensationRequest;
import com.calyvora.people.dto.CompensationResponse;
import com.calyvora.people.dto.CompensationResponse.Entry;
import com.calyvora.feature.Feature;
import com.calyvora.payroll.PfCalculator;
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
    private final com.calyvora.feature.FeatureService featureService;
    private final com.calyvora.payroll.PfSettingsService pfSettingsService;

    public CompensationService(CompensationRepository compensationRepository,
                               EmployeeRepository employeeRepository, UserRepository userRepository,
                               PayslipTemplateService payslipTemplateService,
                               AttendanceService attendanceService, EmployeeService employeeService,
                               com.calyvora.company.CompanyRepository companyRepository,
                               com.calyvora.company.CompanySettingsRepository companySettingsRepository,
                               EmployeeFinanceService financeService,
                               DepartmentRepository departmentRepository,
                               com.calyvora.feature.FeatureService featureService,
                               com.calyvora.payroll.PfSettingsService pfSettingsService) {
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
        this.featureService = featureService;
        this.pfSettingsService = pfSettingsService;
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
        BigDecimal totalEmployer = BigDecimal.ZERO;
        double totalLop = 0;
        UUID runCompanyId = TenantContext.getCompanyId();
        String currency = companyCurrency(runCompanyId);

        // A month of attendance for EVERYBODY, in one batch rather than one query set per person.
        //
        // The run called payslip() per employee, and payslip() fetches a month of attendance (three
        // queries), a salary, a finance row, a department and a user for that person — plus the
        // company's payslip template, currency, feature flags and settings, which are identical for
        // all of them and were re-read every single time. Roughly a dozen round trips each: about
        // 2,600 queries and seven seconds at 200 people, and thirty seconds at 1,000.
        //
        // Attendance is the biggest of those and the easiest to hoist without touching any arithmetic.
        // Only for people who are actually being paid. A payroll run skips anybody with no salary on
        // record, and computing a month of attendance for them is work thrown away — at a thousand
        // employees with no compensation that was the entire request, and it turned a 30-second run
        // into one that timed out. Restricting first is what makes the batch a win rather than a loss.
        java.util.Set<UUID> paid = new java.util.HashSet<>();
        for (var e : employeeService.directory()) {
            UUID id = UUID.fromString(e.id());
            if (!compensationRepository.findByEmployeeIdOrderByEffectiveDateDescCreatedAtDesc(id).isEmpty()) {
                paid.add(id);
            }
        }
        java.util.Map<UUID, com.calyvora.people.dto.AttendanceMonthResponse> attendanceByEmployee =
                attendanceService.monthForEveryone(ym, paid);

        for (var e : employeeService.directory()) {
            try {
                PayslipResponse p = payslip(UUID.fromString(e.id()), ym.toString(),
                        attendanceByEmployee.get(UUID.fromString(e.id())));
                // Absent statutory block = the feature is off or this person is not enrolled. Zero
                // rather than null so the row arithmetic works without every caller null-checking.
                BigDecimal employeePf = p.statutory() == null ? BigDecimal.ZERO : p.statutory().employeePf();
                BigDecimal employerContribution =
                        p.statutory() == null ? BigDecimal.ZERO : p.statutory().employerTotal();
                rows.add(new com.calyvora.people.dto.PayrollRunResponse.Row(
                        e.id(), p.employeeName(), e.jobTitle(), p.gross(), p.lopDays(), p.net(),
                        employeePf, employerContribution));
                totalGross = totalGross.add(p.gross());
                totalNet = totalNet.add(p.net());
                totalEmployer = totalEmployer.add(employerContribution);
                totalLop += p.lopDays();
            } catch (NotFoundException noSalary) {
                // Employee has no salary on record yet — not part of this run.
            }
        }
        return new com.calyvora.people.dto.PayrollRunResponse(
                ym.toString(), currency, rows, totalGross, totalNet, totalLop, rows.size(), totalEmployer);
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
        return payslip(employeeId, month, null);
    }

    /**
     * One payslip, optionally reusing attendance the caller has already loaded.
     *
     * <p>{@code prefetchedAttendance} is the only difference between a payslip opened on screen and one
     * computed inside a payroll run. Passing it in rather than giving the run its own copy of the
     * calculation is deliberate: a run and a payslip that disagreed about the same person's loss of pay
     * would be found by the employee, not by us. Null means fetch it, which is what every single-payslip
     * caller does.
     */
    @Transactional(readOnly = true)
    public PayslipResponse payslip(UUID employeeId, String month,
                                   com.calyvora.people.dto.AttendanceMonthResponse prefetchedAttendance) {
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
        var att = prefetchedAttendance != null ? prefetchedAttendance : attendanceService.month(employeeId, ym);
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

        // --- Provident Fund, when the company has statutory payroll switched on ------------------
        //
        // Two switches, both of which must be on: the company-level feature (the vendor's decision,
        // off for everybody until their numbers have been checked) and this employee's own PF status.
        // Neither implies the other — a company can run PF and still have employees who are not
        // enrolled, and an employee marked ENABLED at a company without the feature must not suddenly
        // see a deduction appear.
        //
        // Computed on BASIC, not gross. Using gross here would overstate every PF deduction in the
        // company by roughly a factor of two, and it would look plausible on the payslip.
        EmployeeFinance financeForPf = financeService.rawOrNull(employeeId);
        PayslipResponse.Statutory statutory = null;
        if (featureService.isEnabled(companyId, Feature.STATUTORY_PAYROLL)
                && financeForPf != null && "ENABLED".equals(financeForPf.getPfStatus())) {
            PfCalculator.Result pf = PfCalculator.compute(c.basic(), pfSettingsService.effective(companyId));
            if (pf.employee().signum() > 0) {
                deductions.add(new PayslipResponse.Line("Provident Fund (employee)", pf.employee()));
                totalDed = totalDed.add(pf.employee());
                net = net.subtract(pf.employee());
            }
            statutory = new PayslipResponse.Statutory(pf.pfWages(), pf.employee(), pf.employerEps(),
                    pf.employerEpf(), pf.adminCharges(), pf.edli(), pf.employerTotal());
        }
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
        // Same row the PF block read above; one fetch, because it is the same fact.
        EmployeeFinance finance = financeForPf;
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
                workingDays, lopDays, payableDays, statutory);
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
