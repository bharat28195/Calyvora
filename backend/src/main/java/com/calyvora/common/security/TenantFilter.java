package com.calyvora.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Binds {@link TenantContext} from the authenticated {@link AuthPrincipal} for the duration of the
 * request (SD-2). Runs after {@link JwtAuthFilter}. Always clears the context in a finally block so
 * a pooled thread can never leak one tenant's id into another tenant's request.
 *
 * <p>Also stamps the tenant and user onto the log context, so every line written while serving the
 * request says whose it was. Until this existed a warning in the log — a payroll that took nine
 * seconds, a reset code that failed to send — could not be tied to a customer without a
 * correlation id someone had thought to copy from the browser.
 */
@Component
public class TenantFilter extends OncePerRequestFilter {

    public static final String MDC_COMPANY = "companyId";
    public static final String MDC_USER = "userId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof AuthPrincipal principal) {
                TenantContext.setCompanyId(principal.companyId());
                MDC.put(MDC_COMPANY, String.valueOf(principal.companyId()));
                MDC.put(MDC_USER, String.valueOf(principal.userId()));
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MDC.remove(MDC_COMPANY);
            MDC.remove(MDC_USER);
        }
    }
}
