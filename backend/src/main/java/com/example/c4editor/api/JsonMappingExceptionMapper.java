package com.example.c4editor.api;

import com.example.c4editor.api.Dtos.ApiError;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.annotation.Priority;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns request body parsing failures into the standard error contract.
 *
 * Unknown properties are rejected rather than ignored, so a misnamed field fails loudly instead of
 * being silently dropped.
 *
 * Note that Quarkus REST answers an unrecognised-property failure with an empty 400 without
 * consulting any exception mapper, so that case cannot be enriched here; naming the offending field
 * would require replacing the Jackson message body reader. This mapper still covers the failures
 * that do reach it, which otherwise surfaced as a misleading INTERNAL_ERROR.
 */
@Provider
@Priority(1)
public class JsonMappingExceptionMapper implements ExceptionMapper<WebApplicationException> {
    @Override
    public Response toResponse(WebApplicationException exception) {
        Response original = exception.getResponse();
        UnrecognizedPropertyException unknown = find(exception, UnrecognizedPropertyException.class);
        if (unknown != null) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("field", unknown.getPropertyName());
            details.put("path", path(unknown));
            details.put("knownFields", knownFields(unknown));
            return error(Response.Status.BAD_REQUEST, "UNKNOWN_FIELD",
                    "Unknown field '" + unknown.getPropertyName() + "' at " + path(unknown)
                            + ". Check the spelling against the accepted fields.",
                    details);
        }
        JsonMappingException mapping = find(exception, JsonMappingException.class);
        if (mapping != null) {
            return error(Response.Status.BAD_REQUEST, "INVALID_FIELD_VALUE",
                    "Could not read the value at " + path(mapping), Map.of("path", path(mapping)));
        }
        if (find(exception, JsonProcessingException.class) != null) {
            return error(Response.Status.BAD_REQUEST, "MALFORMED_JSON", "Request body is not valid JSON", Map.of());
        }
        // Any other WebApplicationException keeps the response the framework built for it.
        return original;
    }

    private static <T extends Throwable> T find(Throwable throwable, Class<T> type) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return null;
    }

    /** Dotted path to the offending property, e.g. {@code changes[1].element.parentElementReference}. */
    private static String path(JsonMappingException exception) {
        StringBuilder path = new StringBuilder();
        for (JsonMappingException.Reference reference : exception.getPath()) {
            if (reference.getFieldName() != null) {
                if (path.length() > 0) {
                    path.append('.');
                }
                path.append(reference.getFieldName());
            } else if (reference.getIndex() >= 0) {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }
        return path.length() == 0 ? "the request body" : path.toString();
    }

    private static List<String> knownFields(UnrecognizedPropertyException exception) {
        Collection<Object> known = exception.getKnownPropertyIds();
        if (known == null) {
            return List.of();
        }
        return known.stream().map(String::valueOf).sorted().toList();
    }

    private static Response error(Response.Status status, String code, String message, Map<String, Object> details) {
        return Response.status(status).entity(new ApiError(code, message, details, Instant.now())).build();
    }
}
