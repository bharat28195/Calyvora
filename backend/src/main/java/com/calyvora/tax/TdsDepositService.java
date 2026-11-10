package com.calyvora.tax;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.TenantContext;
import com.calyvora.payroll.PayrollMonthRepository;
import com.calyvora.payroll.PayslipSnapshot;
import com.calyvora.payroll.PayslipSnapshotRepository;
import com.calyvora.tax.dto.TaxDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Where each month's TDS went after payroll deducted it: the challan it was paid on, and the receipt
 * number each quarter's 24Q was filed under. Form 130 Part A prints both against every employee's
 * deductions — without them Part A can only say what was deducted, not what was deposited.
 *
 * <p>Orbit does not pay the government; HR records the challan from the bank's counterfoil. The
 * numbers are checked for shape only (a seven-digit BSR code, a five-digit serial), because the
 * real check — that the payment matches — is TRACES's, and it reports it on the official Part A.
 */
@Service
public class TdsDepositService {

    private static final Pattern BSR = Pattern.compile("[0-9]{7}");
    private static final Pattern SERIAL = Pattern.compile("[0-9]{5}");
    private static final Pattern RECEIPT = Pattern.compile("[A-Z0-9]{8,16}");
    private static final String[] QUARTER_LABELS = {"Apr–Jun", "Jul–Sep", "Oct–Dec", "Jan–Mar"};

    private final TdsChallanRepository challanRepository;
    private final TdsQuarterReturnRepository returnRepository;
    private final PayslipSnapshotRepository snapshotRepository;
    private final PayrollMonthRepository monthRepository;
    private final com.calyvora.access.PermissionService permissions;

    public TdsDepositService(TdsChallanRepository challanRepository, TdsQuarterReturnRepository returnRepository,
                             PayslipSnapshotRepository snapshotRepository, PayrollMonthRepository monthRepository,
                             com.calyvora.access.PermissionService permissions) {
        this.challanRepository = challanRepository;
        this.returnRepository = returnRepository;
        this.snapshotRepository = snapshotRepository;
        this.monthRepository = monthRepository;
        this.permissions = permissions;
    }

    /** The year's twelve months by quarter, with what was deducted and what is recorded as paid. */
    @Transactional(readOnly = true)
    public TaxDtos.DepositsResponse deposits(AuthPrincipal principal, String year) {
        requireHr(principal);
        UUID companyId = TenantContext.getCompanyId();
        FinancialYear fy = year == null || year.isBlank() ? FinancialYear.of(LocalDate.now()) : FinancialYear.parse(year);
        List<String> months = months(fy);

        Map<String, BigDecimal> deducted = new HashMap<>();
        for (PayslipSnapshot s : snapshotRepository.findByCompanyIdAndMonthBetween(companyId, months.get(0), months.get(11))) {
            if (s.getIncomeTax() != null) deducted.merge(s.getMonth(), s.getIncomeTax(), BigDecimal::add);
        }
        Map<String, TdsChallan> challans = challans(companyId, months);
        Map<String, String> receipts = receipts(companyId, fy);

        List<TaxDtos.DepositQuarter> quarters = new ArrayList<>();
        for (int q = 0; q < 4; q++) {
            List<TaxDtos.DepositMonth> rows = new ArrayList<>();
            for (String m : months.subList(q * 3, q * 3 + 3)) {
                TdsChallan c = challans.get(m);
                rows.add(new TaxDtos.DepositMonth(m, monthRepository.existsByCompanyIdAndMonth(companyId, m),
                        deducted.getOrDefault(m, BigDecimal.ZERO),
                        c == null ? null : c.getBsrCode(),
                        c == null ? null : c.getDepositDate().toString(),
                        c == null ? null : c.getChallanSerial(),
                        c == null ? null : c.getAmount()));
            }
            String quarter = quarterKey(fy, q);
            quarters.add(new TaxDtos.DepositQuarter(quarter, "Q" + (q + 1) + " (" + QUARTER_LABELS[q] + ")",
                    receipts.get(quarter), rows));
        }
        return new TaxDtos.DepositsResponse(fy.label(), quarters);
    }

