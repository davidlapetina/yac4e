package com.example.c4editor.api;

import com.example.c4editor.api.Dtos.ApiError;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jboss.logging.Logger;

@Provider
public class ApiExceptionMapper implements ExceptionMapper<Throwable> {
    private static final Logger LOG = Logger.getLogger(ApiExceptionMapper.class);

    @Override
    public Response toResponse(Throwable throwable) {
        if (throwable instanceof ApiException api) {
            return error(api.status, api.code, api.getMessage(), api.details);
        }
        if (throwable instanceof ConstraintViolationException validation) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("violations", validation.getConstraintViolations().stream()
                    .map(v -> Map.of("path", v.getPropertyPath().toString(), "message", v.getMessage()))
                    .toList());
            return error(Response.Status.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", details);
        }
        if (throwable instanceof NotFoundException) {
            return error(Response.Status.NOT_FOUND, "NOT_FOUND", "Resource was not found", Map.of());
        }
        LOG.error("Unhandled API failure", throwable);
        return error(Response.Status.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected server error", Map.of());
    }

    private Response error(Response.Status status, String code, String message, Map<String, Object> details) {
        return Response.status(status)
                .entity(new ApiError(code, message, details == null ? Map.of() : details, Instant.now()))
                .build();
    }
}
