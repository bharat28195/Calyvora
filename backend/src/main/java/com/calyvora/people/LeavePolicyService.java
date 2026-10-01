package com.calyvora.people;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.TenantContext;
import com.calyvora.people.dto.LeavePolicyPayload;
import com.calyvora.people.dto.LeavePolicyResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Reads and edits a company's leave rules. */
@Service
public class LeavePolicyService {

    private final LeavePolicyRepository repository;

    public LeavePolicyService(LeavePolicyRepository repository) {
        this.repository = repository;
    }

    /**
     * Every type's policy, filling in defaults for any the company has no row for.
     *
     * <p>Filling in rather than failing matters: V45 seeds a row per type for every company that
     * existed, but a company created afterwards by any path that forgets to seed would otherwise have
     * an employee whose balance call throws. Leave is not the place to discover a provisioning gap.
     */
    @Transactional(readOnly = true)
    public List<LeavePolicyResponse> list() {
        Map<LeaveType, LeavePolicy> byType = effectivePolicies();
        List<LeavePolicyResponse> out = new ArrayList<>();
        for (LeaveType type : LeaveType.values()) {
            out.add(LeavePolicyResponse.of(byType.get(type)));
        }
        return out;
    }

    /** The policy actually in force for one type, saved or defaulted. */
    @Transactional(readOnly = true)
    public LeavePolicy effective(LeaveType type) {
        UUID companyId = TenantContext.getCompanyId();
        return repository.findByCompanyIdAndType(companyId, type)
                .orElseGet(() -> LeavePolicy.defaultFor(companyId, type));
    }

    /** All five, keyed by type — one query rather than five when building a full balance sheet. */
    @Transactional(readOnly = true)
    public Map<LeaveType, LeavePolicy> effectivePolicies() {
        UUID companyId = TenantContext.getCompanyId();
        Map<LeaveType, LeavePolicy> byType = new EnumMap<>(LeaveType.class);
        for (LeavePolicy p : repository.findByCompanyIdOrderByTypeAsc(companyId)) {
            byType.put(p.getType(), p);
        }
        for (LeaveType type : LeaveType.values()) {
            byType.computeIfAbsent(type, t -> LeavePolicy.defaultFor(companyId, t));
        }
        return byType;
    }

    @Transactional
    public LeavePolicyResponse update(LeaveType type, LeavePolicyPayload payload) {
        UUID companyId = TenantContext.getCompanyId();
        LeavePolicy policy = repository.findByCompanyIdAndType(companyId, type)
                .orElseGet(() -> repository.save(LeavePolicy.defaultFor(companyId, type)));

        if (payload.daysPerYear() != null) {
            if (payload.daysPerYear().signum() < 0) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "Days per year cannot be negative");
            }
            policy.setDaysPerYear(payload.daysPerYear());
        }
        if (payload.carryForwardCap() != null) {
            if (payload.carryForwardCap().signum() < 0) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "Carry-forward cap cannot be negative");
            }
            policy.setCarryForwardCap(payload.carryForwardCap());
        }
        if (payload.accrual() != null) {
            policy.setAccrual(parseAccrual(payload.accrual()));
        }
        if (payload.paid() != null) {
            policy.setPaid(payload.paid());
        }
        if (payload.compOffExpiryDays() != null) {
            if (payload.compOffExpiryDays() < 1) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "Comp-off must stay usable for at least a day");
            }
            policy.setCompOffExpiryDays(payload.compOffExpiryDays());
        }

        // A cap larger than the entitlement is not an error, but it is always a mistake: you cannot
        // carry forward more than a year can produce, so the number would quietly never apply.
        if (policy.getCarryForwardCap().compareTo(policy.getDaysPerYear()) > 0
                && policy.getDaysPerYear().signum() > 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Carry-forward cap cannot exceed the annual entitlement of "
                            + policy.getDaysPerYear().stripTrailingZeros().toPlainString() + " days");
        }

        return LeavePolicyResponse.of(repository.save(policy));
    }

    private LeaveAccrual parseAccrual(String value) {
        try {
            return LeaveAccrual.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Accrual must be ANNUAL or MONTHLY");
        }
    }

    /** @throws NotFoundException when a path names a leave type that does not exist */
    public static LeaveType parseType(String raw) {
        try {
            return LeaveType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new NotFoundException("No such leave type: " + raw);
        }
    }

    static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
