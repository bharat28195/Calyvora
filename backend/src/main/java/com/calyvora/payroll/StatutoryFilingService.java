package com.calyvora.payroll;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.security.TenantContext;
import com.calyvora.feature.Feature;
import com.calyvora.feature.FeatureService;
import com.calyvora.people.EmployeeFinance;
import com.calyvora.people.EmployeeFinanceRepository;
import com.calyvora.people.dto.PayslipResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * The files a company uploads to the government portals, built from FINALISED months only.
 *
 * <p>No portal (EPFO, ESIC, the income-tax TDS system) accepts filings from third-party software, so
 * this is what Keka and Zoho do too: produce the exact upload file, and the company uploads it and
 * pays the challan. Built from locked months because a return has to match what was paid — a file
 * built from an open month would change if somebody edited a salary before upload.
 *
 * <p>Every file skips rows it cannot make valid (a PF member without a UAN, say) and reports them,
 * rather than writing a line the portal will reject for the whole file.
 */
@Service
public class StatutoryFilingService {

    /** The EPS and EDLI wage ceiling the ECR states wages against. */
    private static final BigDecimal EPS_CEILING = new BigDecimal("15000");

    private final PayrollMonthRepository monthRepository;
    private final PayslipSnapshotRepository snapshotRepository;
    private final EmployeeFinanceRepository financeRepository;
    private final StatutorySettingsService settingsService;
    private final FeatureService featureService;
    private final ObjectMapper objectMapper;
    private final com.calyvora.people.CompensationRepository compensationRepository;
    private final com.calyvora.people.EmployeeRepository employeeRepository;
    private final com.calyvora.identity.UserRepository userRepository;

    public StatutoryFilingService(PayrollMonthRepository monthRepository,
                                  PayslipSnapshotRepository snapshotRepository,
                                  EmployeeFinanceRepository financeRepository,
                                  StatutorySettingsService settingsService,
                                  FeatureService featureService, ObjectMapper objectMapper,
                                  com.calyvora.people.CompensationRepository compensationRepository,
                                  com.calyvora.people.EmployeeRepository employeeRepository,
                                  com.calyvora.identity.UserRepository userRepository) {
        this.compensationRepository = compensationRepository;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.monthRepository = monthRepository;
        this.snapshotRepository = snapshotRepository;
        this.financeRepository = financeRepository;
        this.settingsService = settingsService;
        this.featureService = featureService;
        this.objectMapper = objectMapper;
    }

    /** A file plus the people left out of it and why. */
    public record FilingFile(String filename, String contentType, String content, List<Skipped> skipped) {
    }

    public record Skipped(String employeeId, String name, String reason) {
    }

    // ---- PF: the ECR -----------------------------------------------------------------------------

    /**
     * The EPFO's Electronic Challan-cum-Return, version 2: one line per member, eleven fields joined
     * by {@code #~#}, whole rupees, no header. UAN, name, gross wages, EPF wages, EPS wages, EDLI
     * wages, EPF (employee) contribution, EPS contribution, EPF−EPS difference (the employer's EPF
     * share), NCP (non-contributing) days, refund of advances.
     */
    @Transactional(readOnly = true)
    public FilingFile ecr(String month) {
        UUID companyId = TenantContext.getCompanyId();
        String ym = requireFinalized(companyId, month);
        StringBuilder out = new StringBuilder();
        List<Skipped> skipped = new ArrayList<>();
        for (Slip s : slips(companyId, ym)) {
            if (s.snapshot().getEmployeePf() == null) {
                continue;   // not a PF member this month
            }
            String uan = s.payslip().uan();
            if (uan == null || !uan.matches("\\d{12}")) {
                skipped.add(new Skipped(s.payslip().employeeId(), s.payslip().employeeName(), "No 12-digit UAN"));
                continue;
            }
            BigDecimal pfWages = s.snapshot().getPfWages();
            boolean pension = s.snapshot().getEmployerEps() != null && s.snapshot().getEmployerEps().signum() > 0;
            BigDecimal ceiling = pfWages.min(EPS_CEILING);
            out.append(String.join("#~#",
                    uan,
                    ecrName(s.payslip().employeeName()),
                    rupees(earnedGross(s.payslip())),
                    rupees(pfWages),
                    rupees(pension ? ceiling : BigDecimal.ZERO),
                    rupees(ceiling),
                    rupees(s.snapshot().getEmployeePf()),
                    rupees(nz(s.snapshot().getEmployerEps())),
                    rupees(nz(s.snapshot().getEmployerEpf())),
                    String.valueOf(Math.round(s.payslip().lopDays())),
                    "0")).append("\n");
        }
        return new FilingFile("ECR_" + ym + ".txt", "text/plain", out.toString(), skipped);
    }

