package com.calyvora.feature;

import com.calyvora.common.security.TenantContext;
import com.calyvora.company.CompanyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Whether a feature is on for a company, and why.
 *
 * <p><b>The resolution order, which is the whole design:</b>
 *
 * <ol>
 *   <li><b>The company's own override.</b> A promise made in a sales call outranks a table, so this
 *       wins over everything — including turning something ON that their plan does not include.</li>
 *   <li><b>Their agency's override</b>, when they belong to one. An agency buying twenty companies
 *       negotiates once, and setting it twenty times by hand is how one gets missed.</li>
 *   <li><b>Their plan</b>, if they are on one. Authoritative in both directions: a feature the plan
 *       omits is off, not merely unmentioned. That is what makes a plan mean something.</li>
 *   <li><b>The feature's own default.</b> Every module defaults on, so a customer who predates plans
 *       loses nothing.</li>
 * </ol>
 */
@Service
public class FeatureService {

    private final CompanyFeatureRepository repository;
    private final PlanRepository planRepository;
    private final CompanyRepository companyRepository;

    public FeatureService(CompanyFeatureRepository repository, PlanRepository planRepository,
                          CompanyRepository companyRepository) {
        this.repository = repository;
        this.planRepository = planRepository;
        this.companyRepository = companyRepository;
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(Feature feature) {
        return isEnabled(TenantContext.getCompanyId(), feature);
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(UUID companyId, Feature feature) {
        return resolve(companyId, feature).enabled();
    }

    /**
     * Whether it is on, and which of the four rules decided it.
     *
     * <p>The source is carried because "recruitment is off" is an unanswerable support question
     * without it — the console shows whether that came from an override, an agency, a plan or a
     * default, and only one of those is a mistake.
     */
    @Transactional(readOnly = true)
    public Resolved resolve(UUID companyId, Feature feature) {
        Optional<CompanyFeature> own = repository.findByCompanyIdAndFeature(companyId, feature);
        if (own.isPresent()) {
            return new Resolved(feature, own.get().isEnabled(), Source.COMPANY, null);
        }

        UUID agencyId = companyRepository.findById(companyId)
                .map(com.calyvora.company.Company::getAgencyId).orElse(null);
        if (agencyId != null) {
            Optional<CompanyFeature> agency = repository.findByCompanyIdAndFeature(agencyId, feature);
            if (agency.isPresent()) {
                return new Resolved(feature, agency.get().isEnabled(), Source.AGENCY, null);
            }
        }

        Plan plan = planFor(companyId);
        if (plan != null) {
            return new Resolved(feature, plan.includes(feature), Source.PLAN, plan.getCode());
        }
        return new Resolved(feature, feature.defaultOn(), Source.DEFAULT, null);
    }

    /** Every feature's state for one company, with its source. */
    @Transactional(readOnly = true)
    public List<Resolved> statesFor(UUID companyId) {
        // One pass over the company's own overrides rather than a query per feature: this is read on
        // the console for every company in the list.
        Map<Feature, Boolean> own = new EnumMap<>(Feature.class);
        for (CompanyFeature f : repository.findByCompanyId(companyId)) {
            own.put(f.getFeature(), f.isEnabled());
        }
        UUID agencyId = companyRepository.findById(companyId)
                .map(com.calyvora.company.Company::getAgencyId).orElse(null);
        Map<Feature, Boolean> agency = new EnumMap<>(Feature.class);
        if (agencyId != null) {
            for (CompanyFeature f : repository.findByCompanyId(agencyId)) {
                agency.put(f.getFeature(), f.isEnabled());
            }
        }
        Plan plan = planFor(companyId);

        List<Resolved> out = new ArrayList<>();
        for (Feature f : Feature.values()) {
            if (own.containsKey(f)) {
                out.add(new Resolved(f, own.get(f), Source.COMPANY, null));
            } else if (agency.containsKey(f)) {
                out.add(new Resolved(f, agency.get(f), Source.AGENCY, null));
            } else if (plan != null) {
                out.add(new Resolved(f, plan.includes(f), Source.PLAN, plan.getCode()));
            } else {
                out.add(new Resolved(f, f.defaultOn(), Source.DEFAULT, null));
            }
        }
        return out;
    }

    /**
     * Set or clear a company's own override.
     *
     * <p>Takes an explicit company id rather than reading the tenant context, because the caller is
     * the platform owner acting on somebody else's company — its own context is bound to the platform
     * company, and using it would write the flag to the wrong row.
     *
     * @param enabled null clears the override, so the company falls back to its agency, plan or the
     *                default. Without this, "put them back on their plan" would be impossible to
     *                express and an owner would have to guess what the plan said.
     */
    @Transactional
    public Resolved set(UUID companyId, Feature feature, Boolean enabled) {
        Optional<CompanyFeature> existing = repository.findByCompanyIdAndFeature(companyId, feature);
        if (enabled == null) {
            existing.ifPresent(repository::delete);
            return resolve(companyId, feature);
        }
        CompanyFeature row = existing.orElseGet(
                () -> new CompanyFeature(UUID.randomUUID(), companyId, feature, enabled));
        row.setEnabled(enabled);
        repository.save(row);
        return new Resolved(feature, enabled, Source.COMPANY, null);
    }

    private Plan planFor(UUID companyId) {
        String code = companyRepository.findById(companyId)
                .map(com.calyvora.company.Company::getPlanCode).orElse(null);
        if (code == null || code.isBlank()) {
            return null;
        }
        // A retired plan keeps working for the companies already on it — filtering it out here would
        // silently hand them the whole product the day it was retired.
        return planRepository.findById(code).orElse(null);
    }

    /** Which rule decided a feature's state. */
    public enum Source {
        /** Set for this company specifically — beats everything. */
        COMPANY,
        /** Inherited from the agency this company belongs to. */
        AGENCY,
        /** Comes with the plan they are on. */
        PLAN,
        /** Nothing configured; the feature's built-in default. */
        DEFAULT
    }

    /** @param planCode the plan that decided it, when the source is PLAN */
    public record Resolved(Feature feature, boolean enabled, Source source, String planCode) {
    }
}
