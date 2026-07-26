package com.calyvora.people;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.security.TenantContext;
import com.calyvora.people.dto.PayslipComponentPayload;
import com.calyvora.people.dto.PayslipComponentResponse;
import com.calyvora.people.dto.PayslipResponse.Line;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The company's payslip template — an ordered set of earning/deduction components that drives payslip
 * generation for every employee (founder: "add template for creating payslip"). Holds the validation
 * rules payroll always needs: percentages in range, earnings that sum to gross, and deductions that
 * can't exceed pay.
 */
@Service
public class PayslipTemplateService {

    private final PayslipComponentRepository repository;

    public PayslipTemplateService(PayslipComponentRepository repository) {
        this.repository = repository;
    }

    /** The result of applying the template to a monthly gross. */
    public record Computed(List<Line> earnings, List<Line> deductions,
                           BigDecimal gross, BigDecimal totalDeductions, BigDecimal net) {}

    @Transactional
    public List<PayslipComponentResponse> template() {
        UUID companyId = TenantContext.getCompanyId();
        if (!repository.existsByCompanyId(companyId)) {
            seedDefaults(companyId);
        }
        return repository.findByCompanyIdOrderBySortOrderAsc(companyId).stream()
                .map(PayslipComponentResponse::of).toList();
    }

    @Transactional
    public List<PayslipComponentResponse> save(List<PayslipComponentPayload> payloads) {
        UUID companyId = TenantContext.getCompanyId();
        List<PayslipComponent> validated = validateAndBuild(companyId, payloads);
        repository.deleteByCompanyId(companyId);
        repository.saveAll(validated);
        return validated.stream().map(PayslipComponentResponse::of).toList();
    }

    /** The standard template — a conventional CTC breakdown. Not persisted here. */
    private List<PayslipComponent> defaultComponents(UUID companyId) {
        return List.of(
                new PayslipComponent(UUID.randomUUID(), companyId, "Basic", PayComponentKind.EARNING,
                        PayComponentCalc.PERCENT_OF_GROSS, new BigDecimal("50"), true, 0),
                new PayslipComponent(UUID.randomUUID(), companyId, "House rent allowance", PayComponentKind.EARNING,
                        PayComponentCalc.PERCENT_OF_GROSS, new BigDecimal("25"), false, 1),
                new PayslipComponent(UUID.randomUUID(), companyId, "Special allowance", PayComponentKind.EARNING,
                        PayComponentCalc.REMAINDER, null, false, 2),
                new PayslipComponent(UUID.randomUUID(), companyId, "Provident fund", PayComponentKind.DEDUCTION,
                        PayComponentCalc.PERCENT_OF_BASIC, new BigDecimal("12"), false, 3),
                new PayslipComponent(UUID.randomUUID(), companyId, "Income tax", PayComponentKind.DEDUCTION,
                        PayComponentCalc.PERCENT_OF_GROSS, new BigDecimal("10"), false, 4));
    }

    /** Provision the standard template on first use. Reproduces a conventional CTC breakdown. */
    @Transactional
    public void seedDefaults(UUID companyId) {
        if (repository.existsByCompanyId(companyId)) return;
        repository.saveAll(defaultComponents(companyId));
    }

