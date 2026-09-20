package com.calyvora.common.web;

import com.calyvora.common.error.ApiError;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.security.AuthPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * A ceiling on how fast one caller may ask for things.
 *
 * <p>There was none. A customer's integration with a retry loop and no backoff, a script left running
 * overnight, or somebody working through a password list could each issue requests as fast as the
 * network allowed, and every one of them borrowed a connection from a pool of ten. The first symptom
 * of any of those is the whole platform timing out for every other tenant, which is a strange way to
 * find out that one caller is misbehaving.
 *
 * <p><b>Two tiers, because the two surfaces fail differently.</b> The unauthenticated one — logging
 * in, asking for a password reset, registering — is where guessing happens, and nobody legitimately
 * logs in thirty times a minute, so it is held tight. Everything behind a token is a real customer
 * doing real work and is held loose: the number is set where a human being cannot reach it but a
 * runaway loop will, so it catches the broken integration without ever touching the busy user.
 *
 * <p>Authenticated callers are keyed by user id rather than address, so a whole office behind one
 * corporate NAT is not one caller, and someone moving between networks does not get a fresh
 * allowance. Anonymous callers can only be keyed by address, with the caveat on {@link #clientIp}.
 *
 * <p>Runs after the tenant filter, where the principal is known. The handful of microseconds a
 * rejected request spends parsing its own JWT first is worth having the right key.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    /** Where credentials are presented and accounts are created — the surface worth guessing at. */
    private static final String[] AUTH_SURFACE = {
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            "/api/v1/auth/verify-email",
            "/api/v1/auth/resend-verification",
            "/api/v1/invitations/accept",
            "/api/v1/trial-requests",
    };

    private final RateLimiter limiter;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final int authPerMinute;
    private final int apiPerMinute;

    public RateLimitFilter(RateLimiter limiter,
                           ObjectMapper objectMapper,
                           @Value("${calyvora.rate-limit.enabled:true}") boolean enabled,
                           @Value("${calyvora.rate-limit.auth-per-minute:20}") int authPerMinute,
                           @Value("${calyvora.rate-limit.api-per-minute:600}") int apiPerMinute) {
        this.limiter = limiter;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.authPerMinute = authPerMinute;
        this.apiPerMinute = apiPerMinute;
    }

    /**
     * Health checks and the refresh endpoint are never limited.
     *
     * <p>Health because the keep-alive pinger and Render's own probe would otherwise spend the
     * anonymous allowance for everybody arriving from the same edge. Refresh because a browser with
     * several tabs open renews once per tab on waking, and turning that into a 429 logs the user out
     * of a product they were using correctly — it has its own reuse-detection anyway, which is a far
     * better defence than a counter.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !enabled
                || path.startsWith("/actuator/health")
                || path.equals("/api/v1/auth/refresh")
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean authSurface = isAuthSurface(request.getRequestURI());
        AuthPrincipal principal = currentPrincipal();

        int limit;
        String key;
        if (authSurface) {
            // Keyed by address even when a token happens to be present: the point here is to slow down
            // whoever is trying credentials, and they are by definition not logged in as the victim.
            limit = authPerMinute;
            key = "auth:" + clientIp(request);
        } else if (principal != null) {
            limit = apiPerMinute;
            key = "user:" + principal.userId();
        } else {
            limit = authPerMinute;
            key = "anon:" + clientIp(request);
        }

        RateLimiter.Decision decision = limiter.take(key, limit);
        response.setHeader("X-RateLimit-Limit", String.valueOf(decision.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));

        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }
        reject(request, response, decision, authSurface);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response,
                        RateLimiter.Decision decision, boolean authSurface) throws IOException {
        // INFO, not WARN: being rate limited is the system working, and a flood that produced a WARN
        // per rejected request would bury the lines that do mean something.
        log.info("Rate limited {} {} after {} per minute; retry in {}s",
                request.getMethod(), request.getRequestURI(), decision.limit(), decision.retryAfterSeconds());

        String message = authSurface
                ? "Too many attempts. Wait a moment and try again."
                : "You are sending requests faster than this workspace allows. Wait a moment and try again.";
        ApiError body = ApiError.of(ErrorCode.RATE_LIMITED, message,
                (String) request.getAttribute(CorrelationIdFilter.ATTRIBUTE), null);

        response.setStatus(ErrorCode.RATE_LIMITED.status().value());
        response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private static boolean isAuthSurface(String path) {
        for (String p : AUTH_SURFACE) {
            if (path.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    private static AuthPrincipal currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AuthPrincipal p ? p : null;
    }

    /**
     * The caller's address, as best it can be known from behind two proxies.
     *
     * <p>{@code X-Forwarded-For} is written by whoever is in front, and a client can send one too, so
     * the leftmost entry is caller-controlled and therefore spoofable — someone determined to evade
     * this can vary it per request and get a fresh allowance each time.
     *
     * <p>It is still the right header to use. Without it every request arrives from the load
     * balancer's address, so all of our customers are one caller and the first person to fat-finger
     * their password three times locks out the entire platform. Trusting it trades an evadable limit
     * for one that does not hurt anybody, which is the better failure of the two. The unspoofable
     * version needs the edge to strip and rewrite the header before it reaches us, and belongs there
     * rather than here.
     */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        String remote = request.getRemoteAddr();
        return remote == null ? "unknown" : remote;
    }
}
