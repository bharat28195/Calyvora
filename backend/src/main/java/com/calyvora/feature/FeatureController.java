package com.calyvora.feature;

import com.calyvora.common.security.TenantContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Which capabilities are on for the caller's own company.
 *
 * <p>Readable by anybody signed in, deliberately: the frontend uses it to decide whether to render a
 * statutory-payroll screen at all, and a member hitting a page that turns out to be empty is a worse
 * experience than the page not being offered. It exposes no data beyond the names of features and
 * booleans.
 *
 * <p>There is no write here. Switching a feature on is the vendor's decision, and lives on the
 * platform console.
 */
@RestController
@RequestMapping("/api/v1/company/features")
public class FeatureController {

    private final FeatureService service;

    public FeatureController(FeatureService service) {
        this.service = service;
    }

    @GetMapping
    public List<FeatureService.FeatureState> mine() {
        return service.statesFor(TenantContext.getCompanyId());
    }
}
