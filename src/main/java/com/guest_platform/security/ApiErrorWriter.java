package com.guest_platform.security;

import java.io.IOException;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.guest_platform.dto.ApiErrorResponse;

import tools.jackson.databind.ObjectMapper;

/** Shared error writer for filters, which cannot use controller advice. */
@Component
public class ApiErrorWriter {
    private final ObjectMapper objectMapper;

    public ApiErrorWriter(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    public void write(HttpServletResponse response, int status, String code, String message,
            boolean retryable) throws IOException {
        write(response, status, code, message, null, retryable, null);
    }

    public void write(HttpServletResponse response, int status, String code, String message, String field,
            boolean retryable, Map<String, String> validationErrors) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(),
                new ApiErrorResponse(code, message, field, retryable, validationErrors));
    }
}
