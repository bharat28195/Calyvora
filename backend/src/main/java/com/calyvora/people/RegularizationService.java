package com.calyvora.people;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.error.ForbiddenException;
import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.TenantContext;
import com.calyvora.identity.User;
import com.calyvora.identity.UserRepository;
import com.calyvora.notification.NotificationService;
import com.calyvora.notification.NotificationType;
import com.calyvora.people.dto.RegularizationRequest;
import com.calyvora.people.dto.RegularizationResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Attendance regularization: an employee who forgot to clock in raises a fix-up for a past day; their
 * manager (or HR/admin) approves it, which writes the attendance record. Tenant-scoped.
 */
@Service
public class RegularizationService {

    private final AttendanceRegularizationRepository repository;
    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeService employeeService;
    private final UserRepository userRepository;
    private final NotificationService notifications;

    public RegularizationService(AttendanceRegularizationRepository repository,
                                 AttendanceRepository attendanceRepository, EmployeeRepository employeeRepository,
                                 EmployeeService employeeService, UserRepository userRepository,
                                 NotificationService notifications) {
        this.repository = repository;
        this.attendanceRepository = attendanceRepository;
        this.employeeRepository = employeeRepository;
        this.employeeService = employeeService;
        this.userRepository = userRepository;
        this.notifications = notifications;
    }

    // ---- employee ----

    @Transactional
    public RegularizationResponse raise(RegularizationRequest req, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        UUID employeeId = employeeService.ensureEmployeeId(companyId, principal.userId());
        LocalDate date = parseDate(req.date());
        if (date.isAfter(LocalDate.now())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Can't regularize a future date");
        }
        AttendanceRegularization r = new AttendanceRegularization(UUID.randomUUID(), companyId, employeeId,
                date, parseTime(req.checkIn()), parseTime(req.checkOut()), blankToNull(req.reason()));
        repository.save(r);

        // Notify the approver(s): the employee's manager, or every HR/admin if they have no manager.
        Employee me = employeeRepository.findByIdAndCompanyId(employeeId, companyId).orElse(null);
        UUID managerUserId = managerUserId(companyId, me);
        String title = "Regularization: " + names(companyId).getOrDefault(employeeId, "An employee") + " · " + date;
        if (managerUserId != null) {
            notifications.send(companyId, managerUserId, principal.userId(), NotificationType.REGULARIZATION_RAISED,
                    title, blankToNull(req.reason()), "/regularizations", "REGULARIZATION", r.getId());
        } else {
            notifications.sendAll(companyId, agentUserIds(companyId), principal.userId(),
                    NotificationType.REGULARIZATION_RAISED, title, blankToNull(req.reason()),
                    "/regularizations", "REGULARIZATION", r.getId());
        }
        return RegularizationResponse.of(r, names(companyId));
    }

    @Transactional(readOnly = true)
    public List<RegularizationResponse> mine(AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        UUID employeeId = employeeService.ensureEmployeeId(companyId, principal.userId());
        Map<UUID, String> names = names(companyId);
        return repository.findByEmployeeIdOrderByCreatedAtDesc(employeeId).stream()
                .map(r -> RegularizationResponse.of(r, names)).toList();
    }

    // ---- approver ----

    /** Pending requests this caller can act on: their reports' (manager) or every one (HR/admin). */
    @Transactional(readOnly = true)
    public List<RegularizationResponse> pending(AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        Map<UUID, String> names = names(companyId);
        List<AttendanceRegularization> all = repository
                .findByCompanyIdAndStatusOrderByCreatedAtAsc(companyId, RegularizationStatus.PENDING);
        if (isAgent(principal)) {
            return all.stream().map(r -> RegularizationResponse.of(r, names)).toList();
        }
        UUID myEmployeeId = employeeService.ensureEmployeeId(companyId, principal.userId());
        return all.stream()
                .filter(r -> isMyReport(companyId, r.getEmployeeId(), myEmployeeId))
                .map(r -> RegularizationResponse.of(r, names)).toList();
    }

