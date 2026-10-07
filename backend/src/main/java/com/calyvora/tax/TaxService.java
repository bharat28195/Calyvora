package com.calyvora.tax;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.TenantContext;
import com.calyvora.company.CompanySettings;
import com.calyvora.company.CompanySettingsRepository;
import com.calyvora.identity.User;
import com.calyvora.identity.UserRepository;
import com.calyvora.people.CompensationRecord;
import com.calyvora.people.CompensationRepository;
import com.calyvora.people.Employee;
import com.calyvora.people.EmployeeRepository;
import com.calyvora.people.OrgScope;
import com.calyvora.tax.dto.TaxDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tax declarations and what they cost — the part of the module that knows about people and money.
 *
 * <p>{@link IncomeTaxCalculator} holds the law and knows nothing else; this class finds the salary,
 * reads what was declared, and turns the answer into something a payslip and a screen can use. The
 * split is deliberate: the arithmetic is the part that must be provable, and it is far easier to
 * prove when it cannot reach a database.
 */
@Service
public class TaxService {

    private final TaxDeclarationRepository declarationRepository;
    private final TaxDeclarationItemRepository itemRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final CompensationRepository compensationRepository;
    private final CompanySettingsRepository settingsRepository;
    private final OrgScope orgScope;
    private final com.calyvora.feature.FeatureService featureService;

    private final com.calyvora.access.PermissionService permissions;
    private final com.calyvora.payroll.PayslipSnapshotRepository snapshotRepository;
    private final TdsOpeningBalanceRepository openingRepository;

    public TaxService(TaxDeclarationRepository declarationRepository,
                      TaxDeclarationItemRepository itemRepository,
                      EmployeeRepository employeeRepository,
                      UserRepository userRepository,
                      CompensationRepository compensationRepository,
                      CompanySettingsRepository settingsRepository,
                      OrgScope orgScope,
                      com.calyvora.feature.FeatureService featureService,
            com.calyvora.access.PermissionService permissions,
            com.calyvora.payroll.PayslipSnapshotRepository snapshotRepository,
            TdsOpeningBalanceRepository openingRepository) {
        this.permissions = permissions;
        this.snapshotRepository = snapshotRepository;
        this.openingRepository = openingRepository;
        this.declarationRepository = declarationRepository;
        this.itemRepository = itemRepository;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.compensationRepository = compensationRepository;
        this.settingsRepository = settingsRepository;
        this.orgScope = orgScope;
        this.featureService = featureService;
    }

    // ---- the employee's own declaration -------------------------------------------------------

    @Transactional(readOnly = true)
    public TaxDtos.DeclarationResponse myDeclaration(AuthPrincipal principal, String year) {
        UUID companyId = TenantContext.getCompanyId();
        Employee me = self(principal);
        FinancialYear fy = yearOrCurrent(year);
        return declarationRepository
                .findByCompanyIdAndEmployeeIdAndFinancialYear(companyId, me.getId(), fy.label())
                .map(d -> toResponse(d, itemRepository.findByDeclarationId(d.getId()), windowOpen(companyId)))
                // Nothing on file is not an error — it is the normal state every April, and the
                // screen needs the section list and the default regime to render the empty form.
                .orElseGet(() -> emptyDeclaration(fy, windowOpen(companyId)));
    }

    /**
     * Save what the employee declared.
     *
     * <p>The window is checked here rather than only on the screen: HR closes declarations before the
     * last payroll of the year so the figures cannot move under a run that has already been filed,
     * and a closed window that only hides a button is not closed.
     */
    @Transactional
    public TaxDtos.DeclarationResponse save(AuthPrincipal principal, String year,
                                            TaxDtos.DeclarationPayload payload) {
        UUID companyId = TenantContext.getCompanyId();
        Employee me = self(principal);
        FinancialYear fy = yearOrCurrent(year);
        if (!windowOpen(companyId)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Tax declarations are closed for now. Ask HR to reopen them.");
        }

        TaxDeclaration declaration = declarationRepository
                .findByCompanyIdAndEmployeeIdAndFinancialYear(companyId, me.getId(), fy.label())
                .orElseGet(() -> declarationRepository.save(
                        new TaxDeclaration(companyId, me.getId(), fy.label())));

        if (payload != null && payload.regime() != null) {
            declaration.setRegime(payload.regime());
        }
        if (payload != null && payload.declared() != null) {
            replaceItems(companyId, declaration, payload.declared());
        }
        declarationRepository.save(declaration);
        return toResponse(declaration, itemRepository.findByDeclarationId(declaration.getId()), true);
    }

