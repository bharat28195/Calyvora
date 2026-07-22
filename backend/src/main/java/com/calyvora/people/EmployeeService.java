package com.calyvora.people;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.TenantContext;
import com.calyvora.identity.User;
import com.calyvora.identity.UserRepository;
import com.calyvora.people.dto.EmployeeResponse;
import com.calyvora.people.dto.UpdateEmployeeRequest;
import com.calyvora.people.dto.UpdateMyProfileRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Employee directory & profiles (People OS slice P1). Every company member has exactly one employee
 * profile; profiles are <em>auto-provisioned</em> for users that don't have one yet, so the directory
 * always reflects the company's members without coupling the auth flow to People OS.
 * All reads/writes are tenant-scoped via {@link TenantContext} (SD-2).
 */
@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;

    public EmployeeService(EmployeeRepository employeeRepository, UserRepository userRepository,
                           DepartmentRepository departmentRepository) {
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
    }

    @Transactional
    public List<EmployeeResponse> directory() {
        UUID companyId = TenantContext.getCompanyId();
        List<User> users = userRepository.findByCompanyIdOrderByCreatedAtAsc(companyId);
        Map<UUID, Employee> byUser = ensureProfiles(companyId, users);
        return users.stream()
                .map(u -> EmployeeResponse.of(u, byUser.get(u.getId())))
                .sorted(Comparator.comparing(EmployeeResponse::firstName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional
    public EmployeeResponse get(UUID employeeId) {
        UUID companyId = TenantContext.getCompanyId();
        Employee employee = employeeRepository.findByIdAndCompanyId(employeeId, companyId)
                .orElseThrow(() -> new NotFoundException("Employee not found"));
        User user = userRepository.findById(employee.getUserId())
                .orElseThrow(() -> new NotFoundException("User not found"));
        return EmployeeResponse.of(user, employee);
    }

    @Transactional
    public EmployeeResponse me(UUID userId) {
        UUID companyId = TenantContext.getCompanyId();
        User user = userRepository.findByIdAndCompanyId(userId, companyId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        Employee employee = getOrCreate(companyId, userId);
        return EmployeeResponse.of(user, employee);
    }

    @Transactional
    public EmployeeResponse update(UUID employeeId, UpdateEmployeeRequest request) {
        UUID companyId = TenantContext.getCompanyId();
        Employee employee = employeeRepository.findByIdAndCompanyId(employeeId, companyId)
                .orElseThrow(() -> new NotFoundException("Employee not found"));

        if (request.employeeNo() != null) employee.setEmployeeNo(blankToNull(request.employeeNo()));
        if (request.jobTitle() != null) employee.setJobTitle(blankToNull(request.jobTitle()));
        if (request.employmentType() != null) {
            employee.setEmploymentType(EmploymentType.valueOf(request.employmentType()));
        }
        if (request.employmentStatus() != null) {
            employee.setEmploymentStatus(EmploymentStatus.valueOf(request.employmentStatus()));
        }
        if (request.workLocation() != null) employee.setWorkLocation(blankToNull(request.workLocation()));
        if (request.phone() != null) employee.setPhone(blankToNull(request.phone()));
        if (request.startDate() != null) {
            employee.setStartDate(request.startDate().isBlank() ? null : LocalDate.parse(request.startDate()));
        }
        if (request.endDate() != null) {
            employee.setEndDate(request.endDate().isBlank() ? null : LocalDate.parse(request.endDate()));
        }
        if (request.skills() != null) {
            String joined = request.skills().stream()
                    .map(String::trim).filter(s -> !s.isBlank()).distinct()
                    .reduce((a, b) -> a + ", " + b).orElse(null);
            employee.setSkills(joined);
        }
        if (request.rating() != null) {
            employee.setRating(request.rating() == 0 ? null : request.rating());
        }
        if (request.managerId() != null) {
            employee.setManagerId(resolveManager(companyId, employeeId, request.managerId()));
        }
        if (request.departmentId() != null) {
            employee.setDepartmentId(resolveDepartment(companyId, request.departmentId()));
        }

        User user = userRepository.findById(employee.getUserId())
                .orElseThrow(() -> new NotFoundException("User not found"));
        return EmployeeResponse.of(user, employee);
    }

    @Transactional
    public EmployeeResponse updateMe(UUID userId, UpdateMyProfileRequest request) {
        UUID companyId = TenantContext.getCompanyId();
        Employee employee = getOrCreate(companyId, userId);
        if (request.phone() != null) employee.setPhone(blankToNull(request.phone()));
        if (request.workLocation() != null) employee.setWorkLocation(blankToNull(request.workLocation()));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return EmployeeResponse.of(user, employee);
    }

    // ---- provisioning helpers ----

    /**
     * The employee id for a user, provisioning the profile if this user doesn't have one yet.
     * Lets other OS-apps (e.g. Knowledge OS authorship) attach to the People org graph without
     * duplicating the provisioning rule that People owns.
     */
    @Transactional
    public UUID ensureEmployeeId(UUID companyId, UUID userId) {
        return getOrCreate(companyId, userId).getId();
    }

    private Employee getOrCreate(UUID companyId, UUID userId) {
        return employeeRepository.findByUserId(userId)
                .orElseGet(() -> employeeRepository.save(new Employee(UUID.randomUUID(), companyId, userId)));
    }

    private Map<UUID, Employee> ensureProfiles(UUID companyId, List<User> users) {
        Map<UUID, Employee> byUser = new HashMap<>();
        for (Employee e : employeeRepository.findByCompanyId(companyId)) {
            byUser.put(e.getUserId(), e);
        }
        for (User u : users) {
            byUser.computeIfAbsent(u.getId(),
                    uid -> employeeRepository.save(new Employee(UUID.randomUUID(), companyId, uid)));
        }
        return byUser;
    }

    private UUID resolveManager(UUID companyId, UUID employeeId, String managerId) {
        if (managerId.isBlank()) {
            return null;
        }
        UUID mgr;
        try {
            mgr = UUID.fromString(managerId);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Invalid manager id");
        }
        if (mgr.equals(employeeId)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "An employee cannot manage themselves");
        }
        employeeRepository.findByIdAndCompanyId(mgr, companyId)
                .orElseThrow(() -> new NotFoundException("Manager not found"));
        return mgr;
    }

    private UUID resolveDepartment(UUID companyId, String departmentId) {
        if (departmentId.isBlank()) {
            return null;
        }
        UUID dept;
        try {
            dept = UUID.fromString(departmentId);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Invalid department id");
        }
        departmentRepository.findByIdAndCompanyId(dept, companyId)
                .orElseThrow(() -> new NotFoundException("Department not found"));
        return dept;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
