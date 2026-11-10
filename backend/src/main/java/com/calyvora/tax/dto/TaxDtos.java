package com.calyvora.tax.dto;

import com.calyvora.tax.AgeBand;
import com.calyvora.tax.TaxDeduction;
import com.calyvora.tax.TaxRegime;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * The shapes the tax screens exchange.
 *
 * <p>Kept in one file because they are one conversation: a declaration goes out, comes back amended,
 * the proofs follow it, and the computation explains itself in terms of the same lines.
 */
public final class TaxDtos {

    private TaxDtos() {
    }

    // ---- the catalogue ----------------------------------------------------------------------

    /** One line the declaration form can show. */
    public record CatalogEntry(String key, String section, String oldSection, String sectionLabel, String label,
                               String hint, String group, boolean income, boolean allowedInNewRegime) {
        public static CatalogEntry of(TaxDeduction d) {
            return new CatalogEntry(d.name(), d.section(), d.oldSection(), d.sectionLabel(), d.label(), d.hint(),
                    d.group().name(), d.isIncome(), d.allowedIn(TaxRegime.NEW));
        }
    }

    // ---- the declaration --------------------------------------------------------------------

    public record ProofView(String id, String fileName, String contentType, int sizeBytes, String uploadedAt) {
    }

    public record ItemView(String key, BigDecimal amount, String detail, String proofStatus,
                           BigDecimal acceptedAmount, String reviewNote, List<ProofView> proofs) {
    }

    public record RentView(String id, String fromMonth, String toMonth, BigDecimal monthlyRent, String city,
                           boolean metro, String landlordName, String landlordPan, String landlordAddress,
                           String landlordRelationship, String proofStatus, BigDecimal acceptedRent,
                           String reviewNote, List<ProofView> proofs) {
    }

    public record HouseView(String id, boolean letOut, String address, String lenderName, String lenderPan,
                            String lenderAddress, String lenderType, BigDecimal interest, BigDecimal annualRent, BigDecimal municipalTax,
                            String proofStatus, BigDecimal acceptedInterest, String reviewNote,
                            List<ProofView> proofs) {
    }

    public record PreviousView(String employerName, String tan, BigDecimal income, BigDecimal tds,
                               BigDecimal pf, BigDecimal pt, String status, String reviewNote,
                               List<ProofView> proofs) {
    }

    /** What is on file for a year, and everything the form needs to draw itself. */
    public record DeclarationResponse(
            String employeeId,
            String employeeName,
            /** Form 124 item 1, as the employee last certified it. */
            String employeeAddress,
            String employeePan,
            /** For "son / daughter of" in the verification. */
            String parentName,
            String designation,
            String financialYear,
            TaxRegime regime,
            String status,
            String submittedAt,
            boolean windowOpen,
            boolean proofsOpen,
            String proofDeadline,
            /** True once the proof deadline has passed: only accepted amounts reduce the tax now. */
            boolean proofsDue,
            boolean parentsSenior,
            AgeBand ageBand,
            boolean dateOfBirthKnown,
            /** Whether the salary has an HRA / LTA line — without one there is nothing to exempt. */
            boolean salaryHasHra,
            boolean salaryHasLta,
            /** Section key to the amount claimed, uncapped (kept for older screens). */
            Map<String, BigDecimal> declared,
            List<ItemView> items,
            List<RentView> rent,
            List<HouseView> houses,
            PreviousView previous,
            List<CatalogEntry> catalog) {
    }

    public record ItemPayload(String key, BigDecimal amount, String detail) {
    }

    public record RentPayload(String id, String fromMonth, String toMonth, BigDecimal monthlyRent, String city,
                              String landlordName, String landlordPan, String landlordAddress,
                              String landlordRelationship) {
    }

    public record HousePayload(String id, boolean letOut, String address, String lenderName, String lenderPan,
                               BigDecimal interest, BigDecimal annualRent, BigDecimal municipalTax,
                               String lenderAddress, String lenderType) {
    }

    public record PreviousPayload(String employerName, String tan, BigDecimal income, BigDecimal tds,
                                  BigDecimal pf, BigDecimal pt) {
    }

    /**
     * What is being saved. Every part is optional and a null part is left as it is, so the form can
     * save one step at a time. {@code declared} is the older flat shape and replaces all the lines.
     */
    public record DeclarationPayload(TaxRegime regime, Boolean parentsSenior, Map<String, BigDecimal> declared,
                                     List<ItemPayload> items, List<RentPayload> rent, List<HousePayload> houses,
                                     PreviousPayload previous, String employeeAddress) {
        public DeclarationPayload(TaxRegime regime, Map<String, BigDecimal> declared) {
            this(regime, null, declared, null, null, null, null, null);
        }
    }

    // ---- the computation --------------------------------------------------------------------

    public record BandRow(BigDecimal from, BigDecimal to, BigDecimal ratePercent, BigDecimal taxable,
                          BigDecimal tax) {
    }

    /** One line as claimed and as allowed. */
    /**
     * One line of the working. {@code declared} is the amount the calculation used — what was claimed
     * until the proof deadline, what HR accepted after it. {@code claimed} and {@code approved} are the
     * two figures behind that, for the lines an employee types (null for lines Orbit works out).
     * {@code limit} and {@code usedBefore} explain a capped line: the ceiling, and how much of it the
     * lines above had already used.
     */
    public record DeductionRow(String key, String section, String label, BigDecimal declared, BigDecimal allowed,
                               BigDecimal claimed, BigDecimal approved, String proofStatus,
                               BigDecimal limit, BigDecimal usedBefore, BigDecimal movedToHouse) {
    }

