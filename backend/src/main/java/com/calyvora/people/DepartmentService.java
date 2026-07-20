package com.calyvora.people;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.TenantContext;
import com.calyvora.identity.User;
import com.calyvora.identity.UserRepository;
import com.calyvora.people.dto.CreateDepartmentRequest;
import com.calyvora.people.dto.DepartmentResponse;
import com.calyvora.people.dto.UpdateDepartmentRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Departments + reporting structure (People OS slice P2). Tenant-scoped; OWNER/ADMIN managed. */
@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;

    public DepartmentService(DepartmentRepository departmentRepository,
                             EmployeeRepository employeeRepository,
                             UserRepository userRepository) {
        this.departmentRepository = departmentRepository;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<DepartmentResponse> list() {
        UUID companyId = TenantContext.getCompanyId();
        return departmentRepository.findByCompanyIdOrderByName(companyId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public DepartmentResponse create(CreateDepartmentRequest request) {
        UUID companyId = TenantContext.getCompanyId();
        Department dept = new Department(UUID.randomUUID(), companyId, request.name().trim());
        dept.setParentId(resolveParent(companyId, null, request.parentId()));
        dept.setLeadUserId(resolveLead(companyId, request.leadUserId()));
        departmentRepository.save(dept);
        return toResponse(dept);
    }

    @Transactional
    public DepartmentResponse update(UUID id, UpdateDepartmentRequest request) {
        UUID companyId = TenantContext.getCompanyId();
        Department dept = departmentRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("Department not found"));
        if (request.name() != null && !request.name().isBlank()) {
            dept.setName(request.name().trim());
        }
        if (request.parentId() != null) {
            dept.setParentId(resolveParent(companyId, id, request.parentId()));
        }
        if (request.leadUserId() != null) {
            dept.setLeadUserId(resolveLead(companyId, request.leadUserId()));
        }
        return toResponse(dept);
    }

    @Transactional
    public void delete(UUID id) {
        UUID companyId = TenantContext.getCompanyId();
        Department dept = departmentRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("Department not found"));
        // Detach members and re-parent children to keep referential integrity, then delete.
        employeeRepository.findByDepartmentId(id).forEach(e -> e.setDepartmentId(null));
        departmentRepository.findByParentId(id).forEach(child -> child.setParentId(dept.getParentId()));
        departmentRepository.delete(dept);
    }

    // ---- helpers ----

    private DepartmentResponse toResponse(Department d) {
        String leadName = null;
        if (d.getLeadUserId() != null) {
            leadName = userRepository.findById(d.getLeadUserId()).map(User::fullName).orElse(null);
        }
        long members = employeeRepository.countByDepartmentId(d.getId());
        return new DepartmentResponse(
                d.getId().toString(), d.getName(),
                d.getParentId() == null ? null : d.getParentId().toString(),
                d.getLeadUserId() == null ? null : d.getLeadUserId().toString(),
                leadName, members);
    }

    private UUID resolveParent(UUID companyId, UUID selfId, String parentId) {
        if (parentId == null || parentId.isBlank()) {
            return null;
        }
        UUID parent = parseUuid(parentId, "Invalid parent id");
        if (parent.equals(selfId)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "A department cannot be its own parent");
        }
        departmentRepository.findByIdAndCompanyId(parent, companyId)
                .orElseThrow(() -> new NotFoundException("Parent department not found"));
        return parent;
    }

    private UUID resolveLead(UUID companyId, String leadUserId) {
        if (leadUserId == null || leadUserId.isBlank()) {
            return null;
        }
        UUID lead = parseUuid(leadUserId, "Invalid lead id");
        userRepository.findByIdAndCompanyId(lead, companyId)
                .orElseThrow(() -> new NotFoundException("Lead user not found"));
        return lead;
    }

    private static UUID parseUuid(String value, String message) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, message);
        }
    }
}
