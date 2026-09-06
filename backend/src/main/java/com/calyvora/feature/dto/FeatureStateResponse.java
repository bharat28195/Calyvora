package com.calyvora.feature.dto;

import com.calyvora.feature.FeatureService;

/**
 * One feature's state for one company, and which rule decided it.
 *
 * @param source COMPANY, AGENCY, PLAN or DEFAULT. Carried because "recruitment is off" is an
 *               unanswerable support question without it — only one of those four is a mistake.
 * @param planCode the plan responsible, when the source is PLAN
 */
public record FeatureStateResponse(
        String feature,
        String label,
        String description,
        boolean enabled,
        String source,
        String planCode
) {
    public static FeatureStateResponse of(FeatureService.Resolved r) {
        return new FeatureStateResponse(r.feature().name(), r.feature().label(),
                r.feature().description(), r.enabled(), r.source().name(), r.planCode());
    }
}
