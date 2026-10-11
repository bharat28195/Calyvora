package com.calyvora.people;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.TenantContext;
import com.calyvora.document.DocumentKind;
import com.calyvora.document.DocumentService;
import com.calyvora.expense.ExpenseClaim;
import com.calyvora.expense.ExpenseClaimRepository;
import com.calyvora.expense.ExpenseStatus;
import com.calyvora.identity.UserRepository;
import com.calyvora.people.dto.LeaveTypeBalanceResponse;
import com.calyvora.people.dto.PayslipResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Full and final settlement (PD-73): what a departing employee is owed, and what is recovered,
 * worked out from the systems that already know.
 *
 * <ul>
 *   <li><b>Final salary</b> is the final month's payslip from the payroll engine, which already pays
 *       only up to the last working day and applies loss of pay, PF, ESI, professional tax and TDS —
 *       so the settlement and a payslip can never disagree. If that month's payroll has been
 *       finalised, the salary was paid there and is not paid twice.</li>
 *   <li><b>Leave encashment</b>: the balance on the last day of the leave types HR chooses
 *       (Vacation by default) × a day's pay (basic ÷ 26 by default).</li>
 *   <li><b>Gratuity</b>: see {@link SettlementMath}.</li>
 *   <li><b>Notice shortfall</b>: notice required (from the letterpad's standard terms, or set here)
 *       minus notice served (from the day the exit began to the last day) × gross ÷ 30, unless waived.</li>
 *   <li><b>Expense claims</b> approved but not yet reimbursed are paid with the settlement.</li>
 *   <li>Anything else — a bonus, an asset not returned, an advance — HR adds as a line.</li>
 * </ul>
 *
 * <p>A draft is recomputed every time it is opened. Approving freezes the statement and issues the
 * F&amp;F letter; marking it paid records the date and reference.
 */
@Service
public class SettlementService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    private static final Set<String> BASES = Set.of("BASIC_26", "BASIC_30", "GROSS_30");

    private final SettlementRepository settlements;
    private final EmployeeRepository employees;
    private final UserRepository users;
    private final DepartmentRepository departments;
    private final CompensationService compensation;
    private final LeaveService leave;
    private final ExpenseClaimRepository expenses;
    private final DocumentService documents;
    private final com.calyvora.document.LetterheadRepository letterheads;
    private final ObjectMapper json;

    public SettlementService(SettlementRepository settlements, EmployeeRepository employees, UserRepository users,
                             DepartmentRepository departments, @Lazy CompensationService compensation,
                             @Lazy LeaveService leave, ExpenseClaimRepository expenses, @Lazy DocumentService documents,
                             com.calyvora.document.LetterheadRepository letterheads, ObjectMapper json) {
        this.settlements = settlements;
        this.employees = employees;
        this.users = users;
        this.departments = departments;
        this.compensation = compensation;
        this.leave = leave;
        this.expenses = expenses;
        this.documents = documents;
        this.letterheads = letterheads;
        this.json = json;
    }

    // ---- shapes ----

    public record Line(String label, BigDecimal amount, String note) {}

    public record LeaveLine(String type, BigDecimal available, boolean encashed) {}

    public record ExtraLine(String label, BigDecimal amount, boolean deduction) {}

    public record Statement(String employeeName, String employeeNo, String designation, String department,
                            String startDate, String lastWorkingDay, String currency, String service,
                            boolean gratuityDue, BigDecimal monthlyGross, BigDecimal monthlyBasic, BigDecimal dayRate,
                            Integer noticeRequired, Integer noticeServed, int noticeShortfall,
                            List<LeaveLine> leave, List<Line> earnings, List<Line> deductions,
                            BigDecimal totalEarnings, BigDecimal totalDeductions, BigDecimal netPayable,
                            String payBy, List<String> notes) {}

    public record View(String id, String employeeId, String status, String encashTypes, BigDecimal encashDays,
                       String encashBasis, Integer noticeDays, boolean noticeWaived, boolean includeGratuity,
                       List<ExtraLine> extraLines, String note, Statement statement, String approvedAt,
                       String documentId, String paidOn, String paidReference) {}

    public record Payload(String encashTypes, BigDecimal encashDays, Boolean clearEncashDays, String encashBasis,
                          Integer noticeDays, Boolean clearNoticeDays, Boolean noticeWaived, Boolean includeGratuity,
                          List<ExtraLine> extraLines, String note) {}

    public record PaidRequest(String paidOn, String reference) {}

    // ---- operations ----

    @Transactional
    public View get(UUID employeeId) {
        Employee e = leaving(employeeId);
        Settlement s = settlements.findByEmployeeIdAndCompanyId(employeeId, e.getCompanyId())
                .orElseGet(() -> settlements.save(new Settlement(e.getCompanyId(), employeeId)));
        return view(s, e);
    }

    @Transactional
    public View update(UUID employeeId, Payload p) {
        Employee e = leaving(employeeId);
        Settlement s = draft(employeeId, e);
        if (p.encashTypes() != null) {
            List<String> types = Arrays.stream(p.encashTypes().split(",")).map(String::trim).filter(t -> !t.isEmpty()).toList();
            for (String t : types) {
                try {
                    LeaveType.valueOf(t);
                } catch (IllegalArgumentException ex) {
                    throw invalid("Unknown leave type " + t);
                }
            }
            s.setEncashTypes(String.join(",", types));
        }
        if (Boolean.TRUE.equals(p.clearEncashDays())) s.setEncashDays(null);
        else if (p.encashDays() != null) {
            if (p.encashDays().signum() < 0 || p.encashDays().compareTo(BigDecimal.valueOf(400)) > 0) {
                throw invalid("Days to encash must be between 0 and 400.");
            }
            s.setEncashDays(p.encashDays());
        }
        if (p.encashBasis() != null) {
            if (!BASES.contains(p.encashBasis())) throw invalid("Choose basic ÷ 26, basic ÷ 30 or gross ÷ 30.");
            s.setEncashBasis(p.encashBasis());
        }
        if (Boolean.TRUE.equals(p.clearNoticeDays())) s.setNoticeDays(null);
        else if (p.noticeDays() != null) {
            if (p.noticeDays() < 0 || p.noticeDays() > 365) throw invalid("Notice must be between 0 and 365 days.");
            s.setNoticeDays(p.noticeDays());
        }
        if (p.noticeWaived() != null) s.setNoticeWaived(p.noticeWaived());
        if (p.includeGratuity() != null) s.setIncludeGratuity(p.includeGratuity());
        if (p.extraLines() != null) {
            if (p.extraLines().size() > 30) throw invalid("Keep it to 30 extra lines.");
            for (ExtraLine l : p.extraLines()) {
                if (l.label() == null || l.label().isBlank() || l.label().length() > 120) throw invalid("Each line needs a short description.");
                if (l.amount() == null || l.amount().signum() <= 0) throw invalid("Each line needs an amount above zero.");
            }
            s.setExtraLines(write(p.extraLines()));
        }
        if (p.note() != null) s.setNote(p.note().isBlank() ? null : p.note().trim().substring(0, Math.min(1000, p.note().trim().length())));
        s.touch();
        settlements.save(s);
        return view(s, e);
    }

    /** Freezes the statement and issues the F&F letter. */
    @Transactional
    public View approve(UUID employeeId, AuthPrincipal principal) {
        Employee e = leaving(employeeId);
        Settlement s = draft(employeeId, e);
        Statement st = compute(s, e);
        s.setStatementJson(write(st));
        s.setNetPayable(st.netPayable());
        s.approved(principal.userId(), Instant.now());
        s.setStatus(Settlement.Status.APPROVED);
        Map<String, String> fnf = new LinkedHashMap<>();
        fnf.put("fnf.salaryDue", amount(sumOf(st.earnings(), "Salary")));
        fnf.put("fnf.leaveEncashment", amount(sumOf(st.earnings(), "Leave encashment")));
        fnf.put("fnf.gratuity", amount(sumOf(st.earnings(), "Gratuity")));
        BigDecimal other = st.totalEarnings().subtract(sumOf(st.earnings(), "Salary"))
                .subtract(sumOf(st.earnings(), "Leave encashment")).subtract(sumOf(st.earnings(), "Gratuity"));
        fnf.put("fnf.bonus", amount(other));
        fnf.put("fnf.recoveries", amount(st.totalDeductions()));
        fnf.put("fnf.netPayable", amount(st.netPayable()));
        fnf.put("fnf.paymentDate", st.payBy());
        documents.issueByKind(DocumentKind.FNF_STATEMENT, employeeId, fnf, principal)
                .ifPresent(d -> s.setDocumentId(UUID.fromString(d.id())));
        s.touch();
        settlements.save(s);
        return view(s, e);
    }

    /** Back to draft, to correct something — only before it is paid. */
    @Transactional
    public View reopen(UUID employeeId) {
        Employee e = leaving(employeeId);
        Settlement s = settlements.findByEmployeeIdAndCompanyId(employeeId, e.getCompanyId())
                .orElseThrow(() -> new NotFoundException("No settlement yet"));
        if (s.getStatus() == Settlement.Status.PAID) throw new ApiException(ErrorCode.CONFLICT, "A paid settlement cannot be reopened.");
        s.setStatus(Settlement.Status.DRAFT);
        s.setStatementJson(null);
        s.setNetPayable(null);
        s.touch();
        settlements.save(s);
        return view(s, e);
    }

    @Transactional
    public View markPaid(UUID employeeId, PaidRequest req) {
        Employee e = leaving(employeeId);
        Settlement s = settlements.findByEmployeeIdAndCompanyId(employeeId, e.getCompanyId())
                .orElseThrow(() -> new NotFoundException("No settlement yet"));
        if (s.getStatus() != Settlement.Status.APPROVED) throw new ApiException(ErrorCode.CONFLICT, "Approve the settlement before marking it paid.");
        LocalDate on;
        try {
            on = req == null || req.paidOn() == null ? LocalDate.now(IST) : LocalDate.parse(req.paidOn());
        } catch (Exception ex) {
            throw invalid("Enter the date it was paid.");
        }
        String ref = req == null || req.reference() == null ? null : req.reference().trim();
        if (ref != null && ref.length() > 120) throw invalid("Keep the reference under 120 characters.");
        s.paid(on, ref == null || ref.isEmpty() ? null : ref);
        s.setStatus(Settlement.Status.PAID);
        s.touch();
        settlements.save(s);
        return view(s, e);
    }

    // ---- the calculation ----

    Statement compute(Settlement s, Employee e) {
        UUID companyId = e.getCompanyId();
        LocalDate last = e.getEndDate();
        List<Line> earnings = new ArrayList<>();
        List<Line> deductions = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        String name = users.findByIdAndCompanyId(e.getUserId(), companyId)
                .map(u -> u.getFirstName() + " " + u.getLastName()).orElse("—");
        String dept = e.getDepartmentId() == null ? null
                : departments.findByIdAndCompanyId(e.getDepartmentId(), companyId).map(Department::getName).orElse(null);

        // Salary on the last day: the rate everything else is worked from.
        var salary = compensation.salaryOn(e.getId(), last).orElse(null);
        BigDecimal gross = salary == null ? BigDecimal.ZERO : salary.monthlyGross();
        BigDecimal basic = salary == null ? BigDecimal.ZERO : salary.monthlyBasic();
        String currency = salary == null ? "INR" : salary.currency();
        if (salary == null) notes.add("No salary is on record, so pay-based amounts are zero. Add the salary under Compensation.");

        // 1. The final month's pay, from the payroll engine.
        YearMonth finalMonth = YearMonth.from(last);
        try {
            PayslipResponse slip = compensation.payslip(e.getId(), finalMonth.toString());
            if (slip.finalized()) {
                notes.add("Salary for " + MONTH.format(finalMonth) + " was paid in that month's payroll, so it is not included here.");
            } else {
                earnings.add(new Line("Salary for " + MONTH.format(finalMonth),
                        slip.gross(), "Up to " + LONG.format(last) + ", " + fmt(slip.payableDays()) + " payable days"));
                for (PayslipResponse.Line d : slip.deductions()) {
                    if (d.amount() != null && d.amount().signum() > 0) deductions.add(new Line(d.label(), d.amount(), "Final month"));
                }
            }
        } catch (NotFoundException ex) {
            notes.add("No payslip could be worked out for " + MONTH.format(finalMonth) + ": " + ex.getMessage() + ".");
        }

        // 2. Leave encashment.
        Set<String> encashTypes = Set.of(s.getEncashTypes().split(","));
        List<LeaveLine> leaveLines = new ArrayList<>();
        BigDecimal balance = BigDecimal.ZERO;
        for (LeaveTypeBalanceResponse b : leave.balancesOn(e.getId(), last)) {
            if ("UNPAID".equals(b.type())) continue;
            boolean encashed = encashTypes.contains(b.type());
            leaveLines.add(new LeaveLine(b.type(), b.availableDays(), encashed));
            if (encashed) balance = balance.add(b.availableDays());
        }
        BigDecimal days = s.getEncashDays() != null ? s.getEncashDays() : balance;
        BigDecimal dayRate = salary == null ? BigDecimal.ZERO : SettlementMath.perDay(s.getEncashBasis(), basic, gross);
        if (days.signum() > 0 && dayRate.signum() > 0) {
            earnings.add(new Line("Leave encashment", SettlementMath.money(dayRate.multiply(days)),
                    fmt(days) + " days × " + amount(dayRate) + " (" + basisLabel(s.getEncashBasis()) + ")"
                            + (s.getEncashDays() != null ? ", days set by HR" : "")));
        }

        // 3. Gratuity.
        SettlementMath.Service service = SettlementMath.service(e.getStartDate(), last);
        if (s.isIncludeGratuity() && service.gratuityDue()) {
            BigDecimal g = SettlementMath.gratuity(service, basic);
            if (g.signum() > 0) {
                earnings.add(new Line("Gratuity", g, "15/26 × basic " + amount(basic) + " × " + service.yearsCounted() + " years"
                        + (g.compareTo(SettlementMath.GRATUITY_CAP) == 0 ? " (capped at the statutory limit)" : "")));
            }
        } else if (!service.gratuityDue()) {
            notes.add("Gratuity is not due: " + service.describe() + " of service is under five years (four years and 240 days).");
        }

        // 4. Expense claims approved but not yet reimbursed.
        BigDecimal claims = BigDecimal.ZERO;
        int claimCount = 0;
        for (ExpenseClaim c : expenses.findByEmployeeIdOrderByCreatedAtDesc(e.getId())) {
            if (c.getStatus() == ExpenseStatus.APPROVED && c.getAmount() != null) {
                claims = claims.add(c.getAmount());
                claimCount++;
            }
        }
        if (claims.signum() > 0) {
            earnings.add(new Line("Expense claims to reimburse", SettlementMath.money(claims),
                    claimCount + (claimCount == 1 ? " approved claim" : " approved claims")));
        }

        // 5. Notice shortfall.
        Integer required = s.getNoticeDays() != null ? s.getNoticeDays()
                : letterheads.findById(companyId).map(l -> SettlementMath.noticeDays(l.getNoticePeriod())).orElse(null);
        Integer served = e.getExitStartedAt() == null ? null
                : (int) ChronoUnit.DAYS.between(e.getExitStartedAt().atZone(IST).toLocalDate(), last) + 1;
        int shortfall = required == null || served == null ? 0 : Math.max(0, required - served);
        if (shortfall > 0) {
            BigDecimal perDayGross = SettlementMath.perDay("GROSS_30", basic, gross);
            if (s.isNoticeWaived()) {
                notes.add("Notice shortfall of " + shortfall + " days waived.");
            } else if (perDayGross.signum() > 0) {
                deductions.add(new Line("Notice period shortfall", SettlementMath.money(perDayGross.multiply(BigDecimal.valueOf(shortfall))),
                        shortfall + " days × " + amount(perDayGross) + " (gross ÷ 30); " + served + " of " + required + " days served"));
            }
        }
        if (required == null) notes.add("No notice period is set on the letterpad; set it here to recover a shortfall.");

        // 6. HR's own lines.
        for (ExtraLine l : read(s.getExtraLines())) {
            (l.deduction() ? deductions : earnings).add(new Line(l.label(), SettlementMath.money(l.amount()), "Added by HR"));
        }

        BigDecimal totalEarnings = earnings.stream().map(Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDeductions = deductions.stream().map(Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal net = SettlementMath.money(totalEarnings.subtract(totalDeductions));
        if (net.signum() < 0) notes.add("Recoveries are more than what is due: the employee owes " + amount(net.negate()) + ".");

        return new Statement(name, e.getEmployeeNo(), e.getJobTitle(), dept,
                e.getStartDate() == null ? null : LONG.format(e.getStartDate()), LONG.format(last), currency,
                service.describe(), service.gratuityDue(), gross, basic, dayRate, required, served, shortfall,
                leaveLines, earnings, deductions, SettlementMath.money(totalEarnings), SettlementMath.money(totalDeductions),
                net, LONG.format(payBy(last)), notes);
    }

    /** The Code on Wages: within two working days of the last day. */
    static LocalDate payBy(LocalDate last) {
        LocalDate d = last;
        int working = 0;
        while (working < 2) {
            d = d.plusDays(1);
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) working++;
        }
        return d;
    }

    // ---- helpers ----

    private View view(Settlement s, Employee e) {
        Statement st = s.getStatus() == Settlement.Status.DRAFT || s.getStatementJson() == null
                ? compute(s, e) : readStatement(s.getStatementJson());
        return new View(s.getId().toString(), e.getId().toString(), s.getStatus().name(), s.getEncashTypes(),
                s.getEncashDays(), s.getEncashBasis(), s.getNoticeDays(), s.isNoticeWaived(), s.isIncludeGratuity(),
                read(s.getExtraLines()), s.getNote(), st,
                s.getApprovedAt() == null ? null : s.getApprovedAt().toString(),
                s.getDocumentId() == null ? null : s.getDocumentId().toString(),
                s.getPaidOn() == null ? null : s.getPaidOn().toString(), s.getPaidReference());
    }

    /** Someone with a last working day: on notice, or gone. */
    private Employee leaving(UUID employeeId) {
        Employee e = employees.findByIdAndCompanyId(employeeId, TenantContext.getCompanyId())
                .orElseThrow(() -> new NotFoundException("Employee not found"));
        if (e.getEndDate() == null) {
            throw new ApiException(ErrorCode.CONFLICT, "Approve their exit and set a last working day first.");
        }
        return e;
    }

    private Settlement draft(UUID employeeId, Employee e) {
        Settlement s = settlements.findByEmployeeIdAndCompanyId(employeeId, e.getCompanyId())
                .orElseGet(() -> settlements.save(new Settlement(e.getCompanyId(), employeeId)));
        if (s.getStatus() != Settlement.Status.DRAFT) {
            throw new ApiException(ErrorCode.CONFLICT, "This settlement is approved. Reopen it to make changes.");
        }
        return s;
    }

    private static BigDecimal sumOf(List<Line> lines, String startsWith) {
        return lines.stream().filter(l -> l.label().startsWith(startsWith)).map(Line::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String amount(BigDecimal v) {
        NumberFormat f = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN"));
        f.setMinimumFractionDigits(2);
        f.setMaximumFractionDigits(2);
        return f.format(v);
    }

    private static String fmt(Number n) {
        BigDecimal b = new BigDecimal(n.toString()).stripTrailingZeros();
        return b.scale() < 0 ? b.setScale(0).toPlainString() : b.toPlainString();
    }

    private static String basisLabel(String basis) {
        return switch (basis) {
            case "BASIC_30" -> "basic ÷ 30";
            case "GROSS_30" -> "gross ÷ 30";
            default -> "basic ÷ 26";
        };
    }

    private String write(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private List<ExtraLine> read(String s) {
        if (s == null || s.isBlank()) return List.of();
        try {
            return json.readValue(s, new TypeReference<List<ExtraLine>>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private Statement readStatement(String s) {
        try {
            return json.readValue(s, Statement.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Stored settlement could not be read", ex);
        }
    }

    private static ApiException invalid(String m) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, m);
    }
}