    // ---- ESI: the monthly contribution file ------------------------------------------------------

    /**
     * The ESIC monthly contribution upload, in the column order of the ESIC's own template: IP
     * number, IP name, days paid, total monthly wages, reason code for zero days, last working day.
     * The portal takes its .xls template; this CSV opens in a spreadsheet and pastes straight in.
     */
    @Transactional(readOnly = true)
    public FilingFile esi(String month) {
        UUID companyId = TenantContext.getCompanyId();
        String ym = requireFinalized(companyId, month);
        Map<UUID, EmployeeFinance> finance = financeByEmployee(companyId);
        int daysInMonth = YearMonth.parse(ym).lengthOfMonth();
        StringBuilder out = new StringBuilder(
                "IP Number,IP Name,No of Days for which wages paid/payable during the month,"
                        + "Total Monthly Wages,Reason Code for Zero workings days,Last Working Day\n");
        List<Skipped> skipped = new ArrayList<>();
        for (Slip s : slips(companyId, ym)) {
            if (s.snapshot().getEsiWages() == null) {
                continue;   // not covered this period
            }
            EmployeeFinance f = finance.get(s.snapshot().getEmployeeId());
            String ip = f == null ? null : f.getEsiNumber();
            if (ip == null || ip.isBlank()) {
                skipped.add(new Skipped(s.payslip().employeeId(), s.payslip().employeeName(), "No ESI IP number"));
                continue;
            }
            long days = Math.max(0, Math.round(daysInMonth - s.payslip().lopDays()));
            out.append(csv(ip)).append(',').append(csv(s.payslip().employeeName())).append(',')
                    .append(days).append(',').append(rupees(s.snapshot().getEsiWages())).append(',')
                    .append(days == 0 ? "1" : "").append(',').append("\n");
        }
        return new FilingFile("ESI_" + ym + ".csv", "text/csv", out.toString(), skipped);
    }

    // ---- Professional tax: the state-wise summary ------------------------------------------------

    /** Per-employee professional tax for the month, grouped by state with a total per state. */
    @Transactional(readOnly = true)
    public FilingFile professionalTax(String month) {
        UUID companyId = TenantContext.getCompanyId();
        String ym = requireFinalized(companyId, month);
        Map<String, List<Slip>> byState = new TreeMap<>();
        for (Slip s : slips(companyId, ym)) {
            BigDecimal pt = s.snapshot().getProfessionalTax();
            if (pt != null && pt.signum() > 0) {
                String state = s.snapshot().getPtState() == null ? "??" : s.snapshot().getPtState();
                byState.computeIfAbsent(state, k -> new ArrayList<>()).add(s);
            }
        }
        StringBuilder out = new StringBuilder("State,Employee,Gross wages,Professional tax\n");
        for (Map.Entry<String, List<Slip>> e : byState.entrySet()) {
            BigDecimal total = BigDecimal.ZERO;
            for (Slip s : e.getValue()) {
                out.append(e.getKey()).append(',').append(csv(s.payslip().employeeName())).append(',')
                        .append(rupees(earnedGross(s.payslip()))).append(',')
                        .append(rupees(s.snapshot().getProfessionalTax())).append("\n");
                total = total.add(s.snapshot().getProfessionalTax());
            }
            out.append(e.getKey()).append(",TOTAL (").append(e.getValue().size()).append(" employees),,")
                    .append(rupees(total)).append("\n");
        }
        return new FilingFile("PT_" + ym + ".csv", "text/csv", out.toString(), List.of());
    }

