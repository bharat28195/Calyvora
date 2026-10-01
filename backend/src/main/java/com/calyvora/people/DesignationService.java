package com.calyvora.people;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.TenantContext;
import com.calyvora.people.dto.DesignationRequest;
import com.calyvora.people.dto.DesignationResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * The ladder a company defines for itself — Intern, Junior, Senior, Lead, whatever they call them.
 *
 * <p>Editable by ADMIN and HR (enforced on {@link DesignationController}) precisely because it grants
 * nothing: see {@link Designation}. Access is the reporting tree plus the role, and neither reads a
 * designation.
 */
@Service
public class DesignationService {

    private final DesignationRepository designationRepository;
    private final EmployeeRepository employeeRepository;

    public DesignationService(DesignationRepository designationRepository, EmployeeRepository employeeRepository) {
        this.designationRepository = designationRepository;
        this.employeeRepository = employeeRepository;
    }

    /**
     * @param includeArchived the editor wants the archived ones greyed out; the picker on the employee
     *                        form does not, or it would keep offering rungs the company retired.
     */
    @Transactional(readOnly = true)
    public List<DesignationResponse> list(boolean includeArchived) {
        UUID companyId = TenantContext.getCompanyId();
        List<Designation> rows = includeArchived
                ? designationRepository.findByCompanyIdOrderByLevelAscNameAsc(companyId)
                : designationRepository.findByCompanyIdAndArchivedFalseOrderByLevelAscNameAsc(companyId);
        return rows.stream()
                .map(d -> DesignationResponse.of(d, employeeRepository.countByDesignationId(d.getId())))
                .toList();
    }

    @Transactional
    public DesignationResponse create(DesignationRequest request) {
        UUID companyId = TenantContext.getCompanyId();
        String name = request.name().trim();
        requireNameFree(companyId, name, null);
        Designation d = new Designation(UUID.randomUUID(), companyId, name, request.level());
        d.setArchived(request.archived());
        return DesignationResponse.of(designationRepository.save(d), 0);
    }

    @Transactional
    public DesignationResponse update(UUID id, DesignationRequest request) {
        UUID companyId = TenantContext.getCompanyId();
        Designation d = designationRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("Designation not found"));
        String name = request.name().trim();
        requireNameFree(companyId, name, id);
        d.setName(name);
        d.setLevel(request.level());
        d.setArchived(request.archived());
        return DesignationResponse.of(designationRepository.save(d),
                employeeRepository.countByDesignationId(d.getId()));
    }

    /**
     * Delete only while nobody holds it. Once somebody does, the answer is to archive instead: deleting
     * would blank the designation on their profile, and "my level disappeared" is a support call that
     * costs more than the row it saved. The message says the number so the reply is actionable.
     */
    @Transactional
    public void delete(UUID id) {
        UUID companyId = TenantContext.getCompanyId();
        Designation d = designationRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("Designation not found"));
        long held = employeeRepository.countByDesignationId(id);
        if (held > 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    held + " " + (held == 1 ? "person holds" : "people hold")
                            + " this designation. Archive it instead — they keep it, and it stops being offered.");
        }
        designationRepository.delete(d);
    }

    /** Case-insensitive, matching the database index: "Senior Dev" and "senior dev" are one rung. */
    private void requireNameFree(UUID companyId, String name, UUID allowedId) {
        designationRepository.findByCompanyIdAndNameIgnoreCase(companyId, name)
                .filter(existing -> !existing.getId().equals(allowedId))
                .ifPresent(existing -> {
                    throw new ApiException(ErrorCode.VALIDATION_ERROR,
                            "There is already a designation called " + existing.getName() + ".");
                });
    }
}
