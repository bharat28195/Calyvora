package com.calyvora.recruit;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.TenantContext;
import com.calyvora.people.Department;
import com.calyvora.people.DepartmentRepository;
import com.calyvora.recruit.dto.CandidatePayload;
import com.calyvora.recruit.dto.CandidateResponse;
import com.calyvora.recruit.dto.JobOpeningPayload;
import com.calyvora.recruit.dto.JobOpeningResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Recruitment / ATS (Owner/Admin). Job openings hold the requisitions; candidates move through a
 * hiring pipeline. Tenant-scoped throughout.
 */
@Service
public class RecruitService {

    private final JobOpeningRepository jobRepository;
    private final CandidateRepository candidateRepository;
    private final DepartmentRepository departmentRepository;

    public RecruitService(JobOpeningRepository jobRepository, CandidateRepository candidateRepository,
                          DepartmentRepository departmentRepository) {
        this.jobRepository = jobRepository;
        this.candidateRepository = candidateRepository;
        this.departmentRepository = departmentRepository;
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