    /** Record (or correct) the challan a month's tax was paid on. */
    @Transactional
    public TaxDtos.DepositsResponse saveChallan(AuthPrincipal principal, String month, TaxDtos.ChallanPayload p) {
        requireHr(principal);
        UUID companyId = TenantContext.getCompanyId();
        YearMonth ym;
        try {
            ym = YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw invalid("The month must look like 2026-04.");
        }
        if (!monthRepository.existsByCompanyIdAndMonth(companyId, ym.toString())) {
            throw invalid("Finalise " + ym + " first — a challan pays the tax of a finalised month.");
        }
        String bsr = digits(p.bsrCode());
        if (!BSR.matcher(bsr).matches()) throw invalid("The BSR code is the bank branch's 7 digits.");
        String serial = digits(p.challanSerial());
        if (!SERIAL.matcher(serial).matches()) throw invalid("The challan serial number is 5 digits.");
        LocalDate date;
        try {
            date = LocalDate.parse(p.depositDate() == null ? "" : p.depositDate().trim());
        } catch (DateTimeParseException e) {
            throw invalid("The deposit date must be a date like 2026-05-07.");
        }
        if (date.isBefore(ym.atDay(1))) throw invalid("Tax cannot be paid before the month it was deducted in.");
        if (date.isAfter(LocalDate.now().plusDays(1))) throw invalid("The deposit date cannot be in the future.");

        BigDecimal amount = p.amount();
        if (amount == null) {
            amount = BigDecimal.ZERO;
            for (PayslipSnapshot s : snapshotRepository.findByCompanyIdAndMonthBetween(companyId, ym.toString(), ym.toString())) {
                if (s.getIncomeTax() != null) amount = amount.add(s.getIncomeTax());
            }
        } else if (amount.signum() < 0) {
            throw invalid("The amount cannot be negative.");
        }

        TdsChallan c = challanRepository.findByCompanyIdAndMonth(companyId, ym.toString())
                .orElseGet(() -> new TdsChallan(companyId, ym.toString()));
        c.setBsrCode(bsr);
        c.setChallanSerial(serial);
        c.setDepositDate(date);
        c.setAmount(amount);
        challanRepository.save(c);
        return deposits(principal, FinancialYear.of(ym.atDay(1)).label());
    }

    @Transactional
    public TaxDtos.DepositsResponse clearChallan(AuthPrincipal principal, String month) {
        requireHr(principal);
        UUID companyId = TenantContext.getCompanyId();
        challanRepository.findByCompanyIdAndMonth(companyId, month).ifPresent(challanRepository::delete);
        return deposits(principal, FinancialYear.of(YearMonth.parse(month).atDay(1)).label());
    }

    /** Record the receipt number a quarter's 24Q was acknowledged with; blank clears it. */
    @Transactional
    public TaxDtos.DepositsResponse saveReceipt(AuthPrincipal principal, String quarter, TaxDtos.ReceiptPayload p) {
        requireHr(principal);
        UUID companyId = TenantContext.getCompanyId();
        FinancialYear fy = quarterYear(quarter);
        String receipt = p.receiptNo() == null ? "" : p.receiptNo().replaceAll("\\s", "").toUpperCase();
        var existing = returnRepository.findByCompanyIdAndQuarter(companyId, quarter);
        if (receipt.isEmpty()) {
            existing.ifPresent(returnRepository::delete);
        } else {
            if (!RECEIPT.matcher(receipt).matches()) {
                throw invalid("The receipt number is the 8 to 16 letters and digits on the 24Q acknowledgement.");
            }
            TdsQuarterReturn r = existing.orElseGet(() -> new TdsQuarterReturn(companyId, quarter));
            r.setReceiptNo(receipt);
            returnRepository.save(r);
        }
        return deposits(principal, fy.label());
    }

    // ---- for Form 130 ---------------------------------------------------------------------------

    Map<String, TdsChallan> challans(UUID companyId, List<String> months) {
        Map<String, TdsChallan> out = new HashMap<>();
        for (TdsChallan c : challanRepository.findByCompanyIdAndMonthIn(companyId, months)) out.put(c.getMonth(), c);
        return out;
    }

    Map<String, String> receipts(UUID companyId, FinancialYear fy) {
        Map<String, String> out = new HashMap<>();
        for (TdsQuarterReturn r : returnRepository.findByCompanyIdAndQuarterStartingWith(companyId, fy.label() + "-")) {
            out.put(r.getQuarter(), r.getReceiptNo());
        }
        return out;
    }

    static List<String> months(FinancialYear fy) {
        List<String> out = new ArrayList<>();
        YearMonth m = YearMonth.from(fy.start());
        for (int i = 0; i < 12; i++) out.add(m.plusMonths(i).toString());
        return out;
    }

    static String quarterKey(FinancialYear fy, int index) {
        return fy.label() + "-Q" + (index + 1);
    }

    static String quarterLabel(int index) {
        return "Q" + (index + 1) + " (" + QUARTER_LABELS[index] + ")";
    }

    private static FinancialYear quarterYear(String quarter) {
        if (quarter == null || !quarter.matches("[0-9]{4}-[0-9]{2}-Q[1-4]")) {
            throw invalid("The quarter must look like 2026-27-Q1.");
        }
        return FinancialYear.parse(quarter.substring(0, 7));
    }

    private static String digits(String s) {
        return s == null ? "" : s.replaceAll("\\s", "");
    }

    private void requireHr(AuthPrincipal principal) {
        if (!permissions.has(principal, com.calyvora.access.Permission.TAX_MANAGE)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "You do not have permission to perform this action");
        }
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, message);
    }
}
