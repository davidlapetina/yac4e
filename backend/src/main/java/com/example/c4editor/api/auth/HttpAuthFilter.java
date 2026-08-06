package com.example.c4editor.api.auth;

import com.example.c4editor.application.auth.AuthService;
import io.quarkus.vertx.http.runtime.filters.Filters;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.time.Instant;

@ApplicationScoped
public class HttpAuthFilter {
    private static final String REALM = "YaC4e";

    @Inject
    AuthService authService;

    void init(@Observes Filters filters) {
        filters.register(this::filter, 1_000);
    }

    void filter(RoutingContext context) {
        if (!authService.authEnabled() || "OPTIONS".equals(context.request().method().name()) || isQuarkusInternal(context.normalizedPath())) {
            context.next();
            return;
        }
        boolean apiTokenCandidate = hasApiToken(context);
        if (isAgentOrProposalPath(context.normalizedPath()) && apiTokenCandidate) {
            context.next();
            return;
        }
        if (authService.authenticateBasic(context.request().getHeader("Authorization")).isPresent()) {
            context.next();
            return;
        }
        challenge(context);
    }

    private static boolean hasApiToken(RoutingContext context) {
        String authorization = context.request().getHeader("Authorization");
        return context.request().getHeader("X-API-Token") != null
                || (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7));
    }

    private static boolean isAgentOrProposalPath(String path) {
        return path.startsWith("/api/agent") || path.startsWith("/api/proposals");
    }

    private static boolean isQuarkusInternal(String path) {
        return path.startsWith("/q/");
    }

    private static void challenge(RoutingContext context) {
        context.response()
                .setStatusCode(401)
                .putHeader("WWW-Authenticate", "Basic realm=\"" + REALM + "\"")
                .putHeader("Cache-Control", "no-store");
        if (context.normalizedPath().startsWith("/api/")) {
            context.response().putHeader("Content-Type", "application/json")
                    .end("{\"code\":\"UNAUTHENTICATED\",\"message\":\"Authentication is required\",\"details\":{},\"timestamp\":\""
                            + Instant.now() + "\"}");
        } else {
            context.response().end("Authentication is required");
        }
    }
}
