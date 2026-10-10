package com.calyvora.document;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.dto.CursorPage;
import com.calyvora.common.security.TenantContext;
import com.calyvora.common.web.Cursors;
import com.calyvora.company.CompanyRepository;
import com.calyvora.document.dto.DocumentResponse;
import com.calyvora.document.dto.GenerateRequest;
import com.calyvora.document.dto.PreviewResponse;
import com.calyvora.document.dto.TemplatePayload;
import com.calyvora.document.dto.TemplateResponse;
import com.calyvora.identity.UserRepository;
import com.calyvora.people.CompensationRecord;
import com.calyvora.people.CompensationRepository;
import com.calyvora.people.DepartmentRepository;
import com.calyvora.people.Employee;
import com.calyvora.people.EmployeeRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The Documents module (feedback D2 + D3): a per-company template library and the letters generated
 * from it. Owner/Admin-only — the controller enforces the role, since letters carry salary and
 * exit information.
 *
 * <p>Two rules shape the design: templates are seeded once and then owned by the company (we never
 * overwrite an edited template), and a generated letter's body is frozen at issue time so later
 * template edits can't silently rewrite a document someone already signed.
 */
@Service
public class DocumentService {

    private final DocumentTemplateRepository templateRepository;
    private final GeneratedDocumentRepository documentRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final CompensationRepository compensationRepository;
    private final CompanyRepository companyRepository;
    private final LetterheadRepository letterheadRepository;
    private final com.calyvora.company.CompanySettingsRepository settingsRepository;
    private final com.calyvora.people.LeavePolicyRepository leavePolicyRepository;
    private final com.calyvora.people.EmployeeFinanceRepository financeRepository;
    private final com.calyvora.people.CompensationService compensationService;
    private final SalaryTables salaryTables;

    public DocumentService(DocumentTemplateRepository templateRepository,
                           GeneratedDocumentRepository documentRepository,
                           EmployeeRepository employeeRepository,
                           UserRepository userRepository,
                           DepartmentRepository departmentRepository,
                           CompensationRepository compensationRepository,
                           CompanyRepository companyRepository,
                           LetterheadRepository letterheadRepository,
                           com.calyvora.company.CompanySettingsRepository settingsRepository,
                           com.calyvora.people.LeavePolicyRepository leavePolicyRepository,
                           com.calyvora.people.EmployeeFinanceRepository financeRepository,
                           @org.springframework.context.annotation.Lazy com.calyvora.people.CompensationService compensationService,
                           SalaryTables salaryTables) {
        this.templateRepository = templateRepository;
        this.documentRepository = documentRepository;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.compensationRepository = compensationRepository;
        this.companyRepository = companyRepository;
        this.letterheadRepository = letterheadRepository;
        this.settingsRepository = settingsRepository;
        this.leavePolicyRepository = leavePolicyRepository;
        this.financeRepository = financeRepository;
        this.compensationService = compensationService;
        this.salaryTables = salaryTables;
    }

    // ---- templates ----

