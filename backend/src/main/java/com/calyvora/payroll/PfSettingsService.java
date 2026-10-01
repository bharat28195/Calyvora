package com.calyvora.payroll;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.security.TenantContext;
import com.calyvora.feature.Feature;
import com.calyvora.feature.FeatureService;
import com.calyvora.payroll.dto.PfSettingsPayload;
import com.calyvora.payroll.dto.PfSettingsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/** Reads and edits a company's Provident Fund rates. */
@Service
public class PfSettingsService {

    private final PfSettingsRepository repository;
    private final FeatureService featureService;

    public PfSettingsService(PfSettingsRepository repository, FeatureService featureService) {
        this.repository = repository;
        this.featureService = featureService;
    }

    /**
     * The rates in force, saved or statutory.
     *
     * <p>Defaulting rather than failing when there is no row keeps the calculator honest for a
     * company that has switched the feature on without visiting the settings screen: it gets the
     * lawful rates, not a null pointer in the middle of a payroll run.
     */
    @Transactional(readOnly = true)
    public PfSettings effective(UUID companyId) {
        return repository.findById(companyId).orElseGet(() -> PfSettings.defaults(companyId));
    }

    @Transactional(readOnly = true)
    public PfSettingsResponse current() {
        UUID companyId = TenantContext.getCompanyId();
        return PfSettingsResponse.of(effective(companyId),
                featureService.isEnabled(companyId, Feature.STATUTORY_PAYROLL));
    }

    @Transactional
    public PfSettingsResponse update(PfSettingsPayload payload) {
        UUID companyId = TenantContext.getCompanyId();
        PfSettings s = repository.findById(companyId).orElseGet(() -> PfSettings.defaults(companyId));

        if (payload.wageCeiling() != null) {
            if (payload.wageCeiling().signum() <= 0) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "The wage ceiling must be more than zero");
            }
            s.setWageCeiling(payload.wageCeiling());
        }
        if (payload.restrictToCeiling() != null) {
            s.setRestrictToCeiling(payload.restrictToCeiling());
        }
        if (payload.employeeRate() != null) {
            s.setEmployeeRate(rate(payload.employeeRate(), "Employee rate"));
        }
        if (payload.employerRate() != null) {
            s.setEmployerRate(rate(payload.employerRate(), "Employer rate"));
        }
        if (payload.epsRate() != null) {
            s.setEpsRate(rate(payload.epsRate(), "Pension (EPS) rate"));
        }
        if (payload.adminChargeRate() != null) {
            s.setAdminChargeRate(rate(payload.adminChargeRate(), "Admin charges"));
        }
        if (payload.edliRate() != null) {
            s.setEdliRate(rate(payload.edliRate(), "EDLI rate"));
        }

        // EPS is carved out of the employer's contribution, never added to it. The database has the
        // same CHECK; refusing here turns a constraint violation into a sentence somebody can act on.
        if (s.getEpsRate().compareTo(s.getEmployerRate()) > 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "The pension (EPS) rate comes out of the employer's contribution, so it cannot exceed "
                            + s.getEmployerRate().stripTrailingZeros().toPlainString() + "%");
        }

        return PfSettingsResponse.of(repository.save(s),
                featureService.isEnabled(companyId, Feature.STATUTORY_PAYROLL));
    }

    private BigDecimal rate(BigDecimal value, String what) {
        if (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, what + " must be between 0 and 100 percent");
        }
        return value;
    }
}
