package com.calyvora.document;

import com.calyvora.feature.Feature;
import com.calyvora.feature.FeatureService;
import com.calyvora.payroll.EsiCalculator;
import com.calyvora.payroll.PfCalculator;
import com.calyvora.payroll.PfSettingsService;
import com.calyvora.payroll.StatutorySettings;
import com.calyvora.payroll.StatutorySettingsService;
import com.calyvora.people.EmployeeFinance;
import com.calyvora.people.PayComponentCalc;
import com.calyvora.people.PayComponentKind;
import com.calyvora.people.PayslipComponent;
import com.calyvora.people.PayslipTemplateService;
import com.calyvora.people.dto.PayslipResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The salary annexure an appointment, offer or increment letter carries: every earning month by
 * month and for the year, then either what the employer pays on top (giving the CTC) or what comes
 * off (giving take-home).
 *
 * <p>Built by the same template and the same PF / ESI calculators payroll uses, so the letter and the
 * first payslip cannot disagree. The yearly column is twelve months of each line, except the
 * template's balancing line, which takes whatever makes the year add up to the annual figure exactly —
 * a table whose total is a rupee off the CTC in the paragraph above it is the first thing anyone checks.
 *
 * <p>Rendered as a pipe table in the letter's own text format, so it can be edited by hand like any
 * other part of a letter.
 */
@Component
public class SalaryTables {

    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

    private final PayslipTemplateService templateService;
    private final PfSettingsService pfSettingsService;
    private final StatutorySettingsService statutorySettingsService;
    private final FeatureService featureService;

    public SalaryTables(PayslipTemplateService templateService, PfSettingsService pfSettingsService,
                        StatutorySettingsService statutorySettingsService, FeatureService featureService) {
        this.templateService = templateService;
        this.pfSettingsService = pfSettingsService;
        this.statutorySettingsService = statutorySettingsService;
        this.featureService = featureService;
    }

    /** One row: a label, a month of it and a year of it. */
    public record Row(String label, BigDecimal monthly, BigDecimal yearly) {
    }