    @Transactional
    public TaxDtos.DeclarationResponse submit(AuthPrincipal principal, String year) {
        UUID companyId = TenantContext.getCompanyId();
        Employee me = self(principal);
        FinancialYear fy = yearOrCurrent(year);
        TaxDeclaration declaration = declarationRepository
                .findByCompanyIdAndEmployeeIdAndFinancialYear(companyId, me.getId(), fy.label())
                .orElseThrow(() -> new NotFoundException("There is nothing to submit for " + fy.label() + " yet."));
        if (!windowOpen(companyId)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Tax declarations are closed for now. Ask HR to reopen them.");
        }
        declaration.submit();
        declarationRepository.save(declaration);
        return toResponse(declaration, itemRepository.findByDeclarationId(declaration.getId()), true);
    }

    /**
     * Replace the declared amounts wholesale.
     *
     * <p>A merge would be wrong: a section the employee cleared has to disappear, and a payload that
     * only ever adds means nobody can ever take a claim back.
     */
    private void replaceItems(UUID companyId, TaxDeclaration declaration, Map<String, BigDecimal> declared) {
        Map<TaxDeduction, BigDecimal> wanted = parseDeclared(declared);
        itemRepository.deleteByDeclarationId(declaration.getId());
        // Flush the deletes before inserting, or the unique constraint on (declaration, deduction)
        // sees the old rows and the new ones at once.
        itemRepository.flush();
        List<TaxDeclarationItem> rows = new ArrayList<>();
        for (Map.Entry<TaxDeduction, BigDecimal> e : wanted.entrySet()) {
            if (e.getValue().signum() > 0) {
                rows.add(new TaxDeclarationItem(companyId, declaration.getId(), e.getKey(), e.getValue()));
            }
        }
        itemRepository.saveAll(rows);
    }

