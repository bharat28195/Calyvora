package com.calyvora.payroll;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.security.TenantContext;
import com.calyvora.feature.Feature;
import com.calyvora.feature.FeatureService;
import com.calyvora.payroll.dto.StatutorySettingsPayload;
import com.calyvora.payroll.dto.StatutorySettingsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/** Reads and edits a company's ESI / professional-tax settings and registration numbers. */
@Service
public class StatutorySettingsService {

    private final StatutorySettingsRepository repository;
    private final FeatureService featureService;

    public StatutorySettingsService(StatutorySettingsRepository repository, FeatureService featureService) {
        this.repository = repository;
        this.featureService = featureService;
    }

    /** The settings in force, saved or default — same reasoning as {@link PfSettingsService#effective}. */
    @Transactional(readOnly = true)
    public StatutorySettings effective(UUID companyId) {
        return repository.findById(companyId).orElseGet(() -> StatutorySettings.defaults(companyId));
    }

    @Transactional(readOnly = true)
    public StatutorySettingsResponse current() {
        UUID companyId = TenantContext.getCompanyId();
        return StatutorySettingsResponse.of(effective(companyId),
                featureService.isEnabled(companyId, Feature.STATUTORY_PAYROLL));
    }

    @Transactional
    public StatutorySettingsResponse update(StatutorySettingsPayload p) {
        UUID companyId = TenantContext.getCompanyId();
        StatutorySettings s = repository.findById(companyId).orElseGet(() -> StatutorySettings.defaults(companyId));

        if (p.esiEnabled() != null) s.setEsiEnabled(p.esiEnabled());
        if (p.esiEmployeeRate() != null) s.setEsiEmployeeRate(rate(p.esiEmployeeRate(), "ESI employee rate"));
        if (p.esiEmployerRate() != null) s.setEsiEmployerRate(rate(p.esiEmployerRate(), "ESI employer rate"));
        if (p.esiWageCeiling() != null) {
            if (p.esiWageCeiling().signum() <= 0) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "The ESI wage ceiling must be more than zero");
            }
            s.setEsiWageCeiling(p.esiWageCeiling());
        }
        if (p.ptEnabled() != null) s.setPtEnabled(p.ptEnabled());
        if (p.lwfEnabled() != null) s.setLwfEnabled(p.lwfEnabled());
        if (p.pfEstablishmentCode() != null) s.setPfEstablishmentCode(blankToNull(p.pfEstablishmentCode()));
        if (p.esiEmployerCode() != null) s.setEsiEmployerCode(blankToNull(p.esiEmployerCode()));
        if (p.tan() != null) s.setTan(blankToNull(p.tan()));
        if (p.companyPan() != null) s.setCompanyPan(blankToNull(p.companyPan()));
        if (p.ptRegistrationNo() != null) s.setPtRegistrationNo(blankToNull(p.ptRegistrationNo()));

        return StatutorySettingsResponse.of(repository.save(s),
                featureService.isEnabled(companyId, Feature.STATUTORY_PAYROLL));
    }

    private static BigDecimal rate(BigDecimal value, String what) {
        if (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, what + " must be between 0 and 100 percent");
        }
        return value;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