    /**
     * Apply the template to a monthly gross, producing the payslip lines. Falls back to the in-memory
     * default set when a company hasn't customized its template — crucially without writing, so it's
     * safe to call from a read-only payslip lookup.
     */
    @Transactional(readOnly = true)
    public Computed compute(UUID companyId, BigDecimal gross) {
        List<PayslipComponent> components = repository.findByCompanyIdOrderBySortOrderAsc(companyId);
        if (components.isEmpty()) {
            components = defaultComponents(companyId);
        }

        // Earnings — everything except the remainder first, so we know how much of gross is left.
        BigDecimal basisAmount = BigDecimal.ZERO;
        BigDecimal earnedSoFar = BigDecimal.ZERO;
        List<Line> earnings = new ArrayList<>();
        PayslipComponent remainder = null;
        for (PayslipComponent c : components) {
            if (c.getKind() != PayComponentKind.EARNING) continue;
            if (c.getCalc() == PayComponentCalc.REMAINDER) { remainder = c; earnings.add(null); continue; }
            BigDecimal amt = switch (c.getCalc()) {
                case PERCENT_OF_GROSS -> pct(gross, c.getValue());
                case FIXED -> scale(c.getValue());
                default -> BigDecimal.ZERO; // PERCENT_OF_BASIC not valid on an earning (rejected on save)
            };
            if (c.isBasis()) basisAmount = amt;
            earnedSoFar = earnedSoFar.add(amt);
            earnings.add(new Line(c.getName(), amt));
        }
        if (remainder != null) {
            BigDecimal left = gross.subtract(earnedSoFar);
            if (left.signum() < 0) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "Payslip earnings exceed gross pay — check the template");
            }
            // Slot the remainder back into its ordered position.
            for (int i = 0; i < earnings.size(); i++) {
                if (earnings.get(i) == null) { earnings.set(i, new Line(remainder.getName(), left)); break; }
            }
        }

        // Deductions.
        List<Line> deductions = new ArrayList<>();
        BigDecimal totalDed = BigDecimal.ZERO;
        for (PayslipComponent c : components) {
            if (c.getKind() != PayComponentKind.DEDUCTION) continue;
            BigDecimal amt = switch (c.getCalc()) {
                case PERCENT_OF_GROSS -> pct(gross, c.getValue());
                case PERCENT_OF_BASIC -> pct(basisAmount, c.getValue());
                case FIXED -> scale(c.getValue());
                case REMAINDER -> BigDecimal.ZERO; // not valid on a deduction (rejected on save)
            };
            deductions.add(new Line(c.getName(), amt));
            totalDed = totalDed.add(amt);
        }

        BigDecimal net = gross.subtract(totalDed);
        if (net.signum() < 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Payslip deductions exceed gross pay — check the template");
        }
        return new Computed(earnings, deductions, gross, totalDed, net);
    }

    // ---- validation ----

    private List<PayslipComponent> validateAndBuild(UUID companyId, List<PayslipComponentPayload> payloads) {
        if (payloads == null || payloads.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "A payslip template needs at least one component");
        }
        List<PayslipComponent> out = new ArrayList<>();
        int earnings = 0, remainders = 0, bases = 0;
        boolean usesBasis = false;
        BigDecimal earningPct = BigDecimal.ZERO, deductionPct = BigDecimal.ZERO;

        for (int i = 0; i < payloads.size(); i++) {
            PayslipComponentPayload p = payloads.get(i);
            PayComponentKind kind = parseKind(p.kind());
            PayComponentCalc calc = parseCalc(p.calc());
            BigDecimal value = p.value();

            if (calc == PayComponentCalc.REMAINDER) {
                if (kind != PayComponentKind.EARNING) throw invalid(p, "remainder is only valid on an earning");
                remainders++;
                value = null;
            } else if (calc == PayComponentCalc.FIXED) {
                if (value == null || value.signum() < 0) throw invalid(p, "a fixed amount must be zero or more");
            } else { // PERCENT_OF_GROSS / PERCENT_OF_BASIC
                if (value == null || value.signum() < 0 || value.compareTo(new BigDecimal("100")) > 0) {
                    throw invalid(p, "a percentage must be between 0 and 100");
                }
            }

            if (kind == PayComponentKind.EARNING) {
                earnings++;
                if (calc == PayComponentCalc.PERCENT_OF_BASIC) throw invalid(p, "an earning can't be a percent of basic");
                if (calc == PayComponentCalc.PERCENT_OF_GROSS) earningPct = earningPct.add(value);
                if (p.basis()) {
                    bases++;
                    if (calc == PayComponentCalc.REMAINDER) throw invalid(p, "the basis earning can't be the remainder");
                }
            } else { // DEDUCTION
                if (calc == PayComponentCalc.PERCENT_OF_BASIC) usesBasis = true;
                if (calc == PayComponentCalc.PERCENT_OF_GROSS) deductionPct = deductionPct.add(value);
            }

            out.add(new PayslipComponent(UUID.randomUUID(), companyId, p.name().trim(), kind, calc,
                    value == null ? null : scale(value), p.basis() && kind == PayComponentKind.EARNING, i));
        }

        if (earnings == 0) throw new ApiException(ErrorCode.VALIDATION_ERROR, "Add at least one earning");
        if (remainders > 1) throw new ApiException(ErrorCode.VALIDATION_ERROR, "Only one earning can be the remainder");
        if (bases > 1) throw new ApiException(ErrorCode.VALIDATION_ERROR, "Only one earning can be the basis for deductions");
        if (usesBasis && bases == 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "A percent-of-basic deduction needs one earning marked as the basis");
        }
        if (earningPct.compareTo(new BigDecimal("100")) > 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Percentage earnings add up to more than 100% of gross");
        }
        if (deductionPct.compareTo(new BigDecimal("100")) > 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Percentage deductions add up to more than 100% of gross");
        }
        return out;
    }

    private static ApiException invalid(PayslipComponentPayload p, String why) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, "\"" + p.name() + "\": " + why);
    }

    private static PayComponentKind parseKind(String s) {
        try { return PayComponentKind.valueOf(s.trim().toUpperCase()); }
        catch (RuntimeException e) { throw new ApiException(ErrorCode.VALIDATION_ERROR, "Invalid component kind: " + s); }
    }

    private static PayComponentCalc parseCalc(String s) {
        try { return PayComponentCalc.valueOf(s.trim().toUpperCase()); }
        catch (RuntimeException e) { throw new ApiException(ErrorCode.VALIDATION_ERROR, "Invalid calculation: " + s); }
    }

    private static BigDecimal pct(BigDecimal base, BigDecimal percent) {
        return base.multiply(percent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
