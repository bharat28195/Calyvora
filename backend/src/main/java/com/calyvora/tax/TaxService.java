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
import com.calyvora.people.EmployeeFinance;
import com.calyvora.people.EmployeeFinanceRepository;
import com.calyvora.people.EmployeeRepository;
import com.calyvora.people.OrgScope;
import com.calyvora.people.PayslipComponent;
import com.calyvora.people.PayslipTemplateService;
import com.calyvora.tax.dto.TaxDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Tax declarations, their proofs, and what they cost — the part of the module that knows about
 * people and money (PD-55, PD-60).
 *
 * <p>{@link IncomeTaxCalculator} holds the law and knows nothing else; {@link TaxYear} assembles one
 * person's year; this class is the declaration (Form 124), the proofs and HR's review of them, and
 * the answers the screens and the payslip need. The split is deliberate: the arithmetic is the part
 * that must be provable, and it is far easier to prove when it cannot reach a database.
 */
@Service
public class TaxService {

    /** A PAN: five letters, four digits, a letter. */
    private static final Pattern PAN = Pattern.compile("[A-Z]{5}[0-9]{4}[A-Z]");
    /** A TAN: four letters, five digits, a letter. */
    private static final Pattern TAN = Pattern.compile("[A-Z]{4}[0-9]{5}[A-Z]");
    /** Above this much rent a year, the landlord's PAN is required with the claim. */
    static final BigDecimal RENT_NEEDING_PAN = BigDecimal.valueOf(100000);
    static final long MAX_PROOF_BYTES = 5L * 1024 * 1024;
    private static final Set<String> PROOF_TYPES = Set.of("application/pdf", "image/png", "image/jpeg");

    private final TaxDeclarationRepository declarationRepository;
    private final TaxDeclarationItemRepository itemRepository;
    private final TaxRentPeriodRepository rentRepository;
    private final TaxHousePropertyRepository houseRepository;
    private final TaxProofRepository proofRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeFinanceRepository financeRepository;
    private final UserRepository userRepository;
    private final CompensationRepository compensationRepository;
    private final CompanySettingsRepository settingsRepository;
    private final OrgScope orgScope;
    private final com.calyvora.feature.FeatureService featureService;
    private final com.calyvora.access.PermissionService permissions;
    private final TaxYear taxYear;
    private final PayslipTemplateService templateService;
    private final com.calyvora.payroll.StatutorySettingsService statutorySettingsService;
    private final com.calyvora.company.CompanyRepository companyRepository;
    private final TdsDepositService depositService;

    public TaxService(TaxDeclarationRepository declarationRepository,
                      TaxDeclarationItemRepository itemRepository,
                      TaxRentPeriodRepository rentRepository,
                      TaxHousePropertyRepository houseRepository,
                      TaxProofRepository proofRepository,
                      EmployeeRepository employeeRepository,
                      EmployeeFinanceRepository financeRepository,
                      UserRepository userRepository,
                      CompensationRepository compensationRepository,
                      CompanySettingsRepository settingsRepository,
                      OrgScope orgScope,
                      com.calyvora.feature.FeatureService featureService,
                      com.calyvora.access.PermissionService permissions,
                      TaxYear taxYear,
                      PayslipTemplateService templateService,
                      com.calyvora.payroll.StatutorySettingsService statutorySettingsService,
                      com.calyvora.company.CompanyRepository companyRepository,
                      TdsDepositService depositService) {
        this.declarationRepository = declarationRepository;
        this.itemRepository = itemRepository;
        this.rentRepository = rentRepository;
        this.houseRepository = houseRepository;
        this.proofRepository = proofRepository;
        this.employeeRepository = employeeRepository;
        this.financeRepository = financeRepository;
        this.userRepository = userRepository;
        this.compensationRepository = compensationRepository;
        this.settingsRepository = settingsRepository;
        this.orgScope = orgScope;
        this.featureService = featureService;
        this.permissions = permissions;
        this.taxYear = taxYear;
        this.templateService = templateService;
        this.statutorySettingsService = statutorySettingsService;
        this.companyRepository = companyRepository;
        this.depositService = depositService;
    }

    // =============================================================================================
    // The employee's own declaration
    // =============================================================================================

    @Transactional(readOnly = true)
    public TaxDtos.DeclarationResponse myDeclaration(AuthPrincipal principal, String year) {
        return declarationView(TenantContext.getCompanyId(), self(principal), yearOrCurrent(year));
    }

    /**
     * Save what the employee declared.
     *
     * <p>The window is checked here rather than only on the screen: HR closes declarations before the
     * last payroll of the year so the figures cannot move under a run that has already been filed, and
     * a closed window that only hides a button is not closed. This is also what locks the regime —
     * it can be changed only while declarations are open.
     */
    @Transactional
    public TaxDtos.DeclarationResponse save(AuthPrincipal principal, String year, TaxDtos.DeclarationPayload payload) {
        UUID companyId = TenantContext.getCompanyId();
        Employee me = self(principal);
        FinancialYear fy = yearOrCurrent(year);
        if (!settings(companyId).isTaxDeclarationsOpen()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Tax declarations are closed for now. Ask HR to reopen them.");
        }
        TaxDeclaration declaration = declarationRepository
                .findByCompanyIdAndEmployeeIdAndFinancialYear(companyId, me.getId(), fy.label())
                .orElseGet(() -> declarationRepository.save(new TaxDeclaration(companyId, me.getId(), fy.label())));
        apply(companyId, fy, declaration, payload);
        declarationRepository.save(declaration);
        return declarationView(companyId, me, fy);
    }

    @Transactional
    public TaxDtos.DeclarationResponse submit(AuthPrincipal principal, String year) {
        UUID companyId = TenantContext.getCompanyId();
        Employee me = self(principal);
        FinancialYear fy = yearOrCurrent(year);
        TaxDeclaration declaration = declarationRepository
                .findByCompanyIdAndEmployeeIdAndFinancialYear(companyId, me.getId(), fy.label())
                .orElseThrow(() -> new NotFoundException("There is nothing to submit for " + fy.label() + " yet."));
        if (!settings(companyId).isTaxDeclarationsOpen()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Tax declarations are closed for now. Ask HR to reopen them.");
        }
        checkComplete(declaration);
        declaration.submit();
        declarationRepository.save(declaration);
        return declarationView(companyId, me, fy);
    }

    /**
     * What Form 124 requires before a claim can stand. Checked at submission rather than on every
     * save, so a half-filled form can be kept as a draft.
     */
    private void checkComplete(TaxDeclaration d) {
        List<TaxRentPeriod> rent = rentRepository.findByDeclarationIdOrderByFromMonthAsc(d.getId());
        BigDecimal yearRent = BigDecimal.ZERO;
        for (TaxRentPeriod r : rent) {
            yearRent = yearRent.add(r.getMonthlyRent().multiply(BigDecimal.valueOf(r.months())));
            if (blank(r.getLandlordName())) {
                throw invalid("Add the landlord's name for the rent from " + r.getFrom() + ".");
            }
        }
        if (yearRent.compareTo(RENT_NEEDING_PAN) > 0) {
            for (TaxRentPeriod r : rent) {
                if (blank(r.getLandlordPan())) {
                    throw invalid("Rent of more than ₹1,00,000 a year needs the landlord's PAN. Add it for the rent from "
                            + r.getFrom() + ".");
                }
            }
        }
        for (TaxHouseProperty h : houseRepository.findByDeclarationId(d.getId())) {
            if (h.getInterest().signum() > 0 && blank(h.getLenderName())) {
                throw invalid("Add the name of the bank or lender for the home loan.");
            }
        }
        if (d.getPrevIncome() != null && d.getPrevIncome().signum() > 0 && blank(d.getPrevEmployerName())) {
            throw invalid("Add the name of your previous employer.");
        }
    }

