package com.calyvora.common.security;

import com.calyvora.billing.SubscriptionRepository;
import com.calyvora.common.error.ApiError;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.web.CorrelationIdFilter;
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
import java.util.UUID;

/**
 * Enforces the subscription lock server-side (PD-10 pt 1). The owner ending a company's subscription
 * used to change nothing but a flag the frontend chose to respect — the tenant kept reading and
 * writing its data through the API, so cancellation was advisory and trivially bypassed.
 *
 * <p>Runs after {@link TenantFilter}, once the request's company is known. A locked tenant gets
 * {@code 402 SUBSCRIPTION_INACTIVE} on everything except the narrow surface the lock screen itself
 * needs: signing in and out, reading who you are, and reading the subscription that explains the
 * lock. Login deliberately still succeeds — the product shows a "subscription ended" screen, which it
 * cannot do if the credentials themselves start failing.
 */
@Component
@Order(1)
public class SubscriptionLockFilter extends OncePerRequestFilter {

    /** Paths a locked tenant may still reach, so it can see and act on the lock. */
    private static final String[] ALLOWED_WHILE_LOCKED = {
            "/api/v1/auth/",
            "/api/v1/subscription/",
            "/api/v1/platform/",     // the owner's console is never tenant-locked
            "/api/v1/dev/",
            "/actuator/",
    };

    private final SubscriptionRepository subscriptionRepository;
    private final ObjectMapper objectMapper;

    public SubscriptionLockFilter(SubscriptionRepository subscriptionRepository, ObjectMapper objectMapper) {
        this.subscriptionRepository = subscriptionRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        String path = request.getRequestURI();
        for (String allowed : ALLOWED_WHILE_LOCKED) {
            if (path.startsWith(allowed)) return true;
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        UUID companyId = TenantContext.getCompanyIdOrNull();
        if (companyId != null && subscriptionRepository.findByCompanyId(companyId)
                .filter(com.calyvora.billing.Subscription::isLocked).isPresent()) {
            reject(request, response);
            return;
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ApiError body = ApiError.of(ErrorCode.SUBSCRIPTION_INACTIVE,
                "This workspace is locked because the subscription has ended. Contact your provider to renew it.",
                (String) request.getAttribute(CorrelationIdFilter.ATTRIBUTE), null);
        response.setStatus(ErrorCode.SUBSCRIPTION_INACTIVE.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
