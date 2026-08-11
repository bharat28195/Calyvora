package com.calyvora.recruit;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.TenantContext;
import com.calyvora.document.DocumentKind;
import com.calyvora.document.DocumentService;
import com.calyvora.invitation.HireDetails;
import com.calyvora.invitation.InvitationService;
import com.calyvora.invitation.dto.CreateInvitationRequest;
import com.calyvora.invitation.dto.InvitationResponse;
import com.calyvora.people.Department;
import com.calyvora.people.DepartmentRepository;
import com.calyvora.recruit.dto.CandidatePayload;
import com.calyvora.recruit.dto.CandidateResponse;
import com.calyvora.recruit.dto.HireRequest;
import com.calyvora.recruit.dto.HireResponse;
import com.calyvora.recruit.dto.JobOpeningPayload;
import com.calyvora.recruit.dto.JobOpeningResponse;
import com.calyvora.recruit.dto.MakeOfferRequest;
import com.calyvora.recruit.dto.OfferResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Recruitment / ATS (Owner/Admin). Job openings hold the requisitions; candidates move through a
 * hiring pipeline. Tenant-scoped throughout.
 */
@Service
public class RecruitService {

    /** Letter amounts read as money, not as a raw BigDecimal — "1,450,000", never "1450000.00". */
    private static final NumberFormat MONEY = NumberFormat.getNumberInstance(Locale.US);

    static {
        MONEY.setMinimumFractionDigits(0);
        MONEY.setMaximumFractionDigits(2);
    }

    private final JobOpeningRepository jobRepository;
    private final CandidateRepository candidateRepository;
    private final DepartmentRepository departmentRepository;
    private final DocumentService documentService;
    private final InvitationService invitationService;

    public RecruitService(JobOpeningRepository jobRepository, CandidateRepository candidateRepository,
                          DepartmentRepository departmentRepository, DocumentService documentService,
                          InvitationService invitationService) {
        this.jobRepository = jobRepository;
        this.candidateRepository = candidateRepository;
        this.departmentRepository = departmentRepository;
        this.documentService = documentService;
        this.invitationService = invitationService;
    }

    // ---- jobs ----

    @Transactional(readOnly = true)
    public List<JobOpeningResponse> jobs() {
        UUID companyId = TenantContext.getCompanyId();
        Map<UUID, String> depts = departmentNames(companyId);
        return jobRepository.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .map(j -> toResponse(j, depts)).toList();
    }

    @Transactional(readOnly = true)
    public JobOpeningResponse job(UUID id) {
        UUID companyId = TenantContext.getCompanyId();
        return toResponse(requireJob(id, companyId), departmentNames(companyId));
    }

    @Transactional
    public JobOpeningResponse createJob(JobOpeningPayload req, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        JobOpening job = new JobOpening(UUID.randomUUID(), companyId, req.title().trim(),
                resolveDepartment(companyId, req.departmentId()), blankToNull(req.location()),
                blankToNull(req.employmentType()), blankToNull(req.description()),
                req.positions() == null || req.positions() < 1 ? 1 : req.positions(), principal.userId());
        if (req.status() != null) job.setStatus(parseStatus(req.status()));
        jobRepository.save(job);
        return toResponse(job, departmentNames(companyId));
    }

    @Transactional
    public JobOpeningResponse updateJob(UUID id, JobOpeningPayload req) {
        UUID companyId = TenantContext.getCompanyId();
        JobOpening job = requireJob(id, companyId);
        if (req.title() != null && !req.title().isBlank()) job.setTitle(req.title().trim());
        if (req.departmentId() != null) job.setDepartmentId(resolveDepartment(companyId, req.departmentId()));
        if (req.location() != null) job.setLocation(blankToNull(req.location()));
        if (req.employmentType() != null) job.setEmploymentType(blankToNull(req.employmentType()));
        if (req.description() != null) job.setDescription(blankToNull(req.description()));
        if (req.positions() != null && req.positions() >= 1) job.setPositions(req.positions());
        if (req.status() != null) job.setStatus(parseStatus(req.status()));
        return toResponse(job, departmentNames(companyId));
    }

    @Transactional
    public void deleteJob(UUID id) {
        UUID companyId = TenantContext.getCompanyId();
        jobRepository.delete(requireJob(id, companyId));
    }

    // ---- candidates ----

    @Transactional(readOnly = true)
    public List<CandidateResponse> candidates(UUID jobId) {
        UUID companyId = TenantContext.getCompanyId();
        requireJob(jobId, companyId);
        return candidateRepository.findByJobIdOrderByCreatedAtAsc(jobId).stream()
                .map(CandidateResponse::of).toList();
    }