    // ---- Labour Welfare Fund: the contribution summary -------------------------------------------

    /** Employee and employer LWF for the month, by state, with a total per state. */
    @Transactional(readOnly = true)
    public FilingFile labourWelfareFund(String month) {
        UUID companyId = TenantContext.getCompanyId();
        String ym = requireFinalized(companyId, month);
        Map<String, List<Slip>> byState = new TreeMap<>();
        for (Slip s : slips(companyId, ym)) {
            if (s.snapshot().getLwfEmployee() != null || s.snapshot().getLwfEmployer() != null) {
                String state = s.snapshot().getPtState() == null ? "??" : s.snapshot().getPtState();
                byState.computeIfAbsent(state, k -> new ArrayList<>()).add(s);
            }
        }
        StringBuilder out = new StringBuilder("State,Employee,Employee share,Employer share\n");
        for (Map.Entry<String, List<Slip>> e : byState.entrySet()) {
            BigDecimal ee = BigDecimal.ZERO, er = BigDecimal.ZERO;
            for (Slip s : e.getValue()) {
                BigDecimal a = nz(s.snapshot().getLwfEmployee()), b = nz(s.snapshot().getLwfEmployer());
                out.append(e.getKey()).append(',').append(csv(s.payslip().employeeName())).append(',')
                        .append(a.toPlainString()).append(',').append(b.toPlainString()).append("\n");
                ee = ee.add(a);
                er = er.add(b);
            }
            out.append(e.getKey()).append(",TOTAL (").append(e.getValue().size()).append(" employees),")
                    .append(ee.toPlainString()).append(',').append(er.toPlainString()).append("\n");
        }
        return new FilingFile("LWF_" + ym + ".csv", "text/csv", out.toString(), List.of());
    }

    // ---- TDS: Form 24Q deductee data -------------------------------------------------------------

    /**
     * The deductee rows of Form 24Q Annexure I for a quarter: one row per employee per month, salary
     * paid and TDS deducted under section 192. This is the data the return-preparation utility needs;
     * the challan details are added there against the payment the company made. Every month in the
     * quarter must be finalised.
     *
     * @param quarter e.g. {@code 2026-27-Q3} — financial-year quarters: Q1 Apr–Jun … Q4 Jan–Mar
     */
    @Transactional(readOnly = true)
    public FilingFile form24q(String quarter) {
        UUID companyId = TenantContext.getCompanyId();
        List<String> months = quarterMonths(quarter);
        for (String m : months) {
            if (!monthRepository.existsByCompanyIdAndMonth(companyId, m)) {
                throw new ApiException(ErrorCode.CONFLICT, "Finalise " + m + " first — 24Q is built from finalised months");
            }
        }
        Map<UUID, EmployeeFinance> finance = financeByEmployee(companyId);
        StringBuilder out = new StringBuilder(
                "Month,Employee,PAN,Section,Amount paid,TDS deducted,Date of payment,PAN status\n");
        List<Skipped> skipped = new ArrayList<>();
        BigDecimal paid = BigDecimal.ZERO, deducted = BigDecimal.ZERO;
        for (String m : months) {
            String lastDay = YearMonth.parse(m).atEndOfMonth().toString();
            for (Slip s : slips(companyId, m)) {
                EmployeeFinance f = finance.get(s.snapshot().getEmployeeId());
                String pan = f == null ? null : f.getPanNumber();
                boolean validPan = pan != null && pan.matches("[A-Z]{5}[0-9]{4}[A-Z]");
                if (!validPan) {
                    skipped.add(new Skipped(s.payslip().employeeId(), s.payslip().employeeName(),
                            "No valid PAN — tax must be deducted at the higher rate under section 206AA"));
                }
                BigDecimal tds = nz(s.snapshot().getIncomeTax());
                BigDecimal amount = earnedGross(s.payslip());
                out.append(m).append(',').append(csv(s.payslip().employeeName())).append(',')
                        .append(validPan ? pan : "PANNOTAVBL").append(",192,")
                        .append(rupees(amount)).append(',').append(rupees(tds)).append(',')
                        .append(lastDay).append(',').append(validPan ? "Valid" : "Not available").append("\n");
                paid = paid.add(amount);
                deducted = deducted.add(tds);
            }
        }
        out.append("TOTAL,,,,").append(rupees(paid)).append(',').append(rupees(deducted)).append(",,\n");
        return new FilingFile("24Q_" + quarter + ".csv", "text/csv", out.toString(), skipped);
    }

