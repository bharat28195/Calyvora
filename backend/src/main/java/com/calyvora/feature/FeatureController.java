package com.calyvora.feature;

import com.calyvora.common.security.TenantContext;
import com.calyvora.feature.dto.FeatureStateResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Which capabilities are on for the caller's own company.
 *
 * <p>Readable by anybody signed in, deliberately: the frontend uses it to decide whether to render a
 * module's nav entry at all, and a member clicking through to a screen that answers 403 is a worse
 * experience than the screen not being offered. It exposes only feature names and booleans.
 *
 * <p>There is no write here. Which modules a company has is what the vendor sells, and it lives on
 * the platform console.
 */
@RestController
@RequestMapping("/api/v1/company/features")
public class FeatureController {

    private final FeatureService service;

    public FeatureController(FeatureService service) {
        this.service = service;
    }

    @GetMapping
    public List<FeatureStateResponse> mine() {
        return service.statesFor(TenantContext.getCompanyId()).stream()
                .map(FeatureStateResponse::of).toList();
    }
}
