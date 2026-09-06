package com.calyvora.feature;

import com.calyvora.common.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Reads and sets per-company capability switches. */
@Service
public class FeatureService {

    private final CompanyFeatureRepository repository;

    public FeatureService(CompanyFeatureRepository repository) {
        this.repository = repository;
    }

    /**
     * Whether {@code feature} is on for the company the caller is bound to.
     *
     * <p>Absent means off. Every caller of this is a guard around behaviour that used not to exist,
     * so the safe answer when there is no row — a company nobody has configured — is the old
     * behaviour.
     */
    @Transactional(readOnly = true)
    public boolean isEnabled(Feature feature) {
        return isEnabled(TenantContext.getCompanyId(), feature);
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(UUID companyId, Feature feature) {
        return repository.findByCompanyIdAndFeature(companyId, feature)
                .map(CompanyFeature::isEnabled)
                .orElse(false);
    }

    /** Every feature and its state for one company, including the ones with no row (off). */
    @Transactional(readOnly = true)
    public List<FeatureState> statesFor(UUID companyId) {
        Map<Feature, Boolean> saved = new java.util.EnumMap<>(Feature.class);
        for (CompanyFeature f : repository.findByCompanyId(companyId)) {
            saved.put(f.getFeature(), f.isEnabled());
        }
        List<FeatureState> out = new ArrayList<>();
        for (Feature f : Feature.values()) {
            out.add(new FeatureState(f.name(), saved.getOrDefault(f, false)));
        }
        return out;
    }

    /**
     * Turn a feature on or off for one company.
     *
     * <p>Takes an explicit company id rather than reading the tenant context, because the caller is
     * the platform owner acting on somebody else's company — its own context is bound to the platform
     * company, and using it here would write the flag to the wrong row.
     */
    @Transactional
    public FeatureState set(UUID companyId, Feature feature, boolean enabled) {
        CompanyFeature row = repository.findByCompanyIdAndFeature(companyId, feature)
                .orElseGet(() -> new CompanyFeature(UUID.randomUUID(), companyId, feature, enabled));
        row.setEnabled(enabled);
        repository.save(row);
        return new FeatureState(feature.name(), enabled);
    }

    /** @param feature the enum name, so the frontend does not have to know the ordinal */
    public record FeatureState(String feature, boolean enabled) {
    }
}
