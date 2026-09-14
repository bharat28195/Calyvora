package com.calyvora.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;

/**
 * One WARN line for every request that took longer than it should.
 *
 * <p>The aggregate numbers live in Micrometer (see {@code OpsController}); this is the other half.
 * A p95 tells you <em>that</em> something is slow. This line tells you <em>which</em> request, for
 * <em>which</em> tenant, and with the correlation id the browser was given — because it is written
 * inside the request, the MDC stamped by {@code TenantFilter} is still on the thread.
 *
 * <p>Runs after the security chain so the tenant is already bound, and measures from there. The
 * few milliseconds of JWT verification it misses are the same on every request and not what anyone
 * is looking for.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class SlowRequestFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SlowRequestFilter.class);

    private final long thresholdMs;

    public SlowRequestFilter(@Value("${calyvora.ops.slow-request-ms:1000}") long thresholdMs) {
        this.thresholdMs = thresholdMs;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long started = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long ms = (System.nanoTime() - started) / 1_000_000;
            if (ms >= thresholdMs) {
                // The matched pattern, not the raw path: "/people/employees/{id}" groups, a UUID does not.
                Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                log.warn("Slow request: {} {} -> {} in {} ms{}",
                        request.getMethod(),
                        pattern != null ? pattern : request.getRequestURI(),
                        response.getStatus(), ms,
                        request.getQueryString() == null ? "" : " (?" + request.getQueryString() + ")");
            }
        }
    }
}