    /** Apply a payload: each part that is present replaces that part; absent parts are left alone. */
    private void apply(UUID companyId, FinancialYear fy, TaxDeclaration d, TaxDtos.DeclarationPayload p) {
        if (p == null) {
            return;
        }
        if (p.regime() != null) {
            d.setRegime(p.regime());
        }
        if (p.parentsSenior() != null) {
            d.setParentsSenior(p.parentsSenior());
        }
        if (p.employeeAddress() != null) {
            d.setEmployeeAddress(trimTo(p.employeeAddress(), 400));
        }
        if (p.items() != null || p.declared() != null) {
            List<TaxDtos.ItemPayload> items = new ArrayList<>();
            if (p.items() != null) {
                items.addAll(p.items());
            } else {
                p.declared().forEach((k, v) -> items.add(new TaxDtos.ItemPayload(k, v, null)));
            }
            replaceItems(companyId, d, items);
        }
        if (p.rent() != null) {
            replaceRent(companyId, fy, d, p.rent());
        }
        if (p.houses() != null) {
            replaceHouses(companyId, d, p.houses());
        }
        if (p.previous() != null) {
            applyPrevious(d, p.previous());
        }
    }

    /**
     * Lines, matched by key. A line whose amount changes after HR reviewed it goes back for review —
     * an accepted ₹50,000 does not quietly stand behind a claim that is now ₹1,50,000.
     */
    private void replaceItems(UUID companyId, TaxDeclaration d, List<TaxDtos.ItemPayload> wanted) {
        Map<TaxDeduction, TaxDtos.ItemPayload> byKey = new EnumMap<>(TaxDeduction.class);
        for (TaxDtos.ItemPayload i : wanted) {
            TaxDeduction key;
            try {
                key = TaxDeduction.valueOf(i.key());
            } catch (IllegalArgumentException | NullPointerException e) {
                // Named rather than ignored: a typo silently dropping a ₹1,50,000 claim is a tax bill the
                // employee does not expect and cannot explain.
                throw invalid("'" + i.key() + "' is not a line this form knows about.");
            }
            BigDecimal amount = i.amount() == null ? BigDecimal.ZERO : i.amount();
            if (amount.signum() < 0) {
                throw invalid(key.label() + " cannot be a negative amount.");
            }
            byKey.put(key, new TaxDtos.ItemPayload(i.key(), amount, i.detail()));
        }
        Map<TaxDeduction, TaxDeclarationItem> existing = new EnumMap<>(TaxDeduction.class);
        for (TaxDeclarationItem i : itemRepository.findByDeclarationId(d.getId())) {
            existing.put(i.getDeduction(), i);
        }
        for (Map.Entry<TaxDeduction, TaxDeclarationItem> e : existing.entrySet()) {
            TaxDtos.ItemPayload w = byKey.get(e.getKey());
            if (w == null || w.amount().signum() == 0) {
                deleteProofs(d.getId(), TaxProof.Owner.ITEM, e.getValue().getId());
                itemRepository.delete(e.getValue());
            }
        }
        itemRepository.flush();
        for (Map.Entry<TaxDeduction, TaxDtos.ItemPayload> e : byKey.entrySet()) {
            TaxDtos.ItemPayload w = e.getValue();
            if (w.amount().signum() == 0) continue;
            TaxDeclarationItem row = existing.get(e.getKey());
            if (row == null) {
                row = new TaxDeclarationItem(companyId, d.getId(), e.getKey(), w.amount());
            } else if (row.getAmount().compareTo(w.amount()) != 0) {
                row.setAmount(w.amount());
                TaxDeclarationItem changed = row;
                reopenReview(changed.getProofStatus(), d.getId(), TaxProof.Owner.ITEM, changed.getId(),
                        s -> changed.review(s, null, null, null));
            }
            row.setDetail(trimTo(w.detail(), 300));
            itemRepository.save(row);
        }
    }

    private void replaceRent(UUID companyId, FinancialYear fy, TaxDeclaration d, List<TaxDtos.RentPayload> wanted) {
        YearMonth first = YearMonth.from(fy.start());
        YearMonth last = YearMonth.from(fy.end());
        Map<UUID, TaxRentPeriod> existing = new HashMap<>();
        for (TaxRentPeriod r : rentRepository.findByDeclarationIdOrderByFromMonthAsc(d.getId())) {
            existing.put(r.getId(), r);
        }
        List<TaxRentPeriod> keep = new ArrayList<>();
        for (TaxDtos.RentPayload w : wanted) {
            YearMonth from = month(w.fromMonth(), "rent start");
            YearMonth to = month(w.toMonth(), "rent end");
            if (to.isBefore(from)) throw invalid("Rent can't end before it starts.");
            if (from.isBefore(first) || to.isAfter(last)) {
                throw invalid("Rent must fall within " + fy.label() + " (" + first + " to " + last + ").");
            }
            if (w.monthlyRent() == null || w.monthlyRent().signum() <= 0) throw invalid("Enter the monthly rent.");
            if (blank(w.city())) throw invalid("Enter the city the house is in.");
            for (TaxRentPeriod k : keep) {
                if (!from.isAfter(k.getTo()) && !to.isBefore(k.getFrom())) {
                    throw invalid("Two rent periods overlap (" + k.getFrom() + " to " + k.getTo() + "). One home at a time.");
                }
            }
            TaxRentPeriod row = w.id() == null ? null : existing.remove(UUID.fromString(w.id()));
            boolean changed = row == null || row.getMonthlyRent().compareTo(w.monthlyRent()) != 0
                    || !row.getFrom().equals(from) || !row.getTo().equals(to);
            if (row == null) row = new TaxRentPeriod(companyId, d.getId());
            row.setMonths(from, to);
            row.setMonthlyRent(w.monthlyRent());
            row.setCity(w.city().trim());
            row.setLandlordName(trimTo(w.landlordName(), 160));
            row.setLandlordPan(pan(w.landlordPan(), "landlord's PAN"));
            row.setLandlordAddress(trimTo(w.landlordAddress(), 300));
            row.setLandlordRelationship(trimTo(w.landlordRelationship(), 60));
            if (changed && row.getProofStatus() != ProofStatus.NONE) {
                TaxRentPeriod r = row;
                reopenReview(r.getProofStatus(), d.getId(), TaxProof.Owner.RENT, r.getId(), s -> r.review(s, null, null, null));
            }
            keep.add(row);
        }
        for (TaxRentPeriod gone : existing.values()) {
            deleteProofs(d.getId(), TaxProof.Owner.RENT, gone.getId());
            rentRepository.delete(gone);
        }
        rentRepository.saveAll(keep);
    }

    private void replaceHouses(UUID companyId, TaxDeclaration d, List<TaxDtos.HousePayload> wanted) {
        Map<UUID, TaxHouseProperty> existing = new HashMap<>();
        for (TaxHouseProperty h : houseRepository.findByDeclarationId(d.getId())) {
            existing.put(h.getId(), h);
        }
        List<TaxHouseProperty> keep = new ArrayList<>();
        for (TaxDtos.HousePayload w : wanted) {
            BigDecimal interest = nn(w.interest(), "Home loan interest");
            BigDecimal rent = nn(w.annualRent(), "Rent received");
            BigDecimal municipal = nn(w.municipalTax(), "Municipal tax");
            TaxHouseProperty row = w.id() == null ? null : existing.remove(UUID.fromString(w.id()));
            boolean changed = row == null || row.getInterest().compareTo(interest) != 0;
            if (row == null) row = new TaxHouseProperty(companyId, d.getId());
            row.setLetOut(w.letOut());
            row.setAddress(trimTo(w.address(), 300));
            row.setLenderName(trimTo(w.lenderName(), 160));
            row.setLenderPan(pan(w.lenderPan(), "lender's PAN"));
            row.setLenderAddress(trimTo(w.lenderAddress(), 300));
            row.setLenderType(lenderType(w.lenderType()));
            row.setInterest(interest);
            row.setAnnualRent(w.letOut() ? rent : BigDecimal.ZERO);
            row.setMunicipalTax(w.letOut() ? municipal : BigDecimal.ZERO);
            if (changed && row.getProofStatus() != ProofStatus.NONE) {
                TaxHouseProperty h = row;
                reopenReview(h.getProofStatus(), d.getId(), TaxProof.Owner.HOUSE, h.getId(), s -> h.review(s, null, null, null));
            }
            keep.add(row);
        }
        for (TaxHouseProperty gone : existing.values()) {
            deleteProofs(d.getId(), TaxProof.Owner.HOUSE, gone.getId());
            houseRepository.delete(gone);
        }
        houseRepository.saveAll(keep);
    }