    /** Lists templates, seeding the starter library the first time a company opens Documents. */
    @Transactional
    public List<TemplateResponse> listTemplates(AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        ensureStarters(companyId, principal.userId());
        return templateRepository.findByCompanyIdOrderByNameAsc(companyId).stream()
                .map(TemplateResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public TemplateResponse template(UUID templateId) {
        return TemplateResponse.of(requireTemplate(templateId));
    }

    @Transactional
    public TemplateResponse createTemplate(TemplatePayload p, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        if (p.name() == null || p.name().isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Template name is required");
        }
        if (p.body() == null || p.body().isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Template body is required");
        }
        DocumentKind kind = p.kind() == null ? DocumentKind.CUSTOM : DocumentKind.valueOf(p.kind());
        DocumentTemplate t = new DocumentTemplate(UUID.randomUUID(), companyId, p.name().trim(), kind,
                p.body(), principal.userId());
        t.setDescription(blankToNull(p.description()));
        templateRepository.save(t);
        return TemplateResponse.of(t);
    }

    @Transactional
    public TemplateResponse updateTemplate(UUID templateId, TemplatePayload p) {
        DocumentTemplate t = requireTemplate(templateId);
        if (p.name() != null && !p.name().isBlank()) t.setName(p.name().trim());
        if (p.body() != null && !p.body().isBlank()) t.setBody(p.body());
        if (p.description() != null) t.setDescription(blankToNull(p.description()));
        if (p.kind() != null) t.setKind(DocumentKind.valueOf(p.kind()));
        if (p.useLetterhead() != null) t.setUseLetterhead(p.useLetterhead());
        return TemplateResponse.of(t);
    }

    @Transactional
    public void deleteTemplate(UUID templateId) {
        templateRepository.delete(requireTemplate(templateId));
    }

    /** The merge fields the editor offers. */
    public List<MergeFields.Field> fieldCatalogue() {
        return MergeFields.catalogue();
    }

    // ---- generation ----

    /** Dry run: render without storing, and report which fields resolved to nothing. */
    @Transactional(readOnly = true)
    public PreviewResponse preview(GenerateRequest req, AuthPrincipal principal) {
        DocumentTemplate t = requireTemplate(UUID.fromString(req.templateId()));
        Map<String, String> values = resolve(t, req, principal);
        List<String> missing = new ArrayList<>();
        for (String key : MergeFields.placeholdersIn(t.getBody())) {
            String v = values.get(key);
            if (v == null || v.isBlank()) missing.add(key);
        }
        return new PreviewResponse(titleFor(t, req, values), MergeFields.render(t.getBody(), values),
                t.isUseLetterhead(), values, missing);
    }

    @Transactional
    public DocumentResponse generate(GenerateRequest req, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        DocumentTemplate t = requireTemplate(UUID.fromString(req.templateId()));
        Map<String, String> values = resolve(t, req, principal);
        UUID employeeId = req.employeeId() == null || req.employeeId().isBlank()
                ? null : UUID.fromString(req.employeeId());

        // Edited by hand after the fields filled in: the issuer's text is the letter.
        String body = req.body() != null && !req.body().isBlank()
                ? req.body().strip()
                : MergeFields.render(t.getBody(), values);
        GeneratedDocument doc = new GeneratedDocument(UUID.randomUUID(), companyId, t.getId(), employeeId,
                titleFor(t, req, values), t.getKind(), body, t.isUseLetterhead(), principal.userId());
        doc.setRecipientName(recipientName(employeeId, values));
        documentRepository.save(doc);
        return DocumentResponse.of(doc, values.get("employee.fullName"), values.get("signatory.name"));
    }

    /**
     * Issue the company's letter of a given kind without the caller naming a template — how the
     * joining, relieving and offer letters get raised automatically (PD-20).
     *
     * <p>Returns empty rather than throwing when no template of that kind exists. A company that
     * deleted its joining letter has said something by doing so, and failing a hire because a
     * template is missing would be the wrong trade: the employee record matters, the letter can be
     * raised by hand afterwards.
     */
    @Transactional
    public java.util.Optional<DocumentResponse> issueByKind(DocumentKind kind, UUID employeeId,
                                                            Map<String, String> overrides,
                                                            AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        ensureStarters(companyId, principal.userId());
        return templateRepository.findFirstByCompanyIdAndKindOrderByBuiltInAscNameAsc(companyId, kind)
                .map(t -> {
                    GenerateRequest req = new GenerateRequest(t.getId().toString(),
                            employeeId == null ? null : employeeId.toString(), null, overrides);
                    Map<String, String> values = resolve(t, req, principal);
                    GeneratedDocument doc = new GeneratedDocument(UUID.randomUUID(), companyId, t.getId(),
                            employeeId, titleFor(t, req, values), t.getKind(),
                            MergeFields.render(t.getBody(), values), t.isUseLetterhead(), principal.userId());
                    doc.setRecipientName(recipientName(employeeId, values));
                    documentRepository.save(doc);
                    return DocumentResponse.of(doc, values.get("employee.fullName"), values.get("signatory.name"));
                });
    }

    /**
     * Issued documents, newest first, one page at a time.
     *
     * <p>Every letter the company has ever generated is a permanent record, so this list only ever
     * grows — an offer, a joining letter and a relieving letter per person, plus an appraisal letter
     * each year. It had no ceiling, and the names on it were two queries per distinct employee.
     */
    @Transactional(readOnly = true)
    public CursorPage<DocumentResponse> listDocuments(UUID employeeId, String cursor, Integer size) {
        UUID companyId = TenantContext.getCompanyId();
        int limit = Cursors.limit(size);
        Cursors.Position from = Cursors.decode(cursor);
        Pageable window = PageRequest.of(0, limit + 1);

        List<GeneratedDocument> docs = employeeId == null
                ? documentRepository.pageForCompany(companyId, from.createdAt(), from.id(), window)
                : documentRepository.pageForEmployee(companyId, employeeId, from.createdAt(), from.id(), window);

        Map<UUID, String> names = namesForPage(companyId,
                docs.stream().map(GeneratedDocument::getEmployeeId).filter(java.util.Objects::nonNull).toList());
        return Cursors.of(docs, limit,
                d -> DocumentResponse.of(d, d.getEmployeeId() == null ? null : names.get(d.getEmployeeId()), null),
                GeneratedDocument::getCreatedAt, GeneratedDocument::getId);
    }

    /**
     * Display names for the employees on one page, in two reads rather than two per person.
     *
     * <p>The per-row helper below memoises within a single call, which bounded the damage but paid
     * it fresh on every load and scaled with the size of the list rather than the page.
     */
    private Map<UUID, String> namesForPage(UUID companyId, java.util.Collection<UUID> employeeIds) {
        if (employeeIds.isEmpty()) {
            return Map.of();
        }
        List<Employee> employees = employeeRepository
                .findByCompanyIdAndIdIn(companyId, new java.util.LinkedHashSet<>(employeeIds));
        java.util.Set<UUID> userIds = employees.stream()
                .map(Employee::getUserId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        Map<UUID, String> byUser = userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(userIds).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                com.calyvora.identity.User::getId,
                                u -> (u.getFirstName() + " " + u.getLastName()).trim(), (a, b) -> a));
        Map<UUID, String> byEmployee = new HashMap<>();
        for (Employee e : employees) {
            if (e.getUserId() != null && byUser.containsKey(e.getUserId())) {
                byEmployee.put(e.getId(), byUser.get(e.getUserId()));
            }
        }
        return byEmployee;
    }

    @Transactional(readOnly = true)
    public DocumentResponse document(UUID documentId) {
        UUID companyId = TenantContext.getCompanyId();
        GeneratedDocument d = documentRepository.findByIdAndCompanyId(documentId, companyId)
                .orElseThrow(() -> new NotFoundException("Document not found"));
        return DocumentResponse.of(d, nameOfEmployee(d.getEmployeeId(), companyId, new HashMap<>()), null);
    }

    @Transactional
    public void deleteDocument(UUID documentId) {
        UUID companyId = TenantContext.getCompanyId();
        documentRepository.delete(documentRepository.findByIdAndCompanyId(documentId, companyId)
                .orElseThrow(() -> new NotFoundException("Document not found")));
    }

    // ---- merge-field resolution ----

    /**
     * Builds every merge value for a render. Order matters: derived values first, caller overrides
     * last, so an issuer can always correct what the profile got wrong.
     */
    private Map<String, String> resolve(DocumentTemplate t, GenerateRequest req, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        Map<String, String> v = new LinkedHashMap<>();
        java.util.List<String> wanted = MergeFields.placeholdersIn(t.getBody());
        Letterhead lh = letterheadRepository.findById(companyId).orElse(null);
        String style = lh == null ? "LONG" : lh.getDateStyle();
        java.util.function.Function<LocalDate, String> d = x -> MergeFields.date(x, style);
        String currency = settingsRepository.findById(companyId)
                .map(com.calyvora.company.CompanySettings::getCurrency)
                .filter(c -> c != null && !c.isBlank()).orElse("INR");

        v.put("today", d.apply(LocalDate.now()));
        companyRepository.findById(companyId).ifPresent(c -> v.put("company.name", c.getName()));

        // The company's identity and standard terms, from the letterpad screen.
        if (lh != null) {
            if (lh.getAddressLines() != null) {
                v.put("company.address", String.join(", ", lh.getAddressLines().lines()
                        .map(String::trim).filter(s -> !s.isEmpty()).toList()));
            }
            v.put("company.cin", lh.getCin());
            v.put("company.gstin", lh.getGstin());
            v.put("company.website", lh.getWebsite());
            v.put("company.email", lh.getEmail());
            v.put("terms.probationDays", lh.getProbationDays() == null ? null : String.valueOf(lh.getProbationDays()));
            v.put("terms.noticeProbation", lh.getNoticeProbation());
            v.put("terms.noticePeriod", lh.getNoticePeriod());
            v.put("terms.workingDays", lh.getWorkingDays());
            v.put("terms.workingHours", lh.getWorkingHours());
            v.put("terms.payDay", lh.getPayDay());
            v.put("terms.jurisdiction", lh.getJurisdiction());
        }
        if (wanted.contains("leave.summary")) {
            v.put("leave.summary", leaveSummary(companyId));
        }

        // Who signs: the signatory named on the letterpad, or else whoever is issuing the letter.
        if (lh != null && lh.getSignatureName() != null) {
            v.put("signatory.name", lh.getSignatureName());
            v.put("signatory.title", lh.getSignatureTitle());
        } else {
            userRepository.findByIdAndCompanyId(principal.userId(), companyId).ifPresent(u -> {
                v.put("signatory.name", u.getFirstName() + " " + u.getLastName());
                v.put("signatory.title", "OWNER".equals(principal.role()) ? "Founder" : "People Operations");
            });
        }

        BigDecimal annual = null;
        BigDecimal previousAnnual = null;
        Employee employee = null;
        if (req.employeeId() != null && !req.employeeId().isBlank()) {
            Employee e = employeeRepository.findByIdAndCompanyId(UUID.fromString(req.employeeId()), companyId)
                    .orElseThrow(() -> new NotFoundException("Employee not found"));
            employee = e;
            userRepository.findByIdAndCompanyId(e.getUserId(), companyId).ifPresent(u -> {
                v.put("employee.fullName", u.getFirstName() + " " + u.getLastName());
                v.put("employee.firstName", u.getFirstName());
                v.put("employee.lastName", u.getLastName());
                v.put("employee.email", u.getEmail());
            });
            v.put("employee.employeeNo", e.getEmployeeNo());
            v.put("employee.jobTitle", e.getJobTitle());
            v.put("employee.workLocation", e.getWorkLocation());
            v.put("employee.phone", e.getPhone());
            v.put("employee.employmentType", pretty(e.getEmploymentType() == null ? null : e.getEmploymentType().name()));
            v.put("employee.startDate", d.apply(e.getStartDate()));
            v.put("employee.endDate", d.apply(e.getEndDate()));
            v.put("employee.tenure", MergeFields.tenure(e.getStartDate(), e.getEndDate()));
            if (e.getStartDate() != null && lh != null && lh.getProbationDays() != null) {
                v.put("employee.probationEndDate", d.apply(e.getStartDate().plusDays(lh.getProbationDays() - 1L)));
            }
            if (e.getDepartmentId() != null) {
                departmentRepository.findByIdAndCompanyId(e.getDepartmentId(), companyId)
                        .ifPresent(dep -> v.put("employee.department", dep.getName()));
            }
            if (e.getManagerId() != null) {
                employeeRepository.findByIdAndCompanyId(e.getManagerId(), companyId)
                        .flatMap(m -> userRepository.findByIdAndCompanyId(m.getUserId(), companyId))
                        .ifPresent(u -> v.put("employee.manager", u.getFirstName() + " " + u.getLastName()));
            }
            List<CompensationRecord> pay = compensationRepository
                    .findByEmployeeIdOrderByEffectiveDateDescCreatedAtDesc(e.getId());
            if (!pay.isEmpty()) {
                CompensationRecord current = pay.get(0);
                annual = current.getAnnualAmount();
                v.put("salary.effectiveDate", d.apply(current.getEffectiveDate()));
                for (CompensationRecord r : pay.subList(1, pay.size())) {
                    if (r.getAnnualAmount() != null && r.getAnnualAmount().compareTo(annual) != 0) {
                        previousAnnual = r.getAnnualAmount();
                        break;
                    }
                }
            }
            if (wanted.contains("payslips.recent")) {
                v.put("payslips.recent", recentPayslips(e.getId(), currency));
            }
        }

        // Somebody typed in by hand (an offer): the salary they typed drives every derived figure.
        Map<String, String> overrides = req.overrides() == null ? Map.of() : req.overrides();
        BigDecimal typed = parseAmount(overrides.get("salary.annual"));
        if (typed != null) annual = typed;

        if (annual != null) {
            v.put("salary.currency", currency);
            v.put("salary.annual", amount(annual, currency));
            v.put("salary.monthly", amount(annual.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP), currency));
            if ("INR".equals(currency)) v.put("salary.annualInWords", MergeFields.inWords(annual) + " Rupees");
            if (previousAnnual != null && previousAnnual.signum() > 0) {
                BigDecimal increase = annual.subtract(previousAnnual);
                v.put("salary.previousAnnual", amount(previousAnnual, currency));
                v.put("salary.increase", amount(increase, currency));
                v.put("salary.increasePercent", increase.multiply(BigDecimal.valueOf(100))
                        .divide(previousAnnual, 2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "%");
            }
            boolean tables = wanted.contains("salary.structure") || wanted.contains("salary.takeHome")
                    || wanted.contains("salary.ctc") || wanted.contains("salary.ctcInWords");
            if (tables) {
                com.calyvora.people.EmployeeFinance fin = employee == null ? null
                        : financeRepository.findByEmployeeIdAndCompanyId(employee.getId(), companyId).orElse(null);
                SalaryTables.Structure s = salaryTables.structure(companyId, annual, fin);
                v.put("salary.structure", SalaryTables.ctcTable(s, currency));
                v.put("salary.takeHome", SalaryTables.takeHomeTable(s, currency));
                v.put("salary.ctc", amount(s.annualCtc(), currency));
                if ("INR".equals(currency)) v.put("salary.ctcInWords", MergeFields.inWords(s.annualCtc()) + " Rupees");
            }
        }

        overrides.forEach((key, value) -> {
            if (value != null && !value.isBlank()) v.put(key, value.trim());
        });
        if (typed != null) v.put("salary.annual", amount(typed, currency));
        v.values().removeIf(java.util.Objects::isNull);
        completeName(v);
        return v;
    }

    /** "26 days a year — 12 privilege / earned leave, 7 sick leave and 7 casual leave", from the policies. */
    private String leaveSummary(UUID companyId) {
        List<String> parts = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (com.calyvora.people.LeavePolicy p : leavePolicyRepository.findByCompanyIdOrderByTypeAsc(companyId)) {
            if (!p.isPaid() || p.getDaysPerYear() == null || p.getDaysPerYear().signum() <= 0) continue;
            String label = switch (p.getType()) {
                case VACATION -> "privilege / earned leave";
                case SICK -> "sick leave";
                case PERSONAL -> "casual leave";
                default -> null;
            };
            if (label == null) continue;
            total = total.add(p.getDaysPerYear());
            parts.add(p.getDaysPerYear().stripTrailingZeros().toPlainString() + " days of " + label);
        }
        if (parts.isEmpty()) return null;
        String list = parts.size() == 1 ? parts.get(0)
                : String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.get(parts.size() - 1);
        return total.stripTrailingZeros().toPlainString() + " days a year — " + list;
    }

    /** The last three months' payslips as a table, newest first — what a salary certificate quotes. */
    private String recentPayslips(UUID employeeId, String currency) {
        StringBuilder t = new StringBuilder("| MONTH | GROSS (" + currency + ") | DEDUCTIONS (" + currency + ") | NET PAY ("
                + currency + ") |\n|---|---|---|---|\n");
        int found = 0;
        java.time.YearMonth m = java.time.YearMonth.now().minusMonths(1);
        for (int i = 0; i < 6 && found < 3; i++, m = m.minusMonths(1)) {
            try {
                com.calyvora.people.dto.PayslipResponse p = compensationService.payslip(employeeId, m.toString());
                BigDecimal deductions = p.totalDeductions().add(p.incomeTax() == null ? BigDecimal.ZERO : p.incomeTax());
                t.append("| ").append(m.atDay(1).format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)))
                        .append(" | ").append(MergeFields.inr(p.gross()))
                        .append(" | ").append(MergeFields.inr(deductions))
                        .append(" | ").append(MergeFields.inr(p.net())).append(" |\n");
                found++;
            } catch (ApiException e) {
                // Not employed or no salary that month: not a row.
            }
        }
        return found == 0 ? null : t.toString().stripTrailing();
    }

    /** "27,68,832.00" for rupees, the US grouping otherwise. */
    private static String amount(BigDecimal value, String currency) {
        return "INR".equals(currency) ? MergeFields.inr(value) : money(value);
    }

    /** A typed salary: "27,68,832", "2768832.00" and "₹ 27,68,832" all read the same. Null if not a number. */
    static BigDecimal parseAmount(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String digits = raw.replaceAll("[^0-9.]", "");
        if (digits.isEmpty()) return null;
        try {
            return new BigDecimal(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * For somebody typed in by hand, one name fills the others: "Priya Sharma" as the full name gives
     * "Dear Priya" in a template that greets by first name, and a first and last name give the full one.
     * Never overwrites what was given.
     */
    static void completeName(Map<String, String> v) {
        String full = v.get("employee.fullName");
        String first = v.get("employee.firstName");
        String last = v.get("employee.lastName");
        if ((full == null || full.isBlank()) && first != null && !first.isBlank()) {
            v.put("employee.fullName", (first + " " + (last == null ? "" : last)).trim());
        } else if (full != null && !full.isBlank()) {
            String[] parts = full.trim().split("\\s+", 2);
            v.putIfAbsent("employee.firstName", parts[0]);
            if (parts.length > 1) {
                v.putIfAbsent("employee.lastName", parts[1]);
            }
        }
    }

    /** The typed name, kept only for somebody with no employee record (V69). */
    private static String recipientName(UUID employeeId, Map<String, String> values) {
        if (employeeId != null) {
            return null;
        }
        String name = values.get("employee.fullName");
        if (name == null || name.isBlank()) {
            String first = values.getOrDefault("employee.firstName", "");
            String last = values.getOrDefault("employee.lastName", "");
            name = (first + " " + last).trim();
        }
        return name.isBlank() ? null : name.length() > 200 ? name.substring(0, 200) : name;
    }

    private String titleFor(DocumentTemplate t, GenerateRequest req, Map<String, String> values) {
        if (req.title() != null && !req.title().isBlank()) {
            return req.title().trim();
        }
        String who = values.get("employee.fullName");
        return who == null || who.isBlank() ? t.getName() : t.getName() + " — " + who;
    }

    // ---- helpers ----

    /**
     * Gives a company the starter templates it has not had yet. A company new to Documents gets the
     * whole library; one that already had the first set gets only what was added since. A template a
     * company deleted is never put back: what was handed over is remembered, not what still exists.
     */
    private void ensureStarters(UUID companyId, UUID createdBy) {
        com.calyvora.company.CompanySettings settings = settingsRepository.findById(companyId)
                .orElseGet(() -> settingsRepository.save(new com.calyvora.company.CompanySettings(companyId)));
        int had = settings.getLetterStartersSeeded();
        if (had >= StarterTemplates.CURRENT_SET && templateRepository.countByCompanyId(companyId) > 0) {
            return;
        }
        if (templateRepository.countByCompanyId(companyId) == 0 && had < StarterTemplates.CURRENT_SET) {
            had = 0;   // never opened Documents: everything
        }
        seedStarters(companyId, createdBy, had);
        settings.setLetterStartersSeeded(StarterTemplates.CURRENT_SET);
        settingsRepository.save(settings);
    }

    private void seedStarters(UUID companyId, UUID createdBy, int after) {
        for (StarterTemplates.Starter s : StarterTemplates.all()) {
            if (s.set() <= after) continue;
            DocumentTemplate t = new DocumentTemplate(UUID.randomUUID(), companyId, s.name(), s.kind(),
                    s.body(), createdBy);
            t.setDescription(s.description());
            t.setBuiltIn(true);
            templateRepository.save(t);
        }
    }

    private DocumentTemplate requireTemplate(UUID templateId) {
        return templateRepository.findByIdAndCompanyId(templateId, TenantContext.getCompanyId())
                .orElseThrow(() -> new NotFoundException("Template not found"));
    }

    /** Employee display name, memoized across a listing so a page of documents is a handful of lookups. */
    private String nameOfEmployee(UUID employeeId, UUID companyId, Map<UUID, String> cache) {
        if (employeeId == null) {
            return null;
        }
        return cache.computeIfAbsent(employeeId, id -> employeeRepository.findByIdAndCompanyId(id, companyId)
                .flatMap(e -> userRepository.findByIdAndCompanyId(e.getUserId(), companyId))
                .map(u -> u.getFirstName() + " " + u.getLastName())
                .orElse(null));
    }

    private static String money(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        NumberFormat f = NumberFormat.getNumberInstance(Locale.US);
        f.setMinimumFractionDigits(0);
        f.setMaximumFractionDigits(2);
        return f.format(amount);
    }

    /** FULL_TIME -> Full time. */
    private static String pretty(String enumName) {
        if (enumName == null) {
            return null;
        }
        String s = enumName.replace('_', ' ').toLowerCase(Locale.ENGLISH);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