    /** One earning in one month. */
    public record HeadRow(String name, BigDecimal amount) {
    }

    /** A shared ceiling and how full it is. */
    public record GroupRow(String group, BigDecimal claimed, BigDecimal cap, BigDecimal allowed) {
    }

    /** One month of the year: where its figure comes from, the salary and the tax. */
    public record MonthRow(String month, String source, BigDecimal gross, BigDecimal tds, BigDecimal pt,
                           List<HeadRow> heads) {
    }

    public record HraMonthRow(String month, BigDecimal hraReceived, BigDecimal rentLessTenPercent,
                              BigDecimal percentOfBasic, BigDecimal exempt) {
    }

    public record ComputationResponse(
            String financialYear,
            TaxRegime regime,
            AgeBand ageBand,
            String currency,
            BigDecimal grossSalary,
            BigDecimal previousEmployerIncome,
            List<DeductionRow> exemptions,
            BigDecimal standardDeduction,
            BigDecimal professionalTax,
            BigDecimal salaryIncome,
            BigDecimal houseProperty,
            BigDecimal otherIncome,
            BigDecimal grossTotalIncome,
            List<DeductionRow> deductions,
            List<GroupRow> groups,
            BigDecimal totalDeductions,
            BigDecimal taxableIncome,
            List<BandRow> bands,
            BigDecimal taxOnIncome,
            BigDecimal rebate,
            BigDecimal surcharge,
            BigDecimal cess,
            BigDecimal totalTax,
            BigDecimal monthlyTds,
            int monthsElapsed,
            BigDecimal deductedSoFar,
            BigDecimal remainingTax,
            /** What the next pay run should withhold, spreading what is left over the months left. */
            BigDecimal projectedNextMonth,
            RegimeComparison comparison,
            /** False while the company does not withhold income tax through Orbit: an estimate. */
            boolean withheldByPayroll,
            boolean proofsDue,
            List<MonthRow> months,
            List<HraMonthRow> hraMonths,
            /** Salary and tax from before this payroll — a previous employer, or an opening balance. */
            BigDecimal priorIncome,
            BigDecimal priorTds,
            /** Home-loan interest declared under 130 / 131 that counts under Section 22 instead. */
            BigDecimal interestMovedToHouse) {
    }

    /** The same income under both sets of rules, and which one wins. */
    public record RegimeComparison(BigDecimal oldRegimeTax, BigDecimal newRegimeTax,
                                   TaxRegime cheaper, BigDecimal saving) {
    }

    // ---- HR ---------------------------------------------------------------------------------

    /** One row of HR's list. */
    public record DeclarationSummaryRow(String employeeId, String employeeName, TaxRegime regime,
                                        String status, BigDecimal totalDeclared, BigDecimal annualTax,
                                        int proofs, int awaitingReview) {
    }

    /**
     * HR's decision on one thing. {@code type} is ITEM (with the line's key in {@code id}), RENT or
     * HOUSE (with its id) or PREVIOUS. A null accepted amount with ACCEPTED means "as declared".
     */
    public record ReviewPayload(String type, String id, String status, BigDecimal acceptedAmount, String note) {
    }

    /**
     * The windows, and who signs the forms. The signer fields are left as they are when null and
     * cleared when blank.
     */
    public record TaxSettings(boolean declarationsOpen, boolean proofsOpen, String proofDeadline,
                              String signerName, String signerParent, String signerDesignation,
                              String signerPlace, String citTdsAddress) {
        public TaxSettings(boolean declarationsOpen, boolean proofsOpen, String proofDeadline) {
            this(declarationsOpen, proofsOpen, proofDeadline, null, null, null, null, null);
        }
    }

    // ---- TDS deposits (challans and 24Q receipts) --------------------------------------------

    /** One salary month: the tax payroll deducted and the challan it was paid on, if recorded. */
    public record DepositMonth(String month, boolean finalised, BigDecimal tdsDeducted, String bsrCode,
                               String depositDate, String challanSerial, BigDecimal amount) {
    }

    public record DepositQuarter(String quarter, String label, String receiptNo, List<DepositMonth> months) {
    }

    public record DepositsResponse(String financialYear, List<DepositQuarter> quarters) {
    }

    /** A challan for a month. A null amount means "what payroll deducted that month". */
    public record ChallanPayload(String bsrCode, String depositDate, String challanSerial, BigDecimal amount) {
    }

    public record ReceiptPayload(String receiptNo) {
    }

    // ---- Form 130 -----------------------------------------------------------------------------

    /** Part A's summary: one quarter's salary, tax deducted and tax deposited, for one employee. */
    public record QuarterRow(String quarter, String label, String receiptNo, BigDecimal amountPaid,
                             BigDecimal tds, BigDecimal deposited) {
    }

    /** Part A, section II: one deposit of this employee's tax and the challan it went on. */
    public record ChallanRow(String month, BigDecimal tds, String bsrCode, String depositDate, String challanSerial) {
    }

    /** Who signs the verification. */
    public record Signer(String name, String parent, String designation, String place) {
    }

    public record Form130Response(
            String financialYear,
            String employerName,
            String employerAddress,
            String employerPan,
            String employerTan,
            String citTdsAddress,
            String employeeName,
            String employeeAddress,
            String employeePan,
            String employeeNo,
            String designation,
            String periodFrom,
            String periodTo,
            Signer signer,
            List<QuarterRow> quarters,
            List<ChallanRow> challans,
            ComputationResponse computation) {
    }
}
