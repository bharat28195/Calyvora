package com.calyvora.common.security;

import com.calyvora.common.error.ApiError;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.web.CorrelationIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Writes the {@link ApiError} 401 envelope when an unauthenticated caller hits a protected endpoint.
 * Security-filter exceptions never reach {@code @RestControllerAdvice}, so we render here directly.
 * The message is generic — no hint about why (Sprint1 §10, no enumeration).
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        Object cid = request.getAttribute(CorrelationIdFilter.ATTRIBUTE);
        ApiError body = ApiError.of(ErrorCode.UNAUTHORIZED, "Authentication required",
                cid != null ? cid.toString() : null, null);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
