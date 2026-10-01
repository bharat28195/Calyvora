package com.calyvora.common.security;

import com.calyvora.common.error.ApiError;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.web.CorrelationIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Writes the {@link ApiError} 403 envelope when an authenticated caller lacks the required role.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        Object cid = request.getAttribute(CorrelationIdFilter.ATTRIBUTE);
        ApiError body = ApiError.of(ErrorCode.FORBIDDEN, "You do not have permission to perform this action",
                cid != null ? cid.toString() : null, null);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
