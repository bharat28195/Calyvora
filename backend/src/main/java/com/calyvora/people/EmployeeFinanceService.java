package com.calyvora.people;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.TenantContext;
import com.calyvora.identity.User;
import com.calyvora.identity.UserRepository;
import com.calyvora.people.dto.EmployeeFinanceResponse;
import com.calyvora.people.dto.UpdateEmployeeFinanceRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * An employee's bank, statutory and identity record (the data behind "My Finances" and the payslip
 * header).
 *
 * <p>Two rules run through this class. <b>Visibility</b> is self-or-HR: nobody else, not even a
 * manager, has any business reading a colleague's bank account or PAN. <b>Ownership of edits</b> is
 * split — an employee maintains their own bank details and identity, while PF/ESI/professional-tax
 * enrolment is HR's to set, because those are employer filings and letting people edit their own
 * would be a compliance problem rather than a convenience.
 */
@Service
public class EmployeeFinanceService {

    private static final Set<String> HR_ROLES = Set.of("OWNER", "ADMIN", "HR");

    /** Fields only HR may change — they drive statutory filings, not personal preference. */
    private static final String STATUTORY_FIELDS =
            "PF, ESI and professional-tax details are maintained by HR";

    private final EmployeeFinanceRepository financeRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;

    public EmployeeFinanceService(EmployeeFinanceRepository financeRepository,
                                  EmployeeRepository employeeRepository,
                                  UserRepository userRepository) {
        this.financeRepository = financeRepository;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
    }

    /** The caller's own record, created empty on first view so the page always has something to show. */
    @Transactional
    public EmployeeFinanceResponse forSelf(UUID userId) {
        UUID employeeId = selfEmployeeId(userId);
        return respond(getOrCreate(employeeId));
    }

    /** Anyone's record — HR/admin only; the controller enforces the role. */
    @Transactional
    public EmployeeFinanceResponse forEmployee(UUID employeeId) {
        requireEmployee(employeeId);
        return respond(getOrCreate(employeeId));
    }

    @Transactional
    public EmployeeFinanceResponse updateSelf(UUID userId, UpdateEmployeeFinanceRequest request) {
        UUID employeeId = selfEmployeeId(userId);
        if (touchesStatutory(request)) {
            throw new ApiException(ErrorCode.FORBIDDEN, STATUTORY_FIELDS);
        }
        return respond(apply(getOrCreate(employeeId), request));
    }

    @Transactional
    public EmployeeFinanceResponse update(UUID employeeId, UpdateEmployeeFinanceRequest request) {
        requireEmployee(employeeId);
        return respond(apply(getOrCreate(employeeId), request));
    }

    /** True when the caller is HR/admin/owner, who may read and edit anyone's record. */
    public static boolean isHr(AuthPrincipal principal) {
        return principal != null && HR_ROLES.contains(principal.role());
    }

    /** The employee row for a payslip header — absent until someone fills it in. */
    @Transactional(readOnly = true)
    public EmployeeFinance rawOrNull(UUID employeeId) {
        return financeRepository.findByEmployeeIdAndCompanyId(employeeId, TenantContext.getCompanyId())
                .orElse(null);
    }

    // ---- internals ----

    private boolean touchesStatutory(UpdateEmployeeFinanceRequest r) {
        return r.pfStatus() != null || r.pfNumber() != null || r.uan() != null
                || r.pfJoinDate() != null || r.pfAccountName() != null
                || r.esiStatus() != null || r.esiNumber() != null
                || r.ptState() != null || r.ptLocation() != null
                || r.panVerified() != null;   // verifying your own document defeats the point
    }

    private EmployeeFinance apply(EmployeeFinance f, UpdateEmployeeFinanceRequest r) {
        if (r.paymentMode() != null) f.setPaymentMode(r.paymentMode());
        if (r.bankName() != null) f.setBankName(blankToNull(r.bankName()));
        if (r.bankAccountNo() != null) f.setBankAccountNo(blankToNull(r.bankAccountNo()));
        if (r.bankIfsc() != null) f.setBankIfsc(upperOrNull(r.bankIfsc()));
        if (r.bankAccountName() != null) f.setBankAccountName(blankToNull(r.bankAccountName()));
        if (r.bankBranch() != null) f.setBankBranch(blankToNull(r.bankBranch()));

        if (r.pfStatus() != null) f.setPfStatus(r.pfStatus());
        if (r.pfNumber() != null) f.setPfNumber(blankToNull(r.pfNumber()));
        if (r.uan() != null) f.setUan(blankToNull(r.uan()));
        if (r.pfJoinDate() != null) f.setPfJoinDate(parseDate(r.pfJoinDate(), "PF join date"));
        if (r.pfAccountName() != null) f.setPfAccountName(blankToNull(r.pfAccountName()));

        if (r.esiStatus() != null) f.setEsiStatus(r.esiStatus());
        if (r.esiNumber() != null) f.setEsiNumber(blankToNull(r.esiNumber()));

        if (r.ptState() != null) f.setPtState(blankToNull(r.ptState()));
        if (r.ptLocation() != null) f.setPtLocation(blankToNull(r.ptLocation()));

        if (r.panNumber() != null) {
            String pan = upperOrNull(r.panNumber());
            // A changed PAN is an unverified PAN — otherwise editing it would carry the tick over.
            if (pan == null || !pan.equals(f.getPanNumber())) {
                f.setPanVerified(false);
            }
            f.setPanNumber(pan);
        }
        if (r.panVerified() != null) f.setPanVerified(r.panVerified());
        if (r.dateOfBirth() != null) f.setDateOfBirth(parseDate(r.dateOfBirth(), "date of birth"));
        if (r.parentName() != null) f.setParentName(blankToNull(r.parentName()));
        return f;
    }

    private EmployeeFinance getOrCreate(UUID employeeId) {
        UUID companyId = TenantContext.getCompanyId();
        return financeRepository.findByEmployeeIdAndCompanyId(employeeId, companyId)
                .orElseGet(() -> financeRepository.save(new EmployeeFinance(employeeId, companyId)));
    }

    private EmployeeFinanceResponse respond(EmployeeFinance f) {
        return EmployeeFinanceResponse.of(f, nameOf(f.getEmployeeId()));
    }

    private String nameOf(UUID employeeId) {
        return employeeRepository.findById(employeeId)
                .flatMap(e -> userRepository.findById(e.getUserId()))
                .map(User::fullName)
                .orElse("Employee");
    }

    private UUID selfEmployeeId(UUID userId) {
        UUID companyId = TenantContext.getCompanyId();
        return employeeRepository.findByUserId(userId)
                .filter(e -> e.getCompanyId().equals(companyId))
                .map(Employee::getId)
                .orElseThrow(() -> new NotFoundException("No employee profile for this user"));
    }

    private void requireEmployee(UUID employeeId) {
        employeeRepository.findByIdAndCompanyId(employeeId, TenantContext.getCompanyId())
                .orElseThrow(() -> new NotFoundException("Employee not found"));
    }

    private static LocalDate parseDate(String value, String what) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Invalid " + what + " — use YYYY-MM-DD");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String upperOrNull(String s) {
        String v = blankToNull(s);
        return v == null ? null : v.toUpperCase(Locale.ROOT);
    }
}