    private Map<TaxDeduction, BigDecimal> parseDeclared(Map<String, BigDecimal> declared) {
        Map<TaxDeduction, BigDecimal> out = new EnumMap<>(TaxDeduction.class);
        for (Map.Entry<String, BigDecimal> e : declared.entrySet()) {
            TaxDeduction key;
            try {
                key = TaxDeduction.valueOf(e.getKey());
            } catch (IllegalArgumentException ex) {
                // Named rather than ignored: a typo silently dropping a ₹1,50,000 claim is a tax
                // bill the employee does not expect and cannot explain.
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "'" + e.getKey() + "' is not a deduction this form knows about.");
            }
            BigDecimal amount = e.getValue() == null ? BigDecimal.ZERO : e.getValue();
            if (amount.signum() < 0) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        key.section() + " cannot be a negative amount.");
            }
            out.put(key, amount);
        }
        return out;
    }

    // ---- what it costs ------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public TaxDtos.ComputationResponse myComputation(AuthPrincipal principal, String year) {
        UUID companyId = TenantContext.getCompanyId();
        Employee me = self(principal);
        return computationFor(companyId, me, yearOrCurrent(year));
    }

    /**
     * The whole calculation for one person, plus what the next pay run should withhold.
     *
     * <p>TDS is spread evenly: the year's tax divided by twelve. What is left is then divided by the
     * months that remain, which is what makes a mid-year change — a raise, a late declaration —
     * correct itself over the rest of the year instead of leaving a lump in March.
     */
    private TaxDtos.ComputationResponse computationFor(UUID companyId, Employee employee, FinancialYear fy) {
        CompensationRecord current = currentSalary(employee.getId());
        String currency = current == null || current.getCurrency() == null ? "INR" : current.getCurrency();
        // The same year the payslip sees (yearPositions): salary by date, locked months as paid, and
        // any opening balance — so the screen and the payslip can never disagree.
        java.time.YearMonth asOf = java.time.YearMonth.now();
        java.time.YearMonth fyFirst = java.time.YearMonth.from(fy.start());
        java.time.YearMonth fyLast = fyFirst.plusMonths(11);
        if (asOf.isBefore(fyFirst)) asOf = fyFirst;
        if (asOf.isAfter(fyLast)) asOf = fyLast;
        YearPosition position = yearPositions(companyId, asOf, List.of(employee.getId())).get(employee.getId());
        BigDecimal gross = position != null ? position.income()
                : current == null ? BigDecimal.ZERO : current.getAnnualAmount();

        TaxDeclaration declaration = declarationRepository
                .findByCompanyIdAndEmployeeIdAndFinancialYear(companyId, employee.getId(), fy.label())
                .orElse(null);
        TaxRegime regime = declaration == null ? TaxRegime.DEFAULT : declaration.getRegime();
        Map<TaxDeduction, BigDecimal> declared = declaration == null
                ? Map.of()
                : itemsOf(itemRepository.findByDeclarationId(declaration.getId()));

        IncomeTaxCalculator.Result result = IncomeTaxCalculator.compute(
                new IncomeTaxCalculator.Input(gross, regime, declared));

        // The same salary and the same declarations under the other set of rules. Somebody on the
        // costlier regime cannot tell without this, and April is the only month they can act on it.
        TaxRegime other = regime == TaxRegime.NEW ? TaxRegime.OLD : TaxRegime.NEW;
        IncomeTaxCalculator.Result alternative = IncomeTaxCalculator.compute(
                new IncomeTaxCalculator.Input(gross, other, declared));
        BigDecimal oldTax = regime == TaxRegime.OLD ? result.totalTax() : alternative.totalTax();
        BigDecimal newTax = regime == TaxRegime.NEW ? result.totalTax() : alternative.totalTax();
        TaxRegime cheaper = oldTax.compareTo(newTax) <= 0 ? TaxRegime.OLD : TaxRegime.NEW;
        BigDecimal saving = oldTax.subtract(newTax).abs();

        int elapsed = fy.monthsElapsed(LocalDate.now());
        // The month's withholding, exactly as the payslip computes it.
        BigDecimal perMonth = position == null ? result.monthlyTds() : position.thisMonth(result.totalTax());
        // Whether this company withholds income tax through Orbit at all (off until switched on per
        // customer). When it does not, nothing has been "deducted so far" — the page showed a projected
        // figure under that label to people whose payslips carried no tax line, so it says zero and the
        // screen presents the rest as an estimate to plan with.
        boolean withheld = featureService.isEnabled(companyId, com.calyvora.feature.Feature.INCOME_TAX);
        BigDecimal deductedSoFar = !withheld ? BigDecimal.ZERO
                : position == null ? perMonth.multiply(BigDecimal.valueOf(elapsed))
                : position.withheld().add(perMonth.multiply(BigDecimal.valueOf(position.openPastMonths())));
        if (deductedSoFar.compareTo(result.totalTax()) > 0) {
            deductedSoFar = result.totalTax();
        }
        BigDecimal remaining = result.totalTax().subtract(deductedSoFar).max(BigDecimal.ZERO);
        BigDecimal nextMonth = perMonth;

        return new TaxDtos.ComputationResponse(
                fy.label(), regime, currency,
                result.grossSalary(), result.standardDeduction(),
                deductionRows(result), result.totalDeductions(), result.taxableIncome(),
                bandRows(result), result.taxOnIncome(), result.rebate(),
                result.surcharge(), result.cess(), result.totalTax(), perMonth,
                elapsed, deductedSoFar, remaining, nextMonth,
                new TaxDtos.RegimeComparison(oldTax, newTax, cheaper, saving), withheld);
    }

    private static List<TaxDtos.DeductionRow> deductionRows(IncomeTaxCalculator.Result result) {
        List<TaxDtos.DeductionRow> rows = new ArrayList<>();
        for (IncomeTaxCalculator.AllowedDeduction d : result.deductions()) {
            rows.add(new TaxDtos.DeductionRow(d.deduction().name(), d.deduction().section(),
                    d.deduction().label(), d.declared(), d.allowed()));
        }
        return rows;
    }

    private static List<TaxDtos.BandRow> bandRows(IncomeTaxCalculator.Result result) {
        List<TaxDtos.BandRow> rows = new ArrayList<>();
        for (IncomeTaxCalculator.BandTax b : result.bands()) {
            rows.add(new TaxDtos.BandRow(b.from(), b.to(),
                    b.rate().multiply(BigDecimal.valueOf(100)).stripTrailingZeros(),
                    b.taxable(), b.tax()));
        }
        return rows;
    }

    /**
     * Monthly TDS for a set of employees, for a payroll run.
     *
     * <p>Everything the run needs in two queries rather than two per employee: the declarations for
     * the year and their items, both keyed up front. A thousand-person run asking per person is two
     * thousand round trips to work out a deduction.
     *
     * <p>Depends on the month being paid, never on today's date, so a payslip recomputed for an open
     * month gives the same answer whenever it is opened — and a finalised month is never recomputed
     * at all; its payslip is read back as issued.
     *
     * @param annualSalary employee id to annual gross; employees absent from it get nothing
     */
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> monthlyTdsFor(UUID companyId, java.time.YearMonth month,
                                               java.util.Collection<UUID> employeeIds) {
        Map<UUID, YearPosition> positions = yearPositions(companyId, month, employeeIds);
        if (positions.isEmpty()) {
            return Map.of();
        }
        Map<UUID, BigDecimal> income = new HashMap<>();
        positions.forEach((id, p) -> income.put(id, p.income()));
        Map<UUID, IncomeTaxCalculator.Result> annual = annualTaxFor(companyId, FinancialYear.of(month.atDay(1)), income);

        Map<UUID, BigDecimal> out = new HashMap<>();
        for (Map.Entry<UUID, YearPosition> e : positions.entrySet()) {
            out.put(e.getKey(), e.getValue().thisMonth(annual.get(e.getKey()).totalTax()));
        }
        return out;
    }

    /**
     * Where one employee stands in the financial year, as of a month being paid.
     *
     * @param income        the year's income: opening balance + what locked months actually paid +
     *                      the salary by date for every other month they are employed
     * @param withheld      tax actually withheld before this month — the opening balance's TDS plus
     *                      every locked month's
     * @param spreadMonths  months the rest of the tax is spread over: this month, the rest of the
     *                      year, and earlier months that are neither locked nor covered by an opening
     *                      balance (an open month withholds the same share as this one)
     */
    public record YearPosition(BigDecimal income, BigDecimal withheld, int spreadMonths, int openPastMonths) {
        /** (year's tax − already withheld) ÷ months it is spread over, in whole rupees. */
        public BigDecimal thisMonth(BigDecimal yearTax) {
            if (spreadMonths <= 0) {
                return BigDecimal.ZERO;
            }
            return yearTax.subtract(withheld).max(BigDecimal.ZERO)
                    .divide(BigDecimal.valueOf(spreadMonths), 0, RoundingMode.HALF_UP);
        }
    }

    /**
     * The year so far and ahead, per employee, in a handful of queries for the whole company.
     *
     * <p>For a full-year employee on one salary with nothing locked this is twelve months of the same
     * salary spread over twelve — an even twelfth, as before. It differs exactly where the old
     * figure was wrong: a raise (each month at its own salary), a mid-year joiner or leaver (only the
     * months they are employed, and the tax spread over those months), a locked month (what was
     * actually paid and withheld), and income from before Orbit (the opening balance).
     */
    private Map<UUID, YearPosition> yearPositions(UUID companyId, java.time.YearMonth month,
                                                  java.util.Collection<UUID> employeeIds) {
        if (employeeIds.isEmpty()) {
            return Map.of();
        }
        FinancialYear fy = FinancialYear.of(month.atDay(1));
        java.time.YearMonth fyStart = java.time.YearMonth.from(fy.start());

        Map<UUID, List<CompensationRecord>> history = new HashMap<>();
        for (CompensationRecord r : compensationRepository.findByCompanyIdOrderByEffectiveDateDescCreatedAtDesc(companyId)) {
            if (employeeIds.contains(r.getEmployeeId())) {
                history.computeIfAbsent(r.getEmployeeId(), k -> new ArrayList<>()).add(r);
            }
        }
        Map<UUID, Employee> employees = new HashMap<>();
        for (Employee e : employeeRepository.findByCompanyId(companyId)) {
            employees.put(e.getId(), e);
        }
        Map<UUID, Map<String, com.calyvora.payroll.PayslipSnapshot>> locked = new HashMap<>();
        if (month.isAfter(fyStart)) {
            for (com.calyvora.payroll.PayslipSnapshot s : snapshotRepository.findByCompanyIdAndMonthBetween(
                    companyId, fyStart.toString(), month.minusMonths(1).toString())) {
                locked.computeIfAbsent(s.getEmployeeId(), k -> new HashMap<>()).put(s.getMonth(), s);
            }
        }
        Map<UUID, TdsOpeningBalance> openings = new HashMap<>();
        for (TdsOpeningBalance o : openingRepository.findByCompanyIdAndFinancialYear(companyId, fy.label())) {
            openings.put(o.getEmployeeId(), o);
        }

        Map<UUID, YearPosition> out = new HashMap<>();
        for (UUID id : employeeIds) {
            List<CompensationRecord> h = history.get(id);
            Employee emp = employees.get(id);
            if (h == null || h.isEmpty() || emp == null) {
                continue;
            }
            TdsOpeningBalance opening = openings.get(id);
            java.time.YearMonth coveredThrough = opening == null ? null : java.time.YearMonth.parse(opening.getCoveredThrough());
            BigDecimal income = opening == null ? BigDecimal.ZERO : opening.getIncome();
            BigDecimal withheld = opening == null ? BigDecimal.ZERO : opening.getTds();
            int spread = 0, openPast = 0;
            Map<String, com.calyvora.payroll.PayslipSnapshot> mine = locked.getOrDefault(id, Map.of());
            for (int i = 0; i < 12; i++) {
                java.time.YearMonth m = fyStart.plusMonths(i);
                if (coveredThrough != null && !m.isAfter(coveredThrough)) {
                    continue;   // paid and taxed before Orbit; in the opening balance
                }
                com.calyvora.payroll.PayslipSnapshot snap = m.isBefore(month) ? mine.get(m.toString()) : null;
                if (snap != null) {
                    income = income.add(snap.getEarnedGross());
                    withheld = withheld.add(snap.getIncomeTax() == null ? BigDecimal.ZERO : snap.getIncomeTax());
                    continue;
                }
                BigDecimal g = com.calyvora.people.SalaryCalendar.grossForMonth(h, m, emp.getStartDate(), emp.getEndDate());
                if (g != null && g.signum() > 0) {
                    income = income.add(g);
                    spread++;
                    if (m.isBefore(month)) openPast++;
                }
            }
            out.put(id, new YearPosition(income, withheld, spread, openPast));
        }
        return out;
    }

    /** The year's tax per employee, from their declaration or the default regime with nothing claimed. */
    private Map<UUID, IncomeTaxCalculator.Result> annualTaxFor(UUID companyId, FinancialYear fy,
                                                               Map<UUID, BigDecimal> annualSalary) {
        if (annualSalary.isEmpty()) {
            return Map.of();
        }
        List<TaxDeclaration> declarations = declarationRepository
                .findByCompanyIdAndFinancialYear(companyId, fy.label());
        Map<UUID, TaxDeclaration> byEmployee = new HashMap<>();
        for (TaxDeclaration d : declarations) {
            byEmployee.put(d.getEmployeeId(), d);
        }
        Map<UUID, Map<TaxDeduction, BigDecimal>> itemsByDeclaration = new HashMap<>();
        if (!declarations.isEmpty()) {
            for (TaxDeclarationItem item : itemRepository.findByDeclarationIdIn(
                    declarations.stream().map(TaxDeclaration::getId).toList())) {
                itemsByDeclaration
                        .computeIfAbsent(item.getDeclarationId(), k -> new EnumMap<>(TaxDeduction.class))
                        .put(item.getDeduction(), item.getAmount());
            }
        }

        Map<UUID, IncomeTaxCalculator.Result> out = new HashMap<>();
        for (Map.Entry<UUID, BigDecimal> e : annualSalary.entrySet()) {
            TaxDeclaration d = byEmployee.get(e.getKey());
            // No declaration is not "no tax" — it is the statutory default regime with nothing
            // claimed, which is usually the higher bill. Treating silence as exempt would under-
            // withhold from precisely the people who never got round to filling the form in.
            TaxRegime regime = d == null ? TaxRegime.DEFAULT : d.getRegime();
            Map<TaxDeduction, BigDecimal> declared = d == null
                    ? Map.of()
                    : itemsByDeclaration.getOrDefault(d.getId(), Map.of());
            out.put(e.getKey(), IncomeTaxCalculator.compute(
                    new IncomeTaxCalculator.Input(e.getValue(), regime, declared)));
        }
        return out;
    }

    // ---- HR -----------------------------------------------------------------------------------

    /**
     * Everybody's declaration for the year.
     *
     * <p>Names, salaries and declarations are each read once for the whole list rather than per
     * person — the shape that has been the cause of every performance defect in this codebase.
     */
    @Transactional(readOnly = true)
    public List<TaxDtos.DeclarationSummaryRow> allDeclarations(AuthPrincipal principal, String year) {
        UUID companyId = TenantContext.getCompanyId();
        if (!permissions.has(principal, com.calyvora.access.Permission.TAX_MANAGE)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "You do not have permission to perform this action");
        }
        FinancialYear fy = yearOrCurrent(year);

        List<Employee> employees = employeeRepository.findByCompanyId(companyId);
        Map<UUID, String> names = namesOf(employees);

        Map<UUID, CompensationRecord> salaries = new HashMap<>();
        for (CompensationRecord r : compensationRepository
                .findByCompanyIdOrderByEffectiveDateDescCreatedAtDesc(companyId)) {
            salaries.putIfAbsent(r.getEmployeeId(), r);
        }

        List<TaxDeclaration> declarations = declarationRepository
                .findByCompanyIdAndFinancialYear(companyId, fy.label());
        Map<UUID, TaxDeclaration> byEmployee = new HashMap<>();
        for (TaxDeclaration d : declarations) {
            byEmployee.put(d.getEmployeeId(), d);
        }
        Map<UUID, Map<TaxDeduction, BigDecimal>> itemsByDeclaration = new HashMap<>();
        if (!declarations.isEmpty()) {
            for (TaxDeclarationItem item : itemRepository.findByDeclarationIdIn(
                    declarations.stream().map(TaxDeclaration::getId).toList())) {
                itemsByDeclaration
                        .computeIfAbsent(item.getDeclarationId(), k -> new EnumMap<>(TaxDeduction.class))
                        .put(item.getDeduction(), item.getAmount());
            }
        }

        List<TaxDtos.DeclarationSummaryRow> rows = new ArrayList<>();
        for (Employee e : employees) {
            CompensationRecord salary = salaries.get(e.getId());
            if (salary == null) {
                continue;   // nobody on payroll, nothing to tax
            }
            TaxDeclaration d = byEmployee.get(e.getId());
            TaxRegime regime = d == null ? TaxRegime.DEFAULT : d.getRegime();
            Map<TaxDeduction, BigDecimal> declared = d == null
                    ? Map.of()
                    : itemsByDeclaration.getOrDefault(d.getId(), Map.of());
            IncomeTaxCalculator.Result result = IncomeTaxCalculator.compute(
                    new IncomeTaxCalculator.Input(salary.getAnnualAmount(), regime, declared));
            BigDecimal totalDeclared = declared.values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            rows.add(new TaxDtos.DeclarationSummaryRow(e.getId().toString(),
                    names.getOrDefault(e.getId(), "Employee"), regime,
                    d == null ? "NOT_STARTED" : d.getStatus().name(),
                    totalDeclared, result.totalTax()));
        }
        rows.sort(Comparator.comparing(TaxDtos.DeclarationSummaryRow::employeeName,
                String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    /** HR opens declarations in April and closes them before the last run of the year. */
    @Transactional
    public boolean setWindow(AuthPrincipal principal, boolean open) {
        UUID companyId = TenantContext.getCompanyId();
        if (!permissions.has(principal, com.calyvora.access.Permission.TAX_MANAGE)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "You do not have permission to perform this action");
        }
        CompanySettings settings = settingsRepository.findById(companyId)
                .orElseGet(() -> settingsRepository.save(new CompanySettings(companyId)));
        settings.setTaxDeclarationsOpen(open);
        settingsRepository.save(settings);
        return open;
    }

    // ---- helpers ------------------------------------------------------------------------------

    private boolean windowOpen(UUID companyId) {
        return settingsRepository.findById(companyId)
                .map(CompanySettings::isTaxDeclarationsOpen)
                .orElse(true);
    }

    private Employee self(AuthPrincipal principal) {
        return orgScope.selfOf(principal)
                .orElseThrow(() -> new NotFoundException(
                        "You do not have an employee profile, so there is no tax declaration to make."));
    }

    private CompensationRecord currentSalary(UUID employeeId) {
        List<CompensationRecord> records = compensationRepository
                .findByEmployeeIdOrderByEffectiveDateDescCreatedAtDesc(employeeId);
        return records.isEmpty() ? null : records.get(0);
    }

    private Map<UUID, String> namesOf(List<Employee> employees) {
        List<UUID> userIds = employees.stream()
                .map(Employee::getUserId)
                .filter(java.util.Objects::nonNull)
                .toList();
        Map<UUID, String> byUser = new HashMap<>();
        for (User u : userRepository.findAllById(userIds)) {
            byUser.put(u.getId(), (u.getFirstName() + " " + u.getLastName()).trim());
        }
        Map<UUID, String> byEmployee = new HashMap<>();
        for (Employee e : employees) {
            if (e.getUserId() != null && byUser.containsKey(e.getUserId())) {
                byEmployee.put(e.getId(), byUser.get(e.getUserId()));
            }
        }
        return byEmployee;
    }

    private static Map<TaxDeduction, BigDecimal> itemsOf(List<TaxDeclarationItem> items) {
        Map<TaxDeduction, BigDecimal> out = new EnumMap<>(TaxDeduction.class);
        for (TaxDeclarationItem i : items) {
            out.put(i.getDeduction(), i.getAmount());
        }
        return out;
    }

    private static FinancialYear yearOrCurrent(String year) {
        return year == null || year.isBlank()
                ? FinancialYear.of(LocalDate.now())
                : FinancialYear.parse(year);
    }

    private static TaxDtos.DeclarationResponse toResponse(TaxDeclaration d, List<TaxDeclarationItem> items,
                                                          boolean windowOpen) {
        Map<String, BigDecimal> declared = new LinkedHashMap<>();
        for (TaxDeclarationItem i : items) {
            declared.put(i.getDeduction().name(), i.getAmount());
        }
        return new TaxDtos.DeclarationResponse(d.getFinancialYear(), d.getRegime(),
                d.getStatus().name(),
                d.getSubmittedAt() == null ? null : d.getSubmittedAt().toString(),
                declared, windowOpen, options());
    }

    private static TaxDtos.DeclarationResponse emptyDeclaration(FinancialYear fy, boolean windowOpen) {
        return new TaxDtos.DeclarationResponse(fy.label(), TaxRegime.DEFAULT, "NOT_STARTED", null,
                Map.of(), windowOpen, options());
    }

    private static List<TaxDtos.DeductionOption> options() {
        List<TaxDtos.DeductionOption> out = new ArrayList<>();
        for (TaxDeduction d : TaxDeduction.values()) {
            out.add(TaxDtos.DeductionOption.of(d));
        }
        return out;
    }
}
