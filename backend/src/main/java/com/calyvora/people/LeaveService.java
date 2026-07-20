package com.calyvora.people;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.error.ForbiddenException;
import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.TenantContext;
import com.calyvora.identity.User;
import com.calyvora.identity.UserRepository;
import com.calyvora.people.dto.CreateLeaveRequest;
import com.calyvora.people.dto.LeaveBalanceResponse;
import com.calyvora.people.dto.LeaveRequestResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Year;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Time-off / leave (People OS slice P4): request → approve/reject/cancel, plus a simple vacation
 * balance. Members act on their own requests; OWNER/ADMIN approve and see the whole company.
 */
@Service
public class LeaveService {

    /** Sprint-2 flat annual vacation allowance; a per-policy accrual engine is future work. */
    private static final int VACATION_ALLOWANCE_DAYS = 25;

    private final LeaveRequestRepository leaveRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;

    public LeaveService(LeaveRequestRepository leaveRepository, EmployeeRepository employeeRepository,
                        UserRepository userRepository) {
        this.leaveRepository = leaveRepository;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public LeaveRequestResponse request(AuthPrincipal principal, CreateLeaveRequest dto) {
        UUID companyId = TenantContext.getCompanyId();
        Employee employee = employeeForUser(companyId, principal.userId());

        LocalDate start = LocalDate.parse(dto.startDate());
        LocalDate end = LocalDate.parse(dto.endDate());
        if (end.isBefore(start)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "End date must be on or after the start date");
        }
        int days = (int) (ChronoUnit.DAYS.between(start, end) + 1);

        LeaveRequest req = new LeaveRequest(UUID.randomUUID(), companyId, employee.getId(),
                LeaveType.valueOf(dto.type()), start, end, days,
                dto.reason() == null || dto.reason().isBlank() ? null : dto.reason().trim());
        leaveRepository.save(req);
        return LeaveRequestResponse.of(req, nameOf(employee));
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> listMine(AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        Employee employee = employeeForUser(companyId, principal.userId());
        String name = nameOf(employee);
        return leaveRepository.findByEmployeeIdOrderByCreatedAtDesc(employee.getId()).stream()
                .map(r -> LeaveRequestResponse.of(r, name))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> listAll() {
        UUID companyId = TenantContext.getCompanyId();
        Map<UUID, String> names = new HashMap<>();
        return leaveRepository.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .map(r -> LeaveRequestResponse.of(r,
                        names.computeIfAbsent(r.getEmployeeId(), this::nameOfEmployeeId)))
                .toList();
    }

    @Transactional
    public LeaveRequestResponse approve(UUID id, AuthPrincipal principal) {
        return decide(id, LeaveStatus.APPROVED, principal);
    }

    @Transactional
    public LeaveRequestResponse reject(UUID id, AuthPrincipal principal) {
        return decide(id, LeaveStatus.REJECTED, principal);
    }

    @Transactional
    public LeaveRequestResponse cancel(UUID id, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        LeaveRequest req = leaveRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("Request not found"));
        Employee employee = employeeRepository.findById(req.getEmployeeId())
                .orElseThrow(() -> new NotFoundException("Employee not found"));
        if (!employee.getUserId().equals(principal.userId())) {
            throw new ForbiddenException("You can only cancel your own requests");
        }
        if (req.getStatus() != LeaveStatus.PENDING) {
            throw new ApiException(ErrorCode.CONFLICT, "Only pending requests can be cancelled");
        }
        req.cancel();
        return LeaveRequestResponse.of(req, nameOf(employee));
    }

    @Transactional(readOnly = true)
    public LeaveBalanceResponse balance(AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        Employee employee = employeeForUser(companyId, principal.userId());
        int year = Year.now().getValue();
        int used = 0;
        int pending = 0;
        for (LeaveRequest r : leaveRepository.findByEmployeeIdOrderByCreatedAtDesc(employee.getId())) {
            if (r.getType() != LeaveType.VACATION || r.getStartDate().getYear() != year) {
                continue;
            }
            if (r.getStatus() == LeaveStatus.APPROVED) {
                used += r.getDays();
            } else if (r.getStatus() == LeaveStatus.PENDING) {
                pending += r.getDays();
            }
        }
        return new LeaveBalanceResponse(VACATION_ALLOWANCE_DAYS, used,
                Math.max(0, VACATION_ALLOWANCE_DAYS - used), pending);
    }

    // ---- helpers ----

    private LeaveRequestResponse decide(UUID id, LeaveStatus decision, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        LeaveRequest req = leaveRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("Request not found"));
        if (req.getStatus() != LeaveStatus.PENDING) {
            throw new ApiException(ErrorCode.CONFLICT, "This request has already been decided");
        }
        req.decide(decision, principal.userId());
        return LeaveRequestResponse.of(req, nameOfEmployeeId(req.getEmployeeId()));
    }

    private Employee employeeForUser(UUID companyId, UUID userId) {
        return employeeRepository.findByUserId(userId)
                .orElseGet(() -> employeeRepository.save(new Employee(UUID.randomUUID(), companyId, userId)));
    }

    private String nameOf(Employee employee) {
        return userRepository.findById(employee.getUserId()).map(User::fullName).orElse("Unknown");
    }

    private String nameOfEmployeeId(UUID employeeId) {
        return employeeRepository.findById(employeeId).map(this::nameOf).orElse("Unknown");
    }
}