    public record Structure(List<Row> earnings, List<Row> deductions, List<Row> employer,
                            BigDecimal monthlyGross, BigDecimal annualGross) {

        public BigDecimal monthlyEmployer() { return sum(employer, true); }
        public BigDecimal yearlyEmployer() { return sum(employer, false); }
        public BigDecimal monthlyDeductions() { return sum(deductions, true); }
        public BigDecimal yearlyDeductions() { return sum(deductions, false); }
        public BigDecimal annualCtc() { return annualGross.add(yearlyEmployer()); }

        private static BigDecimal sum(List<Row> rows, boolean monthly) {
            return rows.stream().map(r -> monthly ? r.monthly() : r.yearly()).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    /**
     * The structure for an annual gross. {@code finance} is the employee's statutory record, or null
     * for somebody not yet on payroll (an offer) — they are shown with PF, as a new joiner on a
     * statutory payroll will be.
     */
    public Structure structure(UUID companyId, BigDecimal annualGross, EmployeeFinance finance) {
        BigDecimal monthlyGross = annualGross.divide(TWELVE, 2, RoundingMode.HALF_UP);
        List<PayslipComponent> template = templateService.components(companyId);
        PayslipTemplateService.Computed c = templateService.compute(template, monthlyGross);
        String balancing = template.stream()
                .filter(p -> p.getKind() == PayComponentKind.EARNING && p.getCalc() == PayComponentCalc.REMAINDER)
                .map(PayslipComponent::getName).findFirst()
                .orElse(c.earnings().isEmpty() ? null : c.earnings().get(c.earnings().size() - 1).label());

        List<Row> earnings = new ArrayList<>();
        BigDecimal othersYearly = BigDecimal.ZERO;
        for (PayslipResponse.Line l : c.earnings()) {
            if (!l.label().equals(balancing)) othersYearly = othersYearly.add(l.amount().multiply(TWELVE));
        }
        for (PayslipResponse.Line l : c.earnings()) {
            BigDecimal yearly = l.label().equals(balancing)
                    ? annualGross.subtract(othersYearly)
                    : l.amount().multiply(TWELVE);
            earnings.add(new Row(l.label(), money(l.amount()), money(yearly)));
        }

        List<Row> deductions = new ArrayList<>();
        for (PayslipResponse.Line l : c.deductions()) {
            deductions.add(new Row(l.label(), money(l.amount()), money(l.amount().multiply(TWELVE))));
        }
        List<Row> employer = new ArrayList<>();

        if (featureService.isEnabled(companyId, Feature.STATUTORY_PAYROLL)) {
            boolean pfMember = finance == null || "ENABLED".equals(finance.getPfStatus());
            if (pfMember) {
                PfCalculator.Result pf = PfCalculator.compute(c.basic(), pfSettingsService.effective(companyId), true);
                if (pf.employee().signum() > 0) {
                    deductions.add(new Row("PF — employee", money(pf.employee()), money(pf.employee().multiply(TWELVE))));
                }
                BigDecimal employerPf = pf.employerEpf().add(pf.employerEps());
                if (employerPf.signum() > 0) {
                    employer.add(new Row("PF — employer", money(employerPf), money(employerPf.multiply(TWELVE))));
                }
            }
            StatutorySettings ss = statutorySettingsService.effective(companyId);
            boolean esiMember = finance == null ? true : "ELIGIBLE".equals(finance.getEsiStatus());
            if (ss.isEsiEnabled() && esiMember) {
                EsiCalculator.Result esi = EsiCalculator.compute(monthlyGross, monthlyGross, 30,
                        ss.getEsiEmployeeRate(), ss.getEsiEmployerRate(), ss.getEsiWageCeiling());
                if (esi.covered()) {
                    deductions.add(new Row("ESI — employee", money(esi.employee()), money(esi.employee().multiply(TWELVE))));
                    employer.add(new Row("ESI — employer", money(esi.employer()), money(esi.employer().multiply(TWELVE))));
                }
            }
        }
        return new Structure(List.copyOf(earnings), List.copyOf(deductions), List.copyOf(employer),
                money(monthlyGross), money(annualGross));
    }

    /** Earnings, then the employer's contributions on top: A + B = cost to company. */
    public static String ctcTable(Structure s, String currency) {
        String cur = currency == null ? "INR" : currency;
        StringBuilder t = new StringBuilder();
        t.append("| EARNINGS (PART A) | MONTHLY (").append(cur).append(") | YEARLY (").append(cur).append(") |\n");
        t.append("|---|---|---|\n");
        for (Row r : s.earnings()) row(t, r.label(), r.monthly(), r.yearly(), false);
        row(t, "TOTAL SALARY (A)", s.monthlyGross(), s.annualGross(), true);
        if (!s.employer().isEmpty()) {
            t.append("| **EMPLOYER CONTRIBUTIONS (PART B)** | **MONTHLY (").append(cur).append(")** | **YEARLY (")
                    .append(cur).append(")** |\n");
            for (Row r : s.employer()) row(t, r.label(), r.monthly(), r.yearly(), false);
            row(t, "SUB-TOTAL (B)", s.monthlyEmployer(), s.yearlyEmployer(), true);
            row(t, "COST TO COMPANY (A + B)", s.monthlyGross().add(s.monthlyEmployer()), s.annualCtc(), true);
        }
        return t.toString().stripTrailing();
    }

    /** Earnings, then what comes off them: A − B = take-home before income tax. */
    public static String takeHomeTable(Structure s, String currency) {
        String cur = currency == null ? "INR" : currency;
        StringBuilder t = new StringBuilder();
        t.append("| EARNINGS (PART A) | MONTHLY (").append(cur).append(") | YEARLY (").append(cur).append(") |\n");
        t.append("|---|---|---|\n");
        for (Row r : s.earnings()) row(t, r.label(), r.monthly(), r.yearly(), false);
        row(t, "TOTAL SALARY (A)", s.monthlyGross(), s.annualGross(), true);
        if (!s.deductions().isEmpty()) {
            t.append("| **DEDUCTIONS (PART B)** | **MONTHLY (").append(cur).append(")** | **YEARLY (")
                    .append(cur).append(")** |\n");
            for (Row r : s.deductions()) row(t, r.label(), r.monthly(), r.yearly(), false);
            row(t, "SUB-TOTAL (B)", s.monthlyDeductions(), s.yearlyDeductions(), true);
            row(t, "TAKE-HOME BEFORE INCOME TAX (A − B)", s.monthlyGross().subtract(s.monthlyDeductions()),
                    s.annualGross().subtract(s.yearlyDeductions()), true);
        }
        return t.toString().stripTrailing();
    }

    private static void row(StringBuilder t, String label, BigDecimal monthly, BigDecimal yearly, boolean bold) {
        String b = bold ? "**" : "";
        t.append("| ").append(b).append(label).append(b).append(" | ")
                .append(b).append(MergeFields.inr(monthly)).append(b).append(" | ")
                .append(b).append(MergeFields.inr(yearly)).append(b).append(" |\n");
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