    @Transactional
    public RegularizationResponse decide(UUID id, boolean approve, String note, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        AttendanceRegularization r = repository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("Regularization not found"));
        if (!isAgent(principal)
                && !isMyReport(companyId, r.getEmployeeId(), employeeService.ensureEmployeeId(companyId, principal.userId()))) {
            throw new ForbiddenException("You can't decide this request");
        }
        if (r.getStatus() != RegularizationStatus.PENDING) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "This request has already been decided");
        }
        r.setStatus(approve ? RegularizationStatus.APPROVED : RegularizationStatus.REJECTED);
        r.setDecidedBy(principal.userId());
        r.setDecidedAt(Instant.now());
        r.setDecisionNote(blankToNull(note));

        if (approve) {
            // Write the attendance record for that day (present, with the requested times).
            AttendanceRecord rec = attendanceRepository.findByEmployeeIdAndDate(r.getEmployeeId(), r.getOnDate())
                    .orElseGet(() -> attendanceRepository.save(new AttendanceRecord(UUID.randomUUID(), companyId,
                            r.getEmployeeId(), r.getOnDate(), AttendanceStatus.PRESENT, principal.userId())));
            rec.setStatus(AttendanceStatus.PRESENT);
            rec.setMarkedBy(principal.userId());
            if (r.getCheckIn() != null) rec.setCheckIn(r.getCheckIn());
            if (r.getCheckOut() != null) rec.setCheckOut(r.getCheckOut());
        }

        // Tell the employee.
        Employee emp = employeeRepository.findByIdAndCompanyId(r.getEmployeeId(), companyId).orElse(null);
        if (emp != null) {
            notifications.send(companyId, emp.getUserId(), principal.userId(),
                    NotificationType.REGULARIZATION_DECIDED,
                    "Regularization " + (approve ? "approved" : "rejected") + " · " + r.getOnDate(),
                    blankToNull(note), "/me/attendance", "REGULARIZATION", r.getId());
        }
        return RegularizationResponse.of(r, names(companyId));
    }

    // ---- helpers ----

    private boolean isAgent(AuthPrincipal principal) {
        String role = principal.role();
        return "ADMIN".equals(role) || "HR".equals(role) || "OWNER".equals(role);
    }

    private boolean isMyReport(UUID companyId, UUID employeeId, UUID managerEmployeeId) {
        return employeeRepository.findByIdAndCompanyId(employeeId, companyId)
                .map(Employee::getManagerId).filter(managerEmployeeId::equals).isPresent();
    }

    private UUID managerUserId(UUID companyId, Employee employee) {
        if (employee == null || employee.getManagerId() == null) return null;
        return employeeRepository.findByIdAndCompanyId(employee.getManagerId(), companyId)
                .map(Employee::getUserId).orElse(null);
    }

    private List<UUID> agentUserIds(UUID companyId) {
        return userRepository.findByCompanyIdOrderByCreatedAtAsc(companyId).stream()
                .filter(u -> u.getRole() == com.calyvora.identity.Role.ADMIN || u.getRole() == com.calyvora.identity.Role.HR)
                .map(User::getId).toList();
    }

    private Map<UUID, String> names(UUID companyId) {
        Map<UUID, String> map = new HashMap<>();
        for (Employee e : employeeRepository.findByCompanyId(companyId)) {
            userRepository.findById(e.getUserId()).ifPresent(u -> map.put(e.getId(), u.fullName()));
        }
        return map;
    }

    private static LocalDate parseDate(String s) {
        try { return LocalDate.parse(s.trim()); }
        catch (RuntimeException e) { throw new ApiException(ErrorCode.VALIDATION_ERROR, "Invalid date"); }
    }

    private static LocalTime parseTime(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalTime.parse(s.trim()); }
        catch (RuntimeException e) { throw new ApiException(ErrorCode.VALIDATION_ERROR, "Time must look like 09:30"); }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