    @Transactional
    public CandidateResponse addCandidate(UUID jobId, CandidatePayload req) {
        UUID companyId = TenantContext.getCompanyId();
        requireJob(jobId, companyId);
        Candidate c = new Candidate(UUID.randomUUID(), companyId, jobId, req.name().trim(),
                blankToNull(req.email()), blankToNull(req.phone()), blankToNull(req.resumeUrl()),
                blankToNull(req.source()));
        if (req.stage() != null) c.setStage(parseStage(req.stage()));
        c.setRating(clampRating(req.rating()));
        c.setNotes(blankToNull(req.notes()));
        candidateRepository.save(c);
        return CandidateResponse.of(c);
    }

    @Transactional
    public CandidateResponse updateCandidate(UUID id, CandidatePayload req) {
        UUID companyId = TenantContext.getCompanyId();
        Candidate c = requireCandidate(id, companyId);
        if (req.name() != null && !req.name().isBlank()) c.setName(req.name().trim());
        if (req.email() != null) c.setEmail(blankToNull(req.email()));
        if (req.phone() != null) c.setPhone(blankToNull(req.phone()));
        if (req.resumeUrl() != null) c.setResumeUrl(blankToNull(req.resumeUrl()));
        if (req.source() != null) c.setSource(blankToNull(req.source()));
        if (req.stage() != null) c.setStage(parseStage(req.stage()));
        if (req.rating() != null) c.setRating(clampRating(req.rating()));
        if (req.notes() != null) c.setNotes(blankToNull(req.notes()));
        return CandidateResponse.of(c);
    }

    @Transactional
    public CandidateResponse moveStage(UUID id, String stage) {
        UUID companyId = TenantContext.getCompanyId();
        Candidate c = requireCandidate(id, companyId);
        c.setStage(parseStage(stage));
        return CandidateResponse.of(c);
    }

    @Transactional
    public void deleteCandidate(UUID id) {
        UUID companyId = TenantContext.getCompanyId();
        candidateRepository.delete(requireCandidate(id, companyId));
    }

    // ---- offer & hire (PD-20) ----

    /**
     * Make an offer: move the candidate to OFFER and raise the offer letter.
     *
     * <p>The candidate is not an employee and has no profile to read, so every merge value comes from
     * this request and the job opening, passed as overrides. That is exactly what overrides are for —
     * it needs no special template and no placeholder employee row.
     */
    @Transactional
    public OfferResponse makeOffer(UUID candidateId, MakeOfferRequest req, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        Candidate candidate = requireCandidate(candidateId, companyId);
        JobOpening job = requireJob(candidate.getJobId(), companyId);

        candidate.setStage(CandidateStage.OFFER);
        Map<String, String> values = letterValues(candidate, job, companyId, req.jobTitle(), req.startDate(),
                req.workLocation(), req.employmentType(), req.departmentId());
        if (req.annualSalary() != null) {
            values.put("salary.annual", MONEY.format(req.annualSalary()));
            values.put("salary.monthly", MONEY.format(req.annualSalary()
                    .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP)));
        }
        if (req.currency() != null) {
            values.put("salary.currency", req.currency());
        }