    // ---- readiness -------------------------------------------------------------------------------

    public record Issue(String employeeId, String name, String severity, String message) {
    }

    /**
     * What will stop a return from being filed, before the month is locked: company registration
     * numbers that the files need, and employees missing the identifiers the portals key on.
     */
    @Transactional(readOnly = true)
    public List<Issue> readiness() {
        UUID companyId = TenantContext.getCompanyId();
        // Only people on payroll — somebody with no salary is in no return.
        java.util.Set<UUID> paid = new java.util.HashSet<>();
        for (var r : compensationRepository.findByCompanyIdOrderByEffectiveDateDescCreatedAtDesc(companyId)) {
            paid.add(r.getEmployeeId());
        }
        Map<UUID, String> userNames = new HashMap<>();
        for (var u : userRepository.findByCompanyIdOrderByCreatedAtAsc(companyId)) {
            userNames.put(u.getId(), u.fullName());
        }
        Map<UUID, String> employeeNames = new TreeMap<>();
        for (var e : employeeRepository.findByCompanyId(companyId)) {
            if (paid.contains(e.getId())) {
                employeeNames.put(e.getId(), userNames.getOrDefault(e.getUserId(), "Employee"));
            }
        }
        boolean statutory = featureService.isEnabled(companyId, Feature.STATUTORY_PAYROLL);
        boolean incomeTax = featureService.isEnabled(companyId, Feature.INCOME_TAX);
        StatutorySettings ss = settingsService.effective(companyId);
        List<Issue> issues = new ArrayList<>();
        Map<UUID, EmployeeFinance> finance = financeByEmployee(companyId);

        boolean anyPf = false, anyEsi = false;
        for (Map.Entry<UUID, String> e : employeeNames.entrySet()) {
            EmployeeFinance f = finance.get(e.getKey());
            String id = e.getKey().toString(), name = e.getValue();
            if (f == null) {
                if (statutory || incomeTax) {
                    issues.add(new Issue(id, name, "WARNING", "No finance record yet — PF, ESI and PAN are all unset"));
                }
                continue;
            }
            if (statutory && "ENABLED".equals(f.getPfStatus())) {
                anyPf = true;
                if (f.getUan() == null) issues.add(new Issue(id, name, "ERROR", "Enrolled in PF but has no UAN — left out of the ECR"));
            }
            if (statutory && ss.isEsiEnabled() && "ELIGIBLE".equals(f.getEsiStatus())) {
                anyEsi = true;
                if (f.getEsiNumber() == null) issues.add(new Issue(id, name, "ERROR", "ESI-eligible but has no IP number — left out of the ESI file"));
            }
            if (statutory && ss.isPtEnabled()) {
                String code = ProfessionalTaxCalculator.stateCode(f.getPtState());
                if (code == null) {
                    issues.add(new Issue(id, name, "WARNING", "No professional-tax state — no PT will be deducted"));
                } else if ("MH".equals(code) && f.getGender() == null) {
                    issues.add(new Issue(id, name, "WARNING", "Works in Maharashtra with no gender recorded — women are exempt up to ₹25,000"));
                } else if (List.of("ML", "TR").contains(code)) {
                    issues.add(new Issue(id, name, "WARNING", "Orbit does not hold this state's PT schedule yet — add PT as a template deduction"));
                }
            }
            if (statutory && ss.isLwfEnabled()) {
                var lwf = LwfCalculator.compute(f.getPtState(), BigDecimal.ONE, YearMonth.of(2026, 12));
                if (!lwf.supported()) {
                    issues.add(new Issue(id, name, "WARNING", "Orbit does not hold this state's Labour Welfare Fund amounts yet — add LWF as a template deduction"));
                }
            }
            if (incomeTax && (f.getPanNumber() == null || !f.getPanNumber().matches("[A-Z]{5}[0-9]{4}[A-Z]"))) {
                issues.add(new Issue(id, name, "ERROR", "No valid PAN — TDS must be deducted at the higher rate and 24Q will flag it"));
            }
        }
        if (anyPf && ss.getPfEstablishmentCode() == null) {
            issues.add(new Issue(null, "Company", "ERROR", "PF establishment code is not set (Payroll → Statutory)"));
        }
        if (anyEsi && ss.getEsiEmployerCode() == null) {
            issues.add(new Issue(null, "Company", "ERROR", "ESI employer code is not set (Payroll → Statutory)"));
        }
        if (incomeTax && ss.getTan() == null) {
            issues.add(new Issue(null, "Company", "ERROR", "TAN is not set — needed for TDS returns and Form 16"));
        }
        issues.sort(Comparator.comparing(Issue::severity).thenComparing(Issue::name, String.CASE_INSENSITIVE_ORDER));
        return issues;
    }

