package com.calyvora.document;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.TenantContext;
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

    public DocumentService(DocumentTemplateRepository templateRepository,
                           GeneratedDocumentRepository documentRepository,
                           EmployeeRepository employeeRepository,
                           UserRepository userRepository,
                           DepartmentRepository departmentRepository,
                           CompensationRepository compensationRepository,
                           CompanyRepository companyRepository) {
        this.templateRepository = templateRepository;
        this.documentRepository = documentRepository;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.compensationRepository = compensationRepository;
        this.companyRepository = companyRepository;
    }

    // ---- templates ----

    /** Lists templates, seeding the starter library the first time a company opens Documents. */
    @Transactional
    public List<TemplateResponse> listTemplates(AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        if (templateRepository.countByCompanyId(companyId) == 0) {
            seedStarters(companyId, principal.userId());
        }
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
        return new PreviewResponse(titleFor(t, req, values), MergeFields.render(t.getBody(), values), values, missing);
    }

    @Transactional
    public DocumentResponse generate(GenerateRequest req, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        DocumentTemplate t = requireTemplate(UUID.fromString(req.templateId()));
        Map<String, String> values = resolve(t, req, principal);
        UUID employeeId = req.employeeId() == null || req.employeeId().isBlank()
                ? null : UUID.fromString(req.employeeId());

        GeneratedDocument doc = new GeneratedDocument(UUID.randomUUID(), companyId, t.getId(), employeeId,
                titleFor(t, req, values), t.getKind(), MergeFields.render(t.getBody(), values), principal.userId());
        documentRepository.save(doc);
        return DocumentResponse.of(doc, values.get("employee.fullName"), values.get("signatory.name"));
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> listDocuments(UUID employeeId) {
        UUID companyId = TenantContext.getCompanyId();
        List<GeneratedDocument> docs = employeeId == null
                ? documentRepository.findByCompanyIdOrderByCreatedAtDesc(companyId)
                : documentRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId);
        Map<UUID, String> names = new HashMap<>();
        return docs.stream()
                .map(d -> DocumentResponse.of(d, nameOfEmployee(d.getEmployeeId(), companyId, names), null))
                .toList();
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

        v.put("today", MergeFields.date(LocalDate.now()));
        companyRepository.findById(companyId).ifPresent(c -> v.put("company.name", c.getName()));

        userRepository.findByIdAndCompanyId(principal.userId(), companyId).ifPresent(u -> {
            v.put("signatory.name", u.getFirstName() + " " + u.getLastName());
            v.put("signatory.title", "OWNER".equals(principal.role()) ? "Founder" : "People Operations");
        });

        if (req.employeeId() != null && !req.employeeId().isBlank()) {
            Employee e = employeeRepository.findByIdAndCompanyId(UUID.fromString(req.employeeId()), companyId)
                    .orElseThrow(() -> new NotFoundException("Employee not found"));
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
            v.put("employee.startDate", MergeFields.date(e.getStartDate()));
            v.put("employee.endDate", MergeFields.date(e.getEndDate()));
            v.put("employee.tenure", MergeFields.tenure(e.getStartDate(), e.getEndDate()));
            if (e.getDepartmentId() != null) {
                departmentRepository.findByIdAndCompanyId(e.getDepartmentId(), companyId)
                        .ifPresent(d -> v.put("employee.department", d.getName()));
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
                v.put("salary.currency", current.getCurrency());
                v.put("salary.annual", money(current.getAnnualAmount()));
                v.put("salary.monthly", money(current.getAnnualAmount()
                        .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP)));
                v.put("salary.effectiveDate", MergeFields.date(current.getEffectiveDate()));
            }
        }

        if (req.overrides() != null) {
            req.overrides().forEach((key, value) -> {
                if (value != null && !value.isBlank()) v.put(key, value.trim());
            });
        }
        v.values().removeIf(java.util.Objects::isNull);
        return v;
    }

    private String titleFor(DocumentTemplate t, GenerateRequest req, Map<String, String> values) {
        if (req.title() != null && !req.title().isBlank()) {
            return req.title().trim();
        }
        String who = values.get("employee.fullName");
        return who == null || who.isBlank() ? t.getName() : t.getName() + " — " + who;
    }

    // ---- helpers ----

    private void seedStarters(UUID companyId, UUID createdBy) {
        for (StarterTemplates.Starter s : StarterTemplates.all()) {
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
