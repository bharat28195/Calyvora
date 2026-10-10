package com.calyvora.tax;

import com.calyvora.company.CompanySettings;
import com.calyvora.company.CompanySettingsRepository;
import com.calyvora.feature.Feature;
import com.calyvora.feature.FeatureService;
import com.calyvora.payroll.PayslipSnapshot;
import com.calyvora.payroll.PayslipSnapshotRepository;
import com.calyvora.payroll.PfCalculator;
import com.calyvora.payroll.PfSettings;
import com.calyvora.payroll.PfSettingsService;
import com.calyvora.payroll.ProfessionalTaxCalculator;
import com.calyvora.payroll.StatutorySettings;
import com.calyvora.payroll.StatutorySettingsService;
import com.calyvora.people.CompensationRecord;
import com.calyvora.people.CompensationRepository;
import com.calyvora.people.Employee;
import com.calyvora.people.EmployeeFinance;
import com.calyvora.people.EmployeeFinanceRepository;
import com.calyvora.people.EmployeeRepository;
import com.calyvora.people.PayslipComponent;
import com.calyvora.people.PayslipTemplateService;
import com.calyvora.people.SalaryCalendar;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One employee's tax year, assembled month by month — the single source the payslip, the
 * employee's screen and HR's screen all compute from, so they can never disagree.
 *
 * <p><b>Each month is one of four things.</b> Covered by an opening balance (paid and taxed before
 * Orbit); <em>locked</em> (a finalised payroll month: what was actually paid and withheld, never
 * recomputed); <em>projected</em> (the salary in force on that date, through the company's payslip
 * template); or nothing (before joining or after leaving). From each month comes the gross, the
 * basic, the HRA and LTA in it, the employee's PF and the professional tax — the same calculators
 * the payslip uses, so the PF that Section 123 counts is the PF that was deducted.
 *
 * <p><b>Why month by month.</b> The HRA exemption is the least of three amounts <em>for each
 * month</em>, and a raise in October or a move in December changes them. A yearly average gets both
 * wrong, and HRA is the largest exemption most salaried people have.
 *
 * <p><b>What payroll relies on.</b> Before the proof deadline, what the employee declared; after it,
 * only what HR accepted. Decided per month being paid ({@link #proofsDue}), never by today's date, so
 * a payslip recomputed for an open month gives the same answer whenever it is opened.
 */
@Component
public class TaxYear {

    private final CompensationRepository compensationRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeFinanceRepository financeRepository;
    private final PayslipSnapshotRepository snapshotRepository;
    private final TdsOpeningBalanceRepository openingRepository;
    private final TaxDeclarationRepository declarationRepository;
    private final TaxDeclarationItemRepository itemRepository;
    private final TaxRentPeriodRepository rentRepository;
    private final TaxHousePropertyRepository houseRepository;
    private final PayslipTemplateService templateService;
    private final PfSettingsService pfSettingsService;
    private final StatutorySettingsService statutorySettingsService;
    private final FeatureService featureService;
    private final CompanySettingsRepository settingsRepository;

    public TaxYear(CompensationRepository compensationRepository, EmployeeRepository employeeRepository,
                   EmployeeFinanceRepository financeRepository, PayslipSnapshotRepository snapshotRepository,
                   TdsOpeningBalanceRepository openingRepository, TaxDeclarationRepository declarationRepository,
                   TaxDeclarationItemRepository itemRepository, TaxRentPeriodRepository rentRepository,
                   TaxHousePropertyRepository houseRepository, PayslipTemplateService templateService,
                   PfSettingsService pfSettingsService, StatutorySettingsService statutorySettingsService,
                   FeatureService featureService, CompanySettingsRepository settingsRepository) {
        this.compensationRepository = compensationRepository;
        this.employeeRepository = employeeRepository;
        this.financeRepository = financeRepository;
        this.snapshotRepository = snapshotRepository;
        this.openingRepository = openingRepository;
        this.declarationRepository = declarationRepository;
        this.itemRepository = itemRepository;
        this.rentRepository = rentRepository;
        this.houseRepository = houseRepository;
        this.templateService = templateService;
        this.pfSettingsService = pfSettingsService;
        this.statutorySettingsService = statutorySettingsService;
        this.featureService = featureService;
        this.settingsRepository = settingsRepository;
    }

    // ---- shapes ---------------------------------------------------------------------------------

    /** Where a month's figure comes from. */
    public enum Source { OPENING, LOCKED, PROJECTED, NONE }

    /** One month of salary and what came off it. */
    public record MonthFact(YearMonth month, Source source, BigDecimal gross, BigDecimal basic, BigDecimal hra,
                            BigDecimal lta, BigDecimal pf, BigDecimal pt, BigDecimal tds) {
    }

    /** A run of months at one rent. */
    public record RentLine(YearMonth from, YearMonth to, BigDecimal monthlyRent, boolean metro) {
        boolean covers(YearMonth m) {
            return !m.isBefore(from) && !m.isAfter(to);
        }
    }

    /**
     * What the employee has declared, already reduced to what counts: the declared amounts before the
     * proof deadline, the accepted ones after.
     */
    public record Content(TaxRegime regime, boolean parentsSenior, Map<TaxDeduction, BigDecimal> declared,
                          List<RentLine> rent, List<IncomeTaxCalculator.HouseProperty> houses,
                          BigDecimal previousIncome, BigDecimal previousTds, BigDecimal previousPf,
                          BigDecimal previousPt) {

        public static Content nothing() {
            return new Content(TaxRegime.DEFAULT, false, Map.of(), List.of(), List.of(),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        public Content withRegime(TaxRegime other) {
            return new Content(other, parentsSenior, declared, rent, houses, previousIncome, previousTds,
                    previousPf, previousPt);
        }
    }

    /**
     * One employee's year as of a month being paid.
     *
     * @param withheld     tax already withheld before this month: the opening balance's, the previous
     *                     employer's, and every locked month's
     * @param spreadMonths the months what is left is spread over — this one, the rest of the year, and
     *                     earlier months neither locked nor covered by an opening balance
     */
    public record Facts(Employee employee, YearMonth asOf, FinancialYear year, List<MonthFact> months,
                        Content content, boolean proofsDue, AgeBand age, IncomeTaxCalculator.Input input,
                        HraCalculator.Result hra, BigDecimal withheld, int spreadMonths, int openPastMonths) {

        /** (year's tax − already withheld) ÷ the months it is spread over, in whole rupees. */
        public BigDecimal thisMonth(BigDecimal yearTax) {
            if (spreadMonths <= 0) {
                return BigDecimal.ZERO;
            }
            return yearTax.subtract(withheld).max(BigDecimal.ZERO)
                    .divide(BigDecimal.valueOf(spreadMonths), 0, RoundingMode.HALF_UP);
        }

        public BigDecimal salaryHere() {
            return months.stream().map(MonthFact::gross).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    // ---- the year -------------------------------------------------------------------------------

    /** Whether, in a month being paid, only HR-accepted amounts count (the proof deadline has passed). */
    public boolean proofsDue(UUID companyId, YearMonth month) {
        CompanySettings s = settingsRepository.findById(companyId).orElse(null);
        return proofsDue(s, month);
    }

    static boolean proofsDue(CompanySettings s, YearMonth month) {
        LocalDate deadline = s == null ? null : s.getTaxProofDeadline();
        if (deadline == null) {
            return false;
        }
        FinancialYear fy = FinancialYear.of(month.atDay(1));
        return fy.contains(deadline) && month.isAfter(YearMonth.from(deadline));
    }

    /** Everybody's year, from what is on file. Employees with no salary in the year are left out. */
    public Map<UUID, Facts> facts(UUID companyId, YearMonth asOf, Collection<UUID> employeeIds) {
        return facts(companyId, asOf, employeeIds, null);
    }

    /**
     * As above; {@code override} replaces one employee's declaration with unsaved content — the live
     * preview while somebody is still typing.
     */
    public Map<UUID, Facts> facts(UUID companyId, YearMonth asOf, Collection<UUID> employeeIds,
                                  Map<UUID, Content> override) {
        if (employeeIds.isEmpty()) {
            return Map.of();
        }
        FinancialYear fy = FinancialYear.of(asOf.atDay(1));
        YearMonth fyStart = YearMonth.from(fy.start());
        CompanySettings settings = settingsRepository.findById(companyId).orElse(null);
        boolean proofsDue = proofsDue(settings, asOf);

        // Company-wide facts, once.
        Map<UUID, List<CompensationRecord>> history = new HashMap<>();
        for (CompensationRecord r : compensationRepository.findByCompanyIdOrderByEffectiveDateDescCreatedAtDesc(companyId)) {
            if (employeeIds.contains(r.getEmployeeId())) {
                history.computeIfAbsent(r.getEmployeeId(), k -> new ArrayList<>()).add(r);
            }
        }
        Map<UUID, Employee> employees = new HashMap<>();
        for (Employee e : employeeRepository.findByCompanyIdAndIdIn(companyId, employeeIds)) {
            employees.put(e.getId(), e);
        }
        Map<UUID, EmployeeFinance> finance = new HashMap<>();
        for (EmployeeFinance f : financeRepository.findByCompanyId(companyId)) {
            if (employeeIds.contains(f.getEmployeeId())) finance.put(f.getEmployeeId(), f);
        }
        Map<UUID, Map<String, PayslipSnapshot>> locked = new HashMap<>();
        if (asOf.isAfter(fyStart)) {
            for (PayslipSnapshot s : snapshotRepository.findByCompanyIdAndMonthBetween(
                    companyId, fyStart.toString(), asOf.minusMonths(1).toString())) {
                locked.computeIfAbsent(s.getEmployeeId(), k -> new HashMap<>()).put(s.getMonth(), s);
            }
        }
        Map<UUID, TdsOpeningBalance> openings = new HashMap<>();
        for (TdsOpeningBalance o : openingRepository.findByCompanyIdAndFinancialYear(companyId, fy.label())) {
            openings.put(o.getEmployeeId(), o);
        }
        Map<UUID, Content> declared = contents(companyId, fy, proofsDue);
        if (override != null) {
            declared.putAll(override);
        }

        List<PayslipComponent> template = templateService.components(companyId);
        boolean statutoryOn = featureService.isEnabled(companyId, Feature.STATUTORY_PAYROLL);
        StatutorySettings statutory = statutoryOn ? statutorySettingsService.effective(companyId) : null;
        PfSettings pfSettings = statutoryOn ? pfSettingsService.effective(companyId) : null;

        Map<UUID, Facts> out = new HashMap<>();
        for (UUID id : employeeIds) {
            List<CompensationRecord> h = history.get(id);
            Employee emp = employees.get(id);
            if (h == null || h.isEmpty() || emp == null) {
                continue;
            }
            EmployeeFinance fin = finance.get(id);
            TdsOpeningBalance opening = openings.get(id);
            YearMonth coveredThrough = opening == null ? null : YearMonth.parse(opening.getCoveredThrough());
            Content content = declared.getOrDefault(id, Content.nothing());
            Map<String, PayslipSnapshot> mine = locked.getOrDefault(id, Map.of());

            List<MonthFact> months = new ArrayList<>();
            BigDecimal withheld = BigDecimal.ZERO;
            int spread = 0, openPast = 0;
            for (int i = 0; i < 12; i++) {
                YearMonth m = fyStart.plusMonths(i);
                if (coveredThrough != null && !m.isAfter(coveredThrough)) {
                    months.add(new MonthFact(m, Source.OPENING, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null));
                    continue;
                }
                PayslipSnapshot snap = m.isBefore(asOf) ? mine.get(m.toString()) : null;
                if (snap != null) {
                    months.add(lockedMonth(m, snap, template));
                    withheld = withheld.add(nz(snap.getIncomeTax()));
                    continue;
                }
                BigDecimal g = SalaryCalendar.grossForMonth(h, m, emp.getStartDate(), emp.getEndDate());
                if (g == null || g.signum() <= 0) {
                    months.add(new MonthFact(m, Source.NONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null));
                    continue;
                }
                months.add(projectedMonth(m, g, template, fin, statutory, pfSettings));
                spread++;
                if (m.isBefore(asOf)) openPast++;
            }

            BigDecimal openingIncome = opening == null ? BigDecimal.ZERO : nz(opening.getIncome());
            BigDecimal openingTds = opening == null ? BigDecimal.ZERO : nz(opening.getTds());
            BigDecimal openingPf = opening == null ? BigDecimal.ZERO : nz(opening.getEmployeePf());
            BigDecimal openingPt = opening == null ? BigDecimal.ZERO : nz(opening.getProfessionalTax());
            withheld = withheld.add(openingTds).add(content.previousTds());

            BigDecimal salary = sum(months, MonthFact::gross).add(openingIncome).add(content.previousIncome());
            BigDecimal basic = sum(months, MonthFact::basic);
            BigDecimal pf = sum(months, MonthFact::pf).add(openingPf).add(content.previousPf());
            BigDecimal pt = sum(months, MonthFact::pt).add(openingPt).add(content.previousPt());
            BigDecimal hraReceived = sum(months, MonthFact::hra);
            BigDecimal ltaReceived = sum(months, MonthFact::lta);

            // The HRA exemption, month by month, over the months this employer paid HRA.
            List<HraCalculator.Month> hraMonths = new ArrayList<>();
            BigDecimal rentForYear = BigDecimal.ZERO;
            for (MonthFact f : months) {
                RentLine r = rentFor(content.rent(), f.month());
                BigDecimal rent = r == null ? BigDecimal.ZERO : r.monthlyRent();
                rentForYear = rentForYear.add(rent);
                if (f.hra().signum() > 0) {
                    hraMonths.add(new HraCalculator.Month(f.month().toString(), f.basic(), f.hra(), rent,
                            r != null && r.metro()));
                }
            }
            HraCalculator.Result hra = HraCalculator.compute(hraMonths);

            AgeBand age = AgeBand.of(fin == null ? null : fin.getDateOfBirth(), fy);
            IncomeTaxCalculator.Input input = new IncomeTaxCalculator.Input(salary, content.regime(), age,
                    basic, pf, pt, hra.exempt(), hraReceived, ltaReceived, rentForYear, content.parentsSenior(),
                    content.houses(), content.declared());
            out.put(id, new Facts(emp, asOf, fy, List.copyOf(months), content, proofsDue, age, input, hra,
                    withheld, spread, openPast));
        }
        return out;
    }

    /** A finalised month: what was actually paid and withheld, the allowances in proportion to it. */
    private MonthFact lockedMonth(YearMonth m, PayslipSnapshot snap, List<PayslipComponent> template) {
        BigDecimal contracted = nz(snap.getGross());
        BigDecimal earned = snap.getEarnedGross() == null ? contracted : snap.getEarnedGross();
        BigDecimal basic = BigDecimal.ZERO, hra = BigDecimal.ZERO, lta = BigDecimal.ZERO;
        if (contracted.signum() > 0) {
            PayslipTemplateService.Computed c = templateService.compute(template, contracted);
            BigDecimal share = earned.divide(contracted, 10, RoundingMode.HALF_UP);
            basic = c.basic().multiply(share).setScale(2, RoundingMode.HALF_UP);
            hra = c.hra().multiply(share).setScale(2, RoundingMode.HALF_UP);
            lta = c.lta().multiply(share).setScale(2, RoundingMode.HALF_UP);
        }
        return new MonthFact(m, Source.LOCKED, earned, basic, hra, lta, nz(snap.getEmployeePf()),
                nz(snap.getProfessionalTax()), nz(snap.getIncomeTax()));
    }

    /** A month still to be paid: the salary in force, through the template and the statutory rules. */
    private MonthFact projectedMonth(YearMonth m, BigDecimal gross, List<PayslipComponent> template,
                                     EmployeeFinance fin, StatutorySettings statutory, PfSettings pfSettings) {
        PayslipTemplateService.Computed c = templateService.compute(template, gross);
        BigDecimal pf = BigDecimal.ZERO;
        BigDecimal pt = BigDecimal.ZERO;
        if (statutory != null && fin != null) {
            if ("ENABLED".equals(fin.getPfStatus())) {
                boolean pensionMember = fin.getDateOfBirth() == null
                        || fin.getDateOfBirth().plusYears(58).isAfter(m.atDay(1));
                pf = PfCalculator.compute(c.basic(), pfSettings, pensionMember).employee();
            }
            if (statutory.isPtEnabled()) {
                pt = ProfessionalTaxCalculator.compute(fin.getPtState(), fin.getGender(), fin.getDateOfBirth(),
                        gross, gross, m).amount();
            }
        }
        return new MonthFact(m, Source.PROJECTED, gross, c.basic(), c.hra(), c.lta(), pf, pt, null);
    }

    /** Everybody's declaration for the year, reduced to what counts. */
    private Map<UUID, Content> contents(UUID companyId, FinancialYear fy, boolean proofsDue) {
        List<TaxDeclaration> declarations = declarationRepository.findByCompanyIdAndFinancialYear(companyId, fy.label());
        Map<UUID, Content> out = new HashMap<>();
        if (declarations.isEmpty()) {
            return out;
        }
        List<UUID> ids = declarations.stream().map(TaxDeclaration::getId).toList();
        Map<UUID, List<TaxDeclarationItem>> items = new HashMap<>();
        for (TaxDeclarationItem i : itemRepository.findByDeclarationIdIn(ids)) {
            items.computeIfAbsent(i.getDeclarationId(), k -> new ArrayList<>()).add(i);
        }
        Map<UUID, List<TaxRentPeriod>> rents = new HashMap<>();
        for (TaxRentPeriod r : rentRepository.findByDeclarationIdIn(ids)) {
            rents.computeIfAbsent(r.getDeclarationId(), k -> new ArrayList<>()).add(r);
        }
        Map<UUID, List<TaxHouseProperty>> houses = new HashMap<>();
        for (TaxHouseProperty p : houseRepository.findByDeclarationIdIn(ids)) {
            houses.computeIfAbsent(p.getDeclarationId(), k -> new ArrayList<>()).add(p);
        }
        for (TaxDeclaration d : declarations) {
            out.put(d.getEmployeeId(), content(d, items.getOrDefault(d.getId(), List.of()),
                    rents.getOrDefault(d.getId(), List.of()), houses.getOrDefault(d.getId(), List.of()), proofsDue));
        }
        return out;
    }

    /** One declaration reduced to what counts. Income always counts; reliefs wait on proof once due. */
    public static Content content(TaxDeclaration d, List<TaxDeclarationItem> items, List<TaxRentPeriod> rents,
                                  List<TaxHouseProperty> houses, boolean proofsDue) {
        Map<TaxDeduction, BigDecimal> declared = new EnumMap<>(TaxDeduction.class);
        for (TaxDeclarationItem i : items) {
            // Income is income whether or not anybody proves it; only reliefs wait on a proof.
            declared.put(i.getDeduction(), i.getDeduction().isIncome() ? i.getAmount() : i.effective(proofsDue));
        }
        List<RentLine> rent = new ArrayList<>();
        for (TaxRentPeriod r : rents) {
            rent.add(new RentLine(r.getFrom(), r.getTo(), r.effectiveRent(proofsDue), r.isMetro()));
        }
        List<IncomeTaxCalculator.HouseProperty> props = new ArrayList<>();
        for (TaxHouseProperty p : houses) {
            props.add(new IncomeTaxCalculator.HouseProperty(p.isLetOut(), p.getAnnualRent(), p.getMunicipalTax(),
                    p.effectiveInterest(proofsDue)));
        }
        boolean prev = d.hasPreviousEmployer();
        return new Content(d.getRegime(), d.isParentsSenior(), declared, rent, props,
                prev ? nz(d.getPrevIncome()) : BigDecimal.ZERO, prev ? nz(d.getPrevTds()) : BigDecimal.ZERO,
                prev ? nz(d.getPrevPf()) : BigDecimal.ZERO, prev ? nz(d.getPrevPt()) : BigDecimal.ZERO);
    }

    private static RentLine rentFor(List<RentLine> rent, YearMonth m) {
        for (RentLine r : rent) {
            if (r.covers(m)) return r;
        }
        return null;
    }

    private static BigDecimal sum(List<MonthFact> months, java.util.function.Function<MonthFact, BigDecimal> f) {
        return months.stream().map(f).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
