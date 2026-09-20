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

    public TaxService(TaxDeclarationRepository declarationRepository,
                      TaxDeclarationItemRepository itemRepository,
                      EmployeeRepository employeeRepository,
                      UserRepository userRepository,
                      CompensationRepository compensationRepository,
                      CompanySettingsRepository settingsRepository,
                      OrgScope orgScope) {
        this.declarationRepository = declarationRepository;
        this.itemRepository = itemRepository;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.compensationRepository = compensationRepository;
        this.settingsRepository = settingsRepository;
        this.orgScope = orgScope;
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
        BigDecimal gross = current == null ? BigDecimal.ZERO : current.getAnnualAmount();
        String currency = current == null || current.getCurrency() == null ? "INR" : current.getCurrency();

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
        BigDecimal perMonth = result.monthlyTds();
        BigDecimal deductedSoFar = perMonth.multiply(BigDecimal.valueOf(elapsed));
        if (deductedSoFar.compareTo(result.totalTax()) > 0) {
            deductedSoFar = result.totalTax();
        }
        BigDecimal remaining = result.totalTax().subtract(deductedSoFar).max(BigDecimal.ZERO);
        int monthsLeft = Math.max(1, 12 - elapsed);
        BigDecimal nextMonth = remaining.divide(BigDecimal.valueOf(monthsLeft), 0, RoundingMode.HALF_UP);

        return new TaxDtos.ComputationResponse(
                fy.label(), regime, currency,
                result.grossSalary(), result.standardDeduction(),
                deductionRows(result), result.totalDeductions(), result.taxableIncome(),
                bandRows(result), result.taxOnIncome(), result.rebate(),
                result.surcharge(), result.cess(), result.totalTax(), perMonth,
                elapsed, deductedSoFar, remaining, nextMonth,
                new TaxDtos.RegimeComparison(oldTax, newTax, cheaper, saving));
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
        if (!orgScope.seesWholeCompany(principal)) {
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
        if (!orgScope.seesWholeCompany(principal)) {
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