    // ---- internals -------------------------------------------------------------------------------

    private record Slip(PayslipSnapshot snapshot, PayslipResponse payslip) {
    }

    private List<Slip> slips(UUID companyId, String month) {
        List<Slip> out = new ArrayList<>();
        for (PayslipSnapshot s : snapshotRepository.findByCompanyIdAndMonth(companyId, month)) {
            try {
                out.add(new Slip(s, objectMapper.readValue(s.getPayload(), PayslipResponse.class)));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Unreadable payslip snapshot " + s.getId(), e);
            }
        }
        out.sort(Comparator.comparing(sl -> sl.payslip().employeeName(), String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private Map<UUID, EmployeeFinance> financeByEmployee(UUID companyId) {
        Map<UUID, EmployeeFinance> out = new HashMap<>();
        for (EmployeeFinance f : financeRepository.findByCompanyId(companyId)) {
            out.put(f.getEmployeeId(), f);
        }
        return out;
    }

    private String requireFinalized(UUID companyId, String month) {
        String ym;
        try {
            ym = YearMonth.parse(month).toString();
        } catch (RuntimeException e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Month must look like 2026-10");
        }
        if (!monthRepository.existsByCompanyIdAndMonth(companyId, ym)) {
            throw new ApiException(ErrorCode.CONFLICT, "Finalise " + ym + " first — returns are built from finalised months");
        }
        return ym;
    }

    static BigDecimal earnedGross(PayslipResponse p) {
        return p.earnedGross();
    }

    static List<String> quarterMonths(String quarter) {
        // "2026-27-Q3": FY starting April 2026, third quarter = Oct–Dec 2026.
        if (quarter == null || !quarter.matches("\\d{4}-\\d{2}-Q[1-4]")) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Quarter must look like 2026-27-Q3");
        }
        int startYear = Integer.parseInt(quarter.substring(0, 4));
        int q = quarter.charAt(quarter.length() - 1) - '0';
        YearMonth first = YearMonth.of(startYear, 4).plusMonths((q - 1) * 3L);
        return List.of(first.toString(), first.plusMonths(1).toString(), first.plusMonths(2).toString());
    }

    /** The ECR wants plain upper-case names: letters, spaces and dots only. */
    private static String ecrName(String name) {
        return name == null ? "" : name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z .]", " ").replaceAll("\\s+", " ").trim();
    }

    private static String rupees(BigDecimal v) {
        return v == null ? "0" : v.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String csv(String v) {
        if (v == null) return "";
        return v.contains(",") || v.contains("\"") ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
    }
}
