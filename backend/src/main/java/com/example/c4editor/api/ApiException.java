package com.example.c4editor.api;

import jakarta.ws.rs.core.Response;
import java.util.Map;

public class ApiException extends RuntimeException {
    public final String code;
    public final Response.Status status;
    public final Map<String, Object> details;

    public ApiException(Response.Status status, String code, String message) {
        this(status, code, message, Map.of());
    }

    public ApiException(Response.Status status, String code, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }
}