        return documentService.issueByKind(DocumentKind.OFFER_LETTER, null, values, principal)
                .map(d -> new OfferResponse(CandidateResponse.of(candidate), d.id(), d.title(), null))
                .orElseGet(() -> new OfferResponse(CandidateResponse.of(candidate), null, null,
                        "No offer letter template exists, so no letter was raised."));
    }

    /**
     * Hire: invite them, attach the agreed role to that invitation, mark the candidate HIRED and
     * raise the joining letter.
     *
     * <p>Ordering is deliberate. The invitation goes first because it is the step that can legitimately
     * refuse — the seat cap, a duplicate member, a lapsed subscription — and none of the rest should
     * happen if someone cannot actually be given a login.
     */
    @Transactional
    public HireResponse hire(UUID candidateId, HireRequest req, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        Candidate candidate = requireCandidate(candidateId, companyId);
        JobOpening job = requireJob(candidate.getJobId(), companyId);
        if (candidate.getEmail() == null || candidate.getEmail().isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Add an email address for this candidate before hiring them");
        }

        String jobTitle = req.jobTitle() != null ? req.jobTitle() : job.getTitle();
        UUID departmentId = req.departmentId() != null ? req.departmentId() : job.getDepartmentId();
        InvitationResponse invitation = invitationService.create(
                new CreateInvitationRequest(candidate.getEmail(), req.role()),
                new HireDetails(jobTitle, req.startDate(), departmentId),
                principal);

        candidate.setStage(CandidateStage.HIRED);

        if (!req.shouldIssueJoiningLetter()) {
            return new HireResponse(CandidateResponse.of(candidate), null, null,
                    invitation.id(), invitation.acceptUrl(), null);
        }
        Map<String, String> values = letterValues(candidate, job, companyId, jobTitle, req.startDate(),
                null, null, departmentId);
        return documentService.issueByKind(DocumentKind.JOINING_LETTER, null, values, principal)
                .map(d -> new HireResponse(CandidateResponse.of(candidate), d.id(), d.title(),
                        invitation.id(), invitation.acceptUrl(), null))
                .orElseGet(() -> new HireResponse(CandidateResponse.of(candidate), null, null,
                        invitation.id(), invitation.acceptUrl(),
                        "No joining letter template exists, so no letter was raised."));
    }

    /**
     * The merge values a letter for a candidate can be built from. Keyed as {@code employee.*} on
     * purpose: the templates are written for employees, and a candidate reads the same way in a
     * letter. Nulls are left out so {@link com.calyvora.document.MergeFields} renders its dash.
     */
    private Map<String, String> letterValues(Candidate candidate, JobOpening job, UUID companyId,
                                             String jobTitle, LocalDate startDate, String workLocation,
                                             String employmentType, UUID departmentId) {
        Map<String, String> v = new HashMap<>();
        put(v, "employee.fullName", candidate.getName());
        put(v, "employee.firstName", firstNameOf(candidate.getName()));
        put(v, "employee.lastName", lastNameOf(candidate.getName()));
        put(v, "employee.email", candidate.getEmail());
        put(v, "employee.phone", candidate.getPhone());
        put(v, "employee.jobTitle", jobTitle != null ? jobTitle : job.getTitle());
        put(v, "employee.workLocation", workLocation != null ? workLocation : job.getLocation());
        put(v, "employee.employmentType", employmentType != null ? employmentType : job.getEmploymentType());
        if (startDate != null) {
            put(v, "employee.startDate", com.calyvora.document.MergeFields.date(startDate));
        }
        UUID dept = departmentId != null ? departmentId : job.getDepartmentId();
        if (dept != null) {
            departmentRepository.findByIdAndCompanyId(dept, companyId)
                    .ifPresent(d -> v.put("employee.department", d.getName()));
        }
        return v;
    }

    private static void put(Map<String, String> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value.trim());
        }
    }

    /** "Dana Scully" â†’ "Dana". A single-word name is entirely the first name. */
    private static String firstNameOf(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return null;
        }
        return fullName.trim().split("\\s+")[0];
    }

    /** Everything after the first word, so "Ana Maria de Souza" keeps its surname intact. */
    private static String lastNameOf(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return null;
        }
        String[] parts = fullName.trim().split("\\s+", 2);
        return parts.length < 2 ? null : parts[1];
    }

    // ---- helpers ----

    private JobOpeningResponse toResponse(JobOpening j, Map<UUID, String> depts) {
        long total = candidateRepository.countByJobId(j.getId());
        long hired = candidateRepository.countByJobIdAndStage(j.getId(), CandidateStage.HIRED);
        String dept = j.getDepartmentId() == null ? null : depts.get(j.getDepartmentId());
        return JobOpeningResponse.of(j, dept, total, hired);
    }

    private Map<UUID, String> departmentNames(UUID companyId) {
        Map<UUID, String> map = new HashMap<>();
        for (Department d : departmentRepository.findByCompanyIdOrderByName(companyId)) {
            map.put(d.getId(), d.getName());
        }
        return map;
    }

    private UUID resolveDepartment(UUID companyId, String departmentId) {
        if (departmentId == null || departmentId.isBlank()) return null;
        UUID id;
        try {
            id = UUID.fromString(departmentId);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Invalid department id");
        }
        departmentRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("Department not found"));
        return id;
    }

    private JobOpening requireJob(UUID id, UUID companyId) {
        return jobRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("Job opening not found"));
    }

    private Candidate requireCandidate(UUID id, UUID companyId) {
        return candidateRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("Candidate not found"));
    }

    private static Integer clampRating(Integer r) {
        if (r == null) return null;
        return Math.min(5, Math.max(1, r));
    }

    private static JobStatus parseStatus(String s) {
        try { return JobStatus.valueOf(s.trim().toUpperCase()); }
        catch (RuntimeException e) { throw new ApiException(ErrorCode.VALIDATION_ERROR, "Invalid job status"); }
    }

    private static CandidateStage parseStage(String s) {
        try { return CandidateStage.valueOf(s.trim().toUpperCase()); }
        catch (RuntimeException e) { throw new ApiException(ErrorCode.VALIDATION_ERROR, "Invalid stage"); }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
