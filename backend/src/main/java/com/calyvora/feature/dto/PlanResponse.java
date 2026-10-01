package com.calyvora.feature.dto;

import com.calyvora.feature.Feature;
import com.calyvora.feature.Plan;

import java.math.BigDecimal;
import java.util.List;

/** @param pricePerEmployee null means "charge the published price list rather than a plan price" */
public record PlanResponse(
        String code,
        String name,
        String description,
        BigDecimal pricePerEmployee,
        int sortOrder,
        boolean active,
        List<String> features
) {
    public static PlanResponse of(Plan p) {
        return new PlanResponse(p.getCode(), p.getName(), p.getDescription(), p.getPricePerEmployee(),
                p.getSortOrder(), p.isActive(), p.getFeatures().stream().map(Feature::name).sorted().toList());
    }
}