    private void applyPrevious(TaxDeclaration d, TaxDtos.PreviousPayload p) {
        BigDecimal income = nn(p.income(), "Previous employer's salary");
        BigDecimal tds = nn(p.tds(), "Tax deducted by the previous employer");
        boolean changed = !eq(d.getPrevIncome(), income) || !eq(d.getPrevTds(), tds);
        d.setPrevEmployerName(trimTo(p.employerName(), 160));
        String tan = blank(p.tan()) ? null : p.tan().trim().toUpperCase(Locale.ROOT);
        if (tan != null && !TAN.matcher(tan).matches()) {
            throw invalid("The previous employer's TAN looks wrong — it is four letters, five digits and a letter.");
        }
        d.setPrevEmployerTan(tan);
        d.setPrevIncome(income);
        d.setPrevTds(tds);
        d.setPrevPf(nn(p.pf(), "Previous employer's PF"));
        d.setPrevPt(nn(p.pt(), "Previous employer's professional tax"));
        if (changed && !"NONE".equals(d.getPrevStatus())) {
            boolean hasProof = !proofRepository.idsFor(d.getId(), TaxProof.Owner.PREVIOUS.name(), d.getId()).isEmpty();
            d.setPrevStatus(hasProof ? "SUBMITTED" : "NONE");
            d.setPrevReviewNote(null);
        }
    }

    /** A reviewed thing whose figure changed goes back to waiting (or to nothing, without a proof). */
    private void reopenReview(ProofStatus current, UUID declarationId, TaxProof.Owner owner, UUID ownerId,
                              java.util.function.Consumer<ProofStatus> set) {
        if (current == ProofStatus.NONE) return;
        boolean hasProof = !proofRepository.idsFor(declarationId, owner.name(), ownerId).isEmpty();
        set.accept(hasProof ? ProofStatus.SUBMITTED : ProofStatus.NONE);
    }

    private void deleteProofs(UUID declarationId, TaxProof.Owner owner, UUID ownerId) {
        List<UUID> ids = proofRepository.idsFor(declarationId, owner.name(), ownerId);
        if (!ids.isEmpty()) proofRepository.deleteAllById(ids);
    }

    // =============================================================================================
    // Proofs
    // =============================================================================================

    /** Attach a proof to something on the employee's own declaration. Only while proofs are open. */
    @Transactional
    public TaxDtos.DeclarationResponse uploadProof(AuthPrincipal principal, String year, String ownerType,
                                                   String ownerId, MultipartFile file) {
        UUID companyId = TenantContext.getCompanyId();
        Employee me = self(principal);
        FinancialYear fy = yearOrCurrent(year);
        if (!settings(companyId).isTaxProofsOpen()) {
            throw invalid("Proof submission is closed. Ask HR to open it.");
        }
        TaxDeclaration d = declarationRepository
                .findByCompanyIdAndEmployeeIdAndFinancialYear(companyId, me.getId(), fy.label())
                .orElseThrow(() -> invalid("Save your declaration before adding proofs."));
        TaxProof.Owner owner;
        try {
            owner = TaxProof.Owner.valueOf(ownerType);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw invalid("Unknown proof type");
        }
        UUID target = markSubmitted(d, owner, ownerId);
        byte[] bytes = readProof(file);
        proofRepository.save(new TaxProof(companyId, d.getId(), owner, target,
                trimTo(file.getOriginalFilename() == null ? "proof" : file.getOriginalFilename(), 255),
                file.getContentType().toLowerCase(Locale.ROOT), bytes, principal.userId()));
        declarationRepository.save(d);
        return declarationView(companyId, me, fy);
    }

    /** Find what a proof is for, and set it to "waiting for HR" unless HR has already decided. */
    private UUID markSubmitted(TaxDeclaration d, TaxProof.Owner owner, String ownerId) {
        switch (owner) {
            case ITEM -> {
                TaxDeduction key;
                try {
                    key = TaxDeduction.valueOf(ownerId);
                } catch (IllegalArgumentException | NullPointerException e) {
                    throw invalid("Unknown line");
                }
                TaxDeclarationItem item = itemRepository.findByDeclarationId(d.getId()).stream()
                        .filter(i -> i.getDeduction() == key).findFirst()
                        .orElseThrow(() -> invalid("Declare an amount for this line before adding its proof."));
                if (key.isIncome()) throw invalid("Income needs no proof.");
                if (item.getProofStatus() == ProofStatus.NONE || item.getProofStatus() == ProofStatus.REJECTED) {
                    item.setProofStatus(ProofStatus.SUBMITTED);
                    itemRepository.save(item);
                }
                return item.getId();
            }
            case RENT -> {
                TaxRentPeriod r = rentRepository.findById(parseId(ownerId))
                        .filter(x -> x.getDeclarationId().equals(d.getId()))
                        .orElseThrow(() -> invalid("Unknown rent period"));
                if (r.getProofStatus() == ProofStatus.NONE || r.getProofStatus() == ProofStatus.REJECTED) {
                    r.setProofStatus(ProofStatus.SUBMITTED);
                    rentRepository.save(r);
                }
                return r.getId();
            }
            case HOUSE -> {
                TaxHouseProperty h = houseRepository.findById(parseId(ownerId))
                        .filter(x -> x.getDeclarationId().equals(d.getId()))
                        .orElseThrow(() -> invalid("Unknown house"));
                if (h.getProofStatus() == ProofStatus.NONE || h.getProofStatus() == ProofStatus.REJECTED) {
                    h.setProofStatus(ProofStatus.SUBMITTED);
                    houseRepository.save(h);
                }
                return h.getId();
            }
            default -> {
                if (!d.hasPreviousEmployer()) throw invalid("Add the previous employer's figures first.");
                if ("NONE".equals(d.getPrevStatus()) || "REJECTED".equals(d.getPrevStatus())) d.setPrevStatus("SUBMITTED");
                return d.getId();
            }
        }
    }

