package com.calyvora.common.security;

import com.calyvora.common.error.ErrorCode;
import com.calyvora.feature.Feature;
import com.calyvora.feature.FeatureService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Refuses a request for a module the company has not bought.
 *
 * <p>Without this, turning a feature off would hide a screen and leave the API wide open — the same
 * mistake the subscription lock made before {@link SubscriptionLockFilter} existed, where cancelling
 * changed a flag the frontend chose to respect. A plan the customer can bypass by typing a URL is not
 * a plan, it is a suggestion.
 *
 * <p>One filter mapping path prefixes to features, rather than an annotation on every controller.
 * That is a deliberate trade: a single place to read, and a new endpoint under an existing prefix is
 * covered the day it is written rather than the day somebody remembers to annotate it. The mapping
 * lives on {@link Feature} itself so the guard and the catalogue cannot drift apart.
 *
 * <p>Runs after {@link SubscriptionLockFilter} — an expired subscription is a stronger and more
 * urgent answer than a missing module, and a locked tenant should be told about the lock rather than
 * about a feature they cannot reach either way.
 */
@Component
@Order(2)
public class FeatureGuardFilter extends OncePerRequestFilter {

    private final FeatureService featureService;
    private final ObjectMapper objectMapper;

    public FeatureGuardFilter(FeatureService featureService, ObjectMapper objectMapper) {
        this.featureService = featureService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        // No tenant bound means no company to check against: signing in, the platform console, and
        // anything else that runs before a company is known. Guarding those would break login.
        return TenantContext.getCompanyIdOrNull() == null
                || Feature.guarding(request.getRequestURI()) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Feature feature = Feature.guarding(request.getRequestURI());
        UUID companyId = TenantContext.getCompanyIdOrNull();
        if (feature != null && companyId != null && !featureService.isEnabled(companyId, feature)) {
            deny(response, feature);
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * 403 with a code the frontend can act on, and a message naming the module.
     *
     * <p>Not 404: pretending the endpoint does not exist would be a lie that costs a support call.
     * The honest answer is that it exists and they do not have it, which is a sales conversation.
     */
    private void deny(HttpServletResponse response, Feature feature) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpServletResponse.SC_FORBIDDEN);
        body.put("code", ErrorCode.FORBIDDEN.name());
        body.put("message", feature.label() + " is not included in your plan.");
        body.put("feature", feature.name());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