    private static byte[] readProof(MultipartFile file) {
        if (file == null || file.isEmpty()) throw invalid("Choose a file to upload.");
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!PROOF_TYPES.contains(type)) throw invalid("Upload a PDF, PNG or JPEG.");
        if (file.getSize() > MAX_PROOF_BYTES) throw invalid("A proof can be at most 5 MB.");
        try {
            return file.getBytes();
        } catch (java.io.IOException e) {
            throw invalid("The file could not be read.");
        }
    }

    /** A proof's bytes — for the employee whose it is, or for HR. */
    @Transactional(readOnly = true)
    public TaxProof proof(AuthPrincipal principal, UUID proofId) {
        TaxProof p = proofRepository.findById(proofId).orElseThrow(() -> new NotFoundException("Proof not found"));
        TaxDeclaration d = declarationRepository.findById(p.getDeclarationId())
                .orElseThrow(() -> new NotFoundException("Proof not found"));
        if (!isHr(principal) && !ownDeclaration(principal, d)) {
            throw new NotFoundException("Proof not found");
        }
        p.getContent();   // load the bytes inside the transaction
        return p;
    }

    /** Remove a proof the employee uploaded, while HR has not yet decided on it. */
    @Transactional
    public TaxDtos.DeclarationResponse deleteProof(AuthPrincipal principal, String year, UUID proofId) {
        UUID companyId = TenantContext.getCompanyId();
        Employee me = self(principal);
        FinancialYear fy = yearOrCurrent(year);
        TaxDeclaration d = declarationRepository
                .findByCompanyIdAndEmployeeIdAndFinancialYear(companyId, me.getId(), fy.label())
                .orElseThrow(() -> new NotFoundException("Proof not found"));
        TaxProof p = proofRepository.findByIdAndDeclarationId(proofId, d.getId())
                .orElseThrow(() -> new NotFoundException("Proof not found"));
        ProofStatus status = statusOf(d, p.getOwner(), p.getOwnerId());
        if (status == ProofStatus.ACCEPTED || status == ProofStatus.PARTIAL) {
            throw invalid("HR has already accepted this proof, so it stays on file.");
        }
        proofRepository.delete(p);
        proofRepository.flush();
        if (proofRepository.idsFor(d.getId(), p.getOwner().name(), p.getOwnerId()).isEmpty()) {
            setStatus(d, p.getOwner(), p.getOwnerId(), ProofStatus.NONE);
        }
        return declarationView(companyId, me, fy);
    }

    private ProofStatus statusOf(TaxDeclaration d, TaxProof.Owner owner, UUID ownerId) {
        return switch (owner) {
            case ITEM -> itemRepository.findById(ownerId).map(TaxDeclarationItem::getProofStatus).orElse(ProofStatus.NONE);
            case RENT -> rentRepository.findById(ownerId).map(TaxRentPeriod::getProofStatus).orElse(ProofStatus.NONE);
            case HOUSE -> houseRepository.findById(ownerId).map(TaxHouseProperty::getProofStatus).orElse(ProofStatus.NONE);
            case PREVIOUS -> "ACCEPTED".equals(d.getPrevStatus()) ? ProofStatus.ACCEPTED : ProofStatus.SUBMITTED;
        };
    }

    private void setStatus(TaxDeclaration d, TaxProof.Owner owner, UUID ownerId, ProofStatus s) {
        switch (owner) {
            case ITEM -> itemRepository.findById(ownerId).ifPresent(i -> { i.setProofStatus(s); itemRepository.save(i); });
            case RENT -> rentRepository.findById(ownerId).ifPresent(r -> { r.setProofStatus(s); rentRepository.save(r); });
            case HOUSE -> houseRepository.findById(ownerId).ifPresent(h -> { h.setProofStatus(s); houseRepository.save(h); });
            case PREVIOUS -> { d.setPrevStatus(s.name()); declarationRepository.save(d); }
        }
    }

    // =============================================================================================
    // What it costs
    // =============================================================================================

    @Transactional(readOnly = true)
    public TaxDtos.ComputationResponse myComputation(AuthPrincipal principal, String year) {
        return computationFor(TenantContext.getCompanyId(), self(principal), yearOrCurrent(year), null);
    }

    /**
     * The computation for a declaration that has not been saved — the live figures beside the form as
     * somebody types. The payload is read as declared (not yet proved), exactly as payroll will read it
     * until the proof deadline.
     */
    @Transactional(readOnly = true)
    public TaxDtos.ComputationResponse preview(AuthPrincipal principal, String year, TaxDtos.DeclarationPayload payload) {
        UUID companyId = TenantContext.getCompanyId();
        Employee me = self(principal);
        FinancialYear fy = yearOrCurrent(year);
        TaxYear.Content saved = savedContent(companyId, me.getId(), fy, false);
        return computationFor(companyId, me, fy, merge(saved, payload, fy));
    }

    /**
     * Monthly TDS for a set of employees, for a payroll run.
     *
     * <p>Depends on the month being paid, never on today's date, so a payslip recomputed for an open
     * month gives the same answer whenever it is opened — and a finalised month is never recomputed at
     * all; its payslip is read back as issued.
     */
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> monthlyTdsFor(UUID companyId, YearMonth month, java.util.Collection<UUID> employeeIds) {
        Map<UUID, BigDecimal> out = new HashMap<>();
        for (Map.Entry<UUID, TaxYear.Facts> e : taxYear.facts(companyId, month, employeeIds).entrySet()) {
            TaxYear.Facts f = e.getValue();
            out.put(e.getKey(), f.thisMonth(IncomeTaxCalculator.compute(f.input()).totalTax()));
        }
        return out;
    }

    /**
     * The whole calculation for one person, the other regime beside it, and the year month by month.
     * {@code override} is unsaved content for a preview; null reads what is on file.
     */
    private TaxDtos.ComputationResponse computationFor(UUID companyId, Employee employee, FinancialYear fy,
                                                       TaxYear.Content override) {
        YearMonth asOf = asOfFor(fy);
        Map<UUID, TaxYear.Content> overrides = override == null ? null : Map.of(employee.getId(), override);
        TaxYear.Facts facts = taxYear.facts(companyId, asOf, List.of(employee.getId()), overrides).get(employee.getId());
        CompensationRecord current = currentSalary(employee.getId());
        String currency = current == null || current.getCurrency() == null ? "INR" : current.getCurrency();
        boolean withheld = featureService.isEnabled(companyId, com.calyvora.feature.Feature.INCOME_TAX);

        if (facts == null) {
            // Nobody on payroll this year: an empty computation, not an error.
            IncomeTaxCalculator.Result empty = IncomeTaxCalculator.compute(IncomeTaxCalculator.Input.of(BigDecimal.ZERO,
                    override == null ? TaxRegime.DEFAULT : override.regime()));
            return response(fy, empty, empty, currency, BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO,
                    withheld, false, List.of(), List.of(), BigDecimal.ZERO, Map.of(), BigDecimal.ZERO, BigDecimal.ZERO);
        }

        IncomeTaxCalculator.Result result = IncomeTaxCalculator.compute(facts.input());
        TaxRegime other = facts.input().regime() == TaxRegime.NEW ? TaxRegime.OLD : TaxRegime.NEW;
        IncomeTaxCalculator.Result alternative = IncomeTaxCalculator.compute(facts.input().withRegime(other));

        BigDecimal perMonth = facts.thisMonth(result.totalTax());
        int elapsed = fy.monthsElapsed(LocalDate.now());
        BigDecimal deductedSoFar = !withheld ? BigDecimal.ZERO
                : facts.withheld().add(perMonth.multiply(BigDecimal.valueOf(facts.openPastMonths())));
        deductedSoFar = deductedSoFar.min(result.totalTax());

        List<TaxDtos.MonthRow> months = new ArrayList<>();
        for (TaxYear.MonthFact m : facts.months()) {
            BigDecimal tds = switch (m.source()) {
                case LOCKED -> m.tds();
                case PROJECTED -> perMonth;
                default -> null;
            };
            List<TaxDtos.HeadRow> heads = new ArrayList<>();
            for (TaxYear.Head h : m.heads()) heads.add(new TaxDtos.HeadRow(h.name(), h.amount()));
            months.add(new TaxDtos.MonthRow(m.month().toString(), m.source().name(), m.gross(), tds, m.pt(), heads));
        }
        List<TaxDtos.HraMonthRow> hra = new ArrayList<>();
        for (HraCalculator.MonthResult m : facts.hra().months()) {
            hra.add(new TaxDtos.HraMonthRow(m.month(), m.hraReceived(), m.rentLessTenPercent(), m.percentOfBasic(),
                    m.exempt()));
        }
        return response(fy, result, alternative, currency, facts.content().previousIncome(), elapsed, deductedSoFar,
                perMonth, withheld, facts.proofsDue(), months, hra, perMonth,
                claims(companyId, employee.getId(), fy, override), facts.priorIncome(), facts.priorTds());
    }

    /** What was typed and what HR approved, per line — the two figures behind each deduction. */
    private record Claim(BigDecimal claimed, BigDecimal approved, String status) {
    }

    private Map<String, Claim> claims(UUID companyId, UUID employeeId, FinancialYear fy, TaxYear.Content override) {
        Map<String, Claim> out = new HashMap<>();
        declarationRepository.findByCompanyIdAndEmployeeIdAndFinancialYear(companyId, employeeId, fy.label())
                .ifPresent(d -> {
                    for (TaxDeclarationItem i : itemRepository.findByDeclarationId(d.getId())) {
                        ProofStatus s = i.getProofStatus();
                        BigDecimal approved = switch (s) {
                            case ACCEPTED -> i.getAcceptedAmount() == null ? i.getAmount() : i.getAcceptedAmount();
                            case PARTIAL -> i.getAcceptedAmount();
                            case REJECTED -> BigDecimal.ZERO;
                            default -> null;
                        };
                        out.put(i.getDeduction().name(), new Claim(i.getAmount(), approved, s.name()));
                    }
                });
        if (override != null) {
            // A preview: what is on the screen is what is claimed; HR's decisions stay as they were.
            Map<String, Claim> typed = new HashMap<>();
            override.declared().forEach((k, v) -> {
                Claim saved = out.get(k.name());
                typed.put(k.name(), new Claim(v, saved == null ? null : saved.approved(), saved == null ? "NONE" : saved.status()));
            });
            return typed;
        }
        return out;
    }

    private static TaxDtos.ComputationResponse response(FinancialYear fy, IncomeTaxCalculator.Result r,
                                                        IncomeTaxCalculator.Result alternative, String currency,
                                                        BigDecimal previousIncome, int elapsed, BigDecimal deductedSoFar,
                                                        BigDecimal perMonth, boolean withheld, boolean proofsDue,
                                                        List<TaxDtos.MonthRow> months,
                                                        List<TaxDtos.HraMonthRow> hra, BigDecimal nextMonth,
                                                        Map<String, Claim> claims, BigDecimal priorIncome,
                                                        BigDecimal priorTds) {
        BigDecimal oldTax = r.regime() == TaxRegime.OLD ? r.totalTax() : alternative.totalTax();
        BigDecimal newTax = r.regime() == TaxRegime.NEW ? r.totalTax() : alternative.totalTax();
        TaxRegime cheaper = oldTax.compareTo(newTax) < 0 ? TaxRegime.OLD : TaxRegime.NEW;
        return new TaxDtos.ComputationResponse(fy.label(), r.regime(), r.age(), currency,
                r.grossSalary(), previousIncome, rows(r.exemptions(), claims), r.standardDeduction(), r.professionalTax(),
                r.salaryIncome(), r.houseProperty(), r.otherIncome(), r.grossTotalIncome(),
                rows(r.deductions(), claims), groupRows(r.groups()), r.totalDeductions(), r.taxableIncome(), bandRows(r),
                r.taxOnIncome(), r.rebate(), r.surcharge(), r.cess(), r.totalTax(), perMonth, elapsed,
                deductedSoFar, r.totalTax().subtract(deductedSoFar).max(BigDecimal.ZERO), nextMonth,
                new TaxDtos.RegimeComparison(oldTax, newTax, cheaper, oldTax.subtract(newTax).abs()),
                withheld, proofsDue, months, hra, priorIncome, priorTds, r.interestMovedToHouse());
    }

    private static List<TaxDtos.DeductionRow> rows(List<IncomeTaxCalculator.AllowedDeduction> list,
                                                   Map<String, Claim> claims) {
        List<TaxDtos.DeductionRow> out = new ArrayList<>();
        for (IncomeTaxCalculator.AllowedDeduction d : list) {
            Claim c = claims.get(d.key());
            out.add(new TaxDtos.DeductionRow(d.key(), d.section(), d.label(), d.declared(), d.allowed(),
                    c == null ? null : c.claimed(), c == null ? null : c.approved(), c == null ? null : c.status(),
                    d.limit(), d.usedBefore(), d.movedToHouse()));
        }
        return out;
    }

    private static List<TaxDtos.GroupRow> groupRows(List<IncomeTaxCalculator.GroupTotal> list) {
        List<TaxDtos.GroupRow> out = new ArrayList<>();
        for (IncomeTaxCalculator.GroupTotal g : list) {
            out.add(new TaxDtos.GroupRow(g.group().name(), g.claimed(), g.cap(), g.allowed()));
        }
        return out;
    }

    private static List<TaxDtos.BandRow> bandRows(IncomeTaxCalculator.Result result) {
        List<TaxDtos.BandRow> rows = new ArrayList<>();
        for (IncomeTaxCalculator.BandTax b : result.bands()) {
            rows.add(new TaxDtos.BandRow(b.from(), b.to(), b.rate().multiply(BigDecimal.valueOf(100)).stripTrailingZeros(),
                    b.taxable(), b.tax().setScale(0, java.math.RoundingMode.HALF_UP)));
        }
        return rows;
    }

    /** The month a screen computes "as of": this month inside the year, clamped to its ends outside it. */
    private static YearMonth asOfFor(FinancialYear fy) {
        YearMonth now = YearMonth.now();
        YearMonth first = YearMonth.from(fy.start());
        YearMonth last = first.plusMonths(11);
        if (now.isBefore(first)) return first;
        if (now.isAfter(last)) return last;
        return now;
    }

    // =============================================================================================
    // HR
    // =============================================================================================

    /** Everybody's declaration for the year, with how many proofs are waiting. */
    @Transactional(readOnly = true)
    public List<TaxDtos.DeclarationSummaryRow> allDeclarations(AuthPrincipal principal, String year) {
        UUID companyId = TenantContext.getCompanyId();
        requireHr(principal);
        FinancialYear fy = yearOrCurrent(year);
        List<Employee> employees = employeeRepository.findByCompanyId(companyId);
        Map<UUID, String> names = namesOf(employees);

        List<UUID> paid = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (CompensationRecord r : compensationRepository.findByCompanyIdOrderByEffectiveDateDescCreatedAtDesc(companyId)) {
            if (seen.add(r.getEmployeeId())) paid.add(r.getEmployeeId());
        }
        Map<UUID, TaxYear.Facts> facts = taxYear.facts(companyId, asOfFor(fy), paid);

        List<TaxDeclaration> declarations = declarationRepository.findByCompanyIdAndFinancialYear(companyId, fy.label());
        Map<UUID, TaxDeclaration> byEmployee = new HashMap<>();
        for (TaxDeclaration d : declarations) byEmployee.put(d.getEmployeeId(), d);
        List<UUID> ids = declarations.stream().map(TaxDeclaration::getId).toList();
        Map<UUID, Integer> proofs = new HashMap<>();
        Map<UUID, Integer> waiting = new HashMap<>();
        Map<UUID, BigDecimal> totals = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] row : proofRepository.countsFor(ids)) proofs.put((UUID) row[0], ((Number) row[1]).intValue());
            for (TaxDeclarationItem i : itemRepository.findByDeclarationIdIn(ids)) {
                if (!i.getDeduction().isIncome()) totals.merge(i.getDeclarationId(), i.getAmount(), BigDecimal::add);
                if (i.getProofStatus() == ProofStatus.SUBMITTED) waiting.merge(i.getDeclarationId(), 1, Integer::sum);
            }
            for (TaxRentPeriod r : rentRepository.findByDeclarationIdIn(ids)) {
                if (r.getProofStatus() == ProofStatus.SUBMITTED) waiting.merge(r.getDeclarationId(), 1, Integer::sum);
            }
            for (TaxHouseProperty h : houseRepository.findByDeclarationIdIn(ids)) {
                if (h.getProofStatus() == ProofStatus.SUBMITTED) waiting.merge(h.getDeclarationId(), 1, Integer::sum);
            }
            for (TaxDeclaration d : declarations) {
                if ("SUBMITTED".equals(d.getPrevStatus())) waiting.merge(d.getId(), 1, Integer::sum);
            }
        }

        List<TaxDtos.DeclarationSummaryRow> rows = new ArrayList<>();
        for (Employee e : employees) {
            TaxYear.Facts f = facts.get(e.getId());
            if (f == null) continue;   // nobody on payroll, nothing to tax
            TaxDeclaration d = byEmployee.get(e.getId());
            BigDecimal tax = IncomeTaxCalculator.compute(f.input()).totalTax();
            rows.add(new TaxDtos.DeclarationSummaryRow(e.getId().toString(), names.getOrDefault(e.getId(), "Employee"),
                    f.input().regime(), d == null ? "NOT_STARTED" : d.getStatus().name(),
                    d == null ? BigDecimal.ZERO : totals.getOrDefault(d.getId(), BigDecimal.ZERO), tax,
                    d == null ? 0 : proofs.getOrDefault(d.getId(), 0), d == null ? 0 : waiting.getOrDefault(d.getId(), 0)));
        }
        rows.sort(Comparator.comparing(TaxDtos.DeclarationSummaryRow::employeeName, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    @Transactional(readOnly = true)
    public TaxDtos.DeclarationResponse declarationOf(AuthPrincipal principal, UUID employeeId, String year) {
        requireHr(principal);
        UUID companyId = TenantContext.getCompanyId();
        return declarationView(companyId, employee(companyId, employeeId), yearOrCurrent(year));
    }

    @Transactional(readOnly = true)
    public TaxDtos.ComputationResponse computationOf(AuthPrincipal principal, UUID employeeId, String year) {
        requireHr(principal);
        UUID companyId = TenantContext.getCompanyId();
        return computationFor(companyId, employee(companyId, employeeId), yearOrCurrent(year), null);
    }

    /**
     * HR's decision on one proof. ACCEPTED with no amount accepts what was declared; PARTIAL needs an
     * amount below it; REJECTED accepts nothing and needs a reason the employee can act on.
     */
    @Transactional
    public TaxDtos.DeclarationResponse review(AuthPrincipal principal, UUID employeeId, String year,
                                              TaxDtos.ReviewPayload p) {
        requireHr(principal);
        UUID companyId = TenantContext.getCompanyId();
        Employee e = employee(companyId, employeeId);
        FinancialYear fy = yearOrCurrent(year);
        TaxDeclaration d = declarationRepository
                .findByCompanyIdAndEmployeeIdAndFinancialYear(companyId, e.getId(), fy.label())
                .orElseThrow(() -> new NotFoundException("No declaration for " + fy.label()));
        ProofStatus status;
        try {
            status = ProofStatus.valueOf(p.status());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw invalid("Choose accept, part-accept or reject.");
        }
        if (status != ProofStatus.ACCEPTED && status != ProofStatus.PARTIAL && status != ProofStatus.REJECTED) {
            throw invalid("Choose accept, part-accept or reject.");
        }
        String note = trimTo(p.note(), 400);
        if (status == ProofStatus.REJECTED && blank(note)) {
            throw invalid("Say why it was rejected, so the employee can fix it.");
        }
        switch (p.type() == null ? "" : p.type()) {
            case "ITEM" -> {
                TaxDeduction key = TaxDeduction.valueOf(p.id());
                TaxDeclarationItem item = itemRepository.findByDeclarationId(d.getId()).stream()
                        .filter(i -> i.getDeduction() == key).findFirst()
                        .orElseThrow(() -> new NotFoundException("Nothing declared for that line"));
                item.review(status, accepted(status, p.acceptedAmount(), item.getAmount()), note, principal.userId());
                itemRepository.save(item);
            }
            case "RENT" -> {
                TaxRentPeriod r = rentRepository.findById(parseId(p.id()))
                        .filter(x -> x.getDeclarationId().equals(d.getId()))
                        .orElseThrow(() -> new NotFoundException("Rent period not found"));
                r.review(status, accepted(status, p.acceptedAmount(), r.getMonthlyRent()), note, principal.userId());
                rentRepository.save(r);
            }
            case "HOUSE" -> {
                TaxHouseProperty h = houseRepository.findById(parseId(p.id()))
                        .filter(x -> x.getDeclarationId().equals(d.getId()))
                        .orElseThrow(() -> new NotFoundException("House not found"));
                h.review(status, accepted(status, p.acceptedAmount(), h.getInterest()), note, principal.userId());
                houseRepository.save(h);
            }
            case "PREVIOUS" -> {
                if (status == ProofStatus.PARTIAL) throw invalid("A previous employer's figures are accepted or rejected whole.");
                d.setPrevStatus(status.name());
                d.setPrevReviewNote(note);
                declarationRepository.save(d);
            }
            default -> throw invalid("Unknown review type");
        }
        return declarationView(companyId, e, fy);
    }

    private static BigDecimal accepted(ProofStatus status, BigDecimal amount, BigDecimal declared) {
        return switch (status) {
            case ACCEPTED -> declared;
            case REJECTED -> BigDecimal.ZERO;
            default -> {
                if (amount == null || amount.signum() < 0 || amount.compareTo(declared) >= 0) {
                    throw invalid("A part-acceptance needs an amount below the " + declared.toPlainString() + " declared.");
                }
                yield amount;
            }
        };
    }

    /** Open a submitted declaration for the employee to change again. */
    @Transactional
    public TaxDtos.DeclarationResponse reopen(AuthPrincipal principal, UUID employeeId, String year) {
        requireHr(principal);
        UUID companyId = TenantContext.getCompanyId();
        Employee e = employee(companyId, employeeId);
        FinancialYear fy = yearOrCurrent(year);
        TaxDeclaration d = declarationRepository
                .findByCompanyIdAndEmployeeIdAndFinancialYear(companyId, e.getId(), fy.label())
                .orElseThrow(() -> new NotFoundException("No declaration for " + fy.label()));
        d.reopen();
        declarationRepository.save(d);
        return declarationView(companyId, e, fy);
    }

    @Transactional(readOnly = true)
    public TaxDtos.TaxSettings taxSettings() {
        CompanySettings s = settings(TenantContext.getCompanyId());
        return new TaxDtos.TaxSettings(s.isTaxDeclarationsOpen(), s.isTaxProofsOpen(),
                s.getTaxProofDeadline() == null ? null : s.getTaxProofDeadline().toString(),
                s.getTdsSignerName(), s.getTdsSignerParent(), s.getTdsSignerDesignation(), s.getTdsSignerPlace(),
                s.getCitTdsAddress());
    }

    /** Open or close declarations and proofs, and set the proof deadline. */
    @Transactional
    public TaxDtos.TaxSettings updateTaxSettings(AuthPrincipal principal, TaxDtos.TaxSettings req) {
        requireHr(principal);
        UUID companyId = TenantContext.getCompanyId();
        CompanySettings s = settingsRepository.findById(companyId)
                .orElseGet(() -> settingsRepository.save(new CompanySettings(companyId)));
        s.setTaxDeclarationsOpen(req.declarationsOpen());
        s.setTaxProofsOpen(req.proofsOpen());
        LocalDate deadline = null;
        if (!blank(req.proofDeadline())) {
            try {
                deadline = LocalDate.parse(req.proofDeadline().trim());
            } catch (DateTimeParseException e) {
                throw invalid("The proof deadline must be a date like 2027-01-31.");
            }
        }
        s.setTaxProofDeadline(deadline);
        if (req.signerName() != null) s.setTdsSignerName(trimTo(req.signerName(), 160));
        if (req.signerParent() != null) s.setTdsSignerParent(trimTo(req.signerParent(), 160));
        if (req.signerDesignation() != null) s.setTdsSignerDesignation(trimTo(req.signerDesignation(), 120));
        if (req.signerPlace() != null) s.setTdsSignerPlace(trimTo(req.signerPlace(), 80));
        if (req.citTdsAddress() != null) s.setCitTdsAddress(trimTo(req.citTdsAddress(), 400));
        settingsRepository.save(s);
        return taxSettings();
    }

    /** The older open/close switch, kept for screens that still use it. */
    @Transactional
    public boolean setWindow(AuthPrincipal principal, boolean open) {
        TaxDtos.TaxSettings now = taxSettings();
        updateTaxSettings(principal, new TaxDtos.TaxSettings(open, now.proofsOpen(), now.proofDeadline()));
        return open;
    }

    // =============================================================================================
    // Form 130 Part B — the salary and tax working an employer certifies
    // =============================================================================================

    @Transactional(readOnly = true)
    public TaxDtos.Form130Response myForm130(AuthPrincipal principal, String year) {
        return form130(TenantContext.getCompanyId(), self(principal), yearOrCurrent(year));
    }

    @Transactional(readOnly = true)
    public TaxDtos.Form130Response form130Of(AuthPrincipal principal, UUID employeeId, String year) {
        requireHr(principal);
        UUID companyId = TenantContext.getCompanyId();
        return form130(companyId, employee(companyId, employeeId), yearOrCurrent(year));
    }

    private TaxDtos.Form130Response form130(UUID companyId, Employee e, FinancialYear fy) {
        TaxDtos.ComputationResponse c = computationFor(companyId, e, fy, null);
        CompanySettings s = settings(companyId);
        String employer = s.getLegalName() != null && !s.getLegalName().isBlank() ? s.getLegalName()
                : companyRepository.findById(companyId).map(com.calyvora.company.Company::getName).orElse("");
        var statutory = statutorySettingsService.effective(companyId);
        EmployeeFinance fin = financeRepository.findByEmployeeIdAndCompanyId(e.getId(), companyId).orElse(null);
        LocalDate from = e.getStartDate() != null && e.getStartDate().isAfter(fy.start()) ? e.getStartDate() : fy.start();
        LocalDate to = e.getEndDate() != null && e.getEndDate().isBefore(fy.end()) ? e.getEndDate() : fy.end();
        String address = declarationRepository.findByCompanyIdAndEmployeeIdAndFinancialYear(companyId, e.getId(), fy.label())
                .map(TaxDeclaration::getEmployeeAddress).orElse(null);

        // Part A: only months this employer finalised and paid — not opening balances, not projections.
        Map<String, TdsChallan> challans = depositService.challans(companyId, TdsDepositService.months(fy));
        Map<String, String> receipts = depositService.receipts(companyId, fy);
        BigDecimal[] paid = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        BigDecimal[] deducted = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        BigDecimal[] deposited = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        List<TaxDtos.ChallanRow> challanRows = new ArrayList<>();
        for (TaxDtos.MonthRow m : c.months()) {
            if (!"LOCKED".equals(m.source())) continue;
            int index = (int) (YearMonth.from(fy.start()).until(YearMonth.parse(m.month()), java.time.temporal.ChronoUnit.MONTHS) / 3);
            BigDecimal tds = m.tds() == null ? BigDecimal.ZERO : m.tds();
            paid[index] = paid[index].add(m.gross() == null ? BigDecimal.ZERO : m.gross());
            deducted[index] = deducted[index].add(tds);
            if (tds.signum() <= 0) continue;
            TdsChallan ch = challans.get(m.month());
            if (ch != null) deposited[index] = deposited[index].add(tds);
            challanRows.add(new TaxDtos.ChallanRow(m.month(), tds, ch == null ? null : ch.getBsrCode(),
                    ch == null ? null : ch.getDepositDate().toString(), ch == null ? null : ch.getChallanSerial()));
        }
        List<TaxDtos.QuarterRow> q = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            String key = TdsDepositService.quarterKey(fy, i);
            q.add(new TaxDtos.QuarterRow(key, TdsDepositService.quarterLabel(i), receipts.get(key), paid[i], deducted[i], deposited[i]));
        }
        TaxDtos.Signer signer = new TaxDtos.Signer(s.getTdsSignerName(), s.getTdsSignerParent(),
                s.getTdsSignerDesignation(), s.getTdsSignerPlace());
        return new TaxDtos.Form130Response(fy.label(), employer, s.getAddress(), statutory.getCompanyPan(),
                statutory.getTan(), s.getCitTdsAddress(), nameOf(e), address,
                fin == null ? null : fin.getPanNumber(), e.getEmployeeNo(), e.getJobTitle(),
                from.toString(), to.toString(), signer, q, challanRows, c);
    }

    // =============================================================================================
    // Views and helpers
    // =============================================================================================

    private TaxDtos.DeclarationResponse declarationView(UUID companyId, Employee e, FinancialYear fy) {
        CompanySettings s = settings(companyId);
        boolean proofsDue = TaxYear.proofsDue(s, asOfFor(fy));
        EmployeeFinance fin = financeRepository.findByEmployeeIdAndCompanyId(e.getId(), companyId).orElse(null);
        AgeBand age = AgeBand.of(fin == null ? null : fin.getDateOfBirth(), fy);
        List<PayslipComponent> template = templateService.components(companyId);
        boolean hasHra = template.stream().anyMatch(c -> "HRA".equals(c.getTaxTag()));
        boolean hasLta = template.stream().anyMatch(c -> "LTA".equals(c.getTaxTag()));
        List<TaxDtos.CatalogEntry> catalog = new ArrayList<>();
        for (TaxDeduction d : TaxDeduction.values()) {
            if (d != TaxDeduction.HRA_EXEMPTION) catalog.add(TaxDtos.CatalogEntry.of(d));
        }

        TaxDeclaration d = declarationRepository
                .findByCompanyIdAndEmployeeIdAndFinancialYear(companyId, e.getId(), fy.label()).orElse(null);
        if (d == null) {
            // Nothing on file is not an error — it is the normal state every April.
            return new TaxDtos.DeclarationResponse(e.getId().toString(), nameOf(e), null,
                    fin == null ? null : fin.getPanNumber(), fin == null ? null : fin.getParentName(), e.getJobTitle(),
                    fy.label(), TaxRegime.DEFAULT,
                    "NOT_STARTED", null, s.isTaxDeclarationsOpen(), s.isTaxProofsOpen(),
                    s.getTaxProofDeadline() == null ? null : s.getTaxProofDeadline().toString(), proofsDue, false, age,
                    fin != null && fin.getDateOfBirth() != null, hasHra, hasLta, Map.of(), List.of(), List.of(), List.of(),
                    null, catalog);
        }

        Map<String, List<TaxDtos.ProofView>> proofs = new HashMap<>();
        for (TaxProofRepository.ProofInfo p : proofRepository.infoFor(d.getId())) {
            proofs.computeIfAbsent(p.ownerType() + ":" + p.ownerId(), k -> new ArrayList<>())
                    .add(new TaxDtos.ProofView(p.id().toString(), p.fileName(), p.contentType(), p.sizeBytes(),
                            p.createdAt().toString()));
        }
        Map<String, BigDecimal> declared = new LinkedHashMap<>();
        List<TaxDtos.ItemView> items = new ArrayList<>();
        List<TaxDeclarationItem> rows = new ArrayList<>(itemRepository.findByDeclarationId(d.getId()));
        rows.sort(Comparator.comparing(i -> i.getDeduction().ordinal()));
        for (TaxDeclarationItem i : rows) {
            declared.put(i.getDeduction().name(), i.getAmount());
            items.add(new TaxDtos.ItemView(i.getDeduction().name(), i.getAmount(), i.getDetail(),
                    i.getProofStatus().name(), i.getAcceptedAmount(), i.getReviewNote(),
                    proofs.getOrDefault("ITEM:" + i.getId(), List.of())));
        }
        List<TaxDtos.RentView> rent = new ArrayList<>();
        for (TaxRentPeriod r : rentRepository.findByDeclarationIdOrderByFromMonthAsc(d.getId())) {
            rent.add(new TaxDtos.RentView(r.getId().toString(), r.getFrom().toString(), r.getTo().toString(),
                    r.getMonthlyRent(), r.getCity(), r.isMetro(), r.getLandlordName(), r.getLandlordPan(),
                    r.getLandlordAddress(), r.getLandlordRelationship(), r.getProofStatus().name(), r.getAcceptedRent(),
                    r.getReviewNote(), proofs.getOrDefault("RENT:" + r.getId(), List.of())));
        }
        List<TaxDtos.HouseView> houses = new ArrayList<>();
        for (TaxHouseProperty h : houseRepository.findByDeclarationId(d.getId())) {
            houses.add(new TaxDtos.HouseView(h.getId().toString(), h.isLetOut(), h.getAddress(), h.getLenderName(),
                    h.getLenderPan(), h.getLenderAddress(), h.getLenderType(), h.getInterest(), h.getAnnualRent(), h.getMunicipalTax(), h.getProofStatus().name(),
                    h.getAcceptedInterest(), h.getReviewNote(), proofs.getOrDefault("HOUSE:" + h.getId(), List.of())));
        }
        TaxDtos.PreviousView previous = d.getPrevIncome() == null && d.getPrevTds() == null && d.getPrevEmployerName() == null
                ? null
                : new TaxDtos.PreviousView(d.getPrevEmployerName(), d.getPrevEmployerTan(), d.getPrevIncome(),
                        d.getPrevTds(), d.getPrevPf(), d.getPrevPt(), d.getPrevStatus(), d.getPrevReviewNote(),
                        proofs.getOrDefault("PREVIOUS:" + d.getId(), List.of()));
        return new TaxDtos.DeclarationResponse(e.getId().toString(), nameOf(e), d.getEmployeeAddress(),
                fin == null ? null : fin.getPanNumber(), fin == null ? null : fin.getParentName(), e.getJobTitle(),
                fy.label(), d.getRegime(),
                d.getStatus().name(), d.getSubmittedAt() == null ? null : d.getSubmittedAt().toString(),
                s.isTaxDeclarationsOpen(), s.isTaxProofsOpen(),
                s.getTaxProofDeadline() == null ? null : s.getTaxProofDeadline().toString(), proofsDue,
                d.isParentsSenior(), age, fin != null && fin.getDateOfBirth() != null, hasHra, hasLta,
                declared, items, rent, houses, previous, catalog);
    }

    /** What is on file for one person, as content the calculator reads. */
    private TaxYear.Content savedContent(UUID companyId, UUID employeeId, FinancialYear fy, boolean proofsDue) {
        return declarationRepository.findByCompanyIdAndEmployeeIdAndFinancialYear(companyId, employeeId, fy.label())
                .map(d -> TaxYear.content(d, itemRepository.findByDeclarationId(d.getId()),
                        rentRepository.findByDeclarationIdOrderByFromMonthAsc(d.getId()),
                        houseRepository.findByDeclarationId(d.getId()), proofsDue))
                .orElse(TaxYear.Content.nothing());
    }

    /** Saved content with an unsaved payload laid over it — the parts present replace the saved ones. */
    private static TaxYear.Content merge(TaxYear.Content saved, TaxDtos.DeclarationPayload p, FinancialYear fy) {
        if (p == null) return saved;
        TaxRegime regime = p.regime() == null ? saved.regime() : p.regime();
        boolean parents = p.parentsSenior() == null ? saved.parentsSenior() : p.parentsSenior();
        Map<TaxDeduction, BigDecimal> declared = saved.declared();
        if (p.items() != null || p.declared() != null) {
            declared = new EnumMap<>(TaxDeduction.class);
            if (p.items() != null) {
                for (TaxDtos.ItemPayload i : p.items()) {
                    try {
                        if (i.amount() != null && i.amount().signum() > 0) declared.put(TaxDeduction.valueOf(i.key()), i.amount());
                    } catch (IllegalArgumentException | NullPointerException ignored) {
                        // a preview of a half-typed form; the save will name the bad key
                    }
                }
            } else {
                for (Map.Entry<String, BigDecimal> e : p.declared().entrySet()) {
                    try {
                        if (e.getValue() != null && e.getValue().signum() > 0) declared.put(TaxDeduction.valueOf(e.getKey()), e.getValue());
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }
        List<TaxYear.RentLine> rent = saved.rent();
        if (p.rent() != null) {
            rent = new ArrayList<>();
            for (TaxDtos.RentPayload r : p.rent()) {
                try {
                    YearMonth from = YearMonth.parse(r.fromMonth());
                    YearMonth to = YearMonth.parse(r.toMonth());
                    if (r.monthlyRent() != null && r.monthlyRent().signum() > 0 && !to.isBefore(from)) {
                        rent.add(new TaxYear.RentLine(from, to, r.monthlyRent(), HraCalculator.isMetro(r.city())));
                    }
                } catch (RuntimeException ignored) {
                }
            }
        }
        List<IncomeTaxCalculator.HouseProperty> houses = saved.houses();
        if (p.houses() != null) {
            houses = new ArrayList<>();
            for (TaxDtos.HousePayload h : p.houses()) {
                houses.add(new IncomeTaxCalculator.HouseProperty(h.letOut(), h.annualRent(), h.municipalTax(), h.interest()));
            }
        }
        BigDecimal prevIncome = saved.previousIncome(), prevTds = saved.previousTds(),
                prevPf = saved.previousPf(), prevPt = saved.previousPt();
        if (p.previous() != null) {
            prevIncome = TaxYear.nz(p.previous().income());
            prevTds = TaxYear.nz(p.previous().tds());
            prevPf = TaxYear.nz(p.previous().pf());
            prevPt = TaxYear.nz(p.previous().pt());
        }
        return new TaxYear.Content(regime, parents, declared, rent, houses, prevIncome, prevTds, prevPf, prevPt);
    }

    private CompanySettings settings(UUID companyId) {
        return settingsRepository.findById(companyId).orElseGet(() -> new CompanySettings(companyId));
    }

    private Employee self(AuthPrincipal principal) {
        return orgScope.selfOf(principal)
                .orElseThrow(() -> new NotFoundException(
                        "You do not have an employee profile, so there is no tax declaration to make."));
    }

    private Employee employee(UUID companyId, UUID employeeId) {
        return employeeRepository.findByIdAndCompanyId(employeeId, companyId)
                .orElseThrow(() -> new NotFoundException("Employee not found"));
    }

    private boolean isHr(AuthPrincipal principal) {
        return permissions.has(principal, com.calyvora.access.Permission.TAX_MANAGE);
    }

    private void requireHr(AuthPrincipal principal) {
        if (!isHr(principal)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "You do not have permission to perform this action");
        }
    }

    private boolean ownDeclaration(AuthPrincipal principal, TaxDeclaration d) {
        return orgScope.selfOf(principal).map(e -> e.getId().equals(d.getEmployeeId())).orElse(false);
    }

    private CompensationRecord currentSalary(UUID employeeId) {
        List<CompensationRecord> records = compensationRepository.findByEmployeeIdOrderByEffectiveDateDescCreatedAtDesc(employeeId);
        return records.isEmpty() ? null : records.get(0);
    }

    private String nameOf(Employee e) {
        if (e.getUserId() == null) return "Employee";
        return userRepository.findById(e.getUserId()).map(u -> (u.getFirstName() + " " + u.getLastName()).trim())
                .orElse("Employee");
    }

    private Map<UUID, String> namesOf(List<Employee> employees) {
        List<UUID> userIds = employees.stream().map(Employee::getUserId).filter(java.util.Objects::nonNull).toList();
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

    private static FinancialYear yearOrCurrent(String year) {
        return year == null || year.isBlank() ? FinancialYear.of(LocalDate.now()) : FinancialYear.parse(year);
    }

    private static YearMonth month(String raw, String what) {
        try {
            return YearMonth.parse(raw);
        } catch (RuntimeException e) {
            throw invalid("The " + what + " month must look like 2026-04.");
        }
    }

    private static UUID parseId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (RuntimeException e) {
            throw invalid("Unknown id");
        }
    }

    private static String pan(String raw, String what) {
        if (blank(raw)) return null;
        String v = raw.trim().toUpperCase(Locale.ROOT);
        if (!PAN.matcher(v).matches()) {
            throw invalid("The " + what + " looks wrong — a PAN is five letters, four digits and a letter.");
        }
        return v;
    }

    private static BigDecimal nn(BigDecimal v, String what) {
        if (v == null) return BigDecimal.ZERO;
        if (v.signum() < 0) throw invalid(what + " cannot be negative.");
        return v;
    }

    private static boolean eq(BigDecimal a, BigDecimal b) {
        return (a == null ? BigDecimal.ZERO : a).compareTo(b == null ? BigDecimal.ZERO : b) == 0;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static final Set<String> LENDER_TYPES = Set.of("FINANCIAL_INSTITUTION", "EMPLOYER", "OTHER");

    private static String lenderType(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String v = raw.trim().toUpperCase();
        if (!LENDER_TYPES.contains(v)) throw invalid("The lender is a financial institution, your employer, or other.");
        return v;
    }

    private static String trimTo(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, message);
    }
}
