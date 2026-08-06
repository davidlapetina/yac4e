package com.example.c4editor.api.auth;

import com.example.c4editor.api.Dtos.ApiError;
import com.example.c4editor.application.WorkspaceAccessService;
import com.example.c4editor.application.auth.AuthRequestContext;
import com.example.c4editor.application.auth.AuthService;
import com.example.c4editor.application.auth.AuthenticatedPrincipal;
import com.example.c4editor.domain.AuthScope;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Provider
@Priority(Priorities.AUTHENTICATION)
public class ApiAuthenticationFilter implements ContainerRequestFilter, ContainerResponseFilter {
    private static final Pattern WORKSPACE_PATH = Pattern.compile("(?:^|/)workspaces/([0-9a-fA-F-]{36})(?:/|$)");

    @Inject
    AuthService authService;

    @Inject
    AuthRequestContext authContext;

    @Inject
    WorkspaceAccessService workspaceAccess;

    @Override
    public void filter(ContainerRequestContext request) {
        authContext.clear();
        AuthenticatedPrincipal principal = authenticate(request)
                .orElseGet(authContext::developmentPrincipal);
        if (authService.authEnabled() && principal.credentialType() == com.example.c4editor.application.auth.AuthCredentialType.DEVELOPMENT) {
            abort(request, Response.Status.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required");
            return;
        }
        String path = request.getUriInfo().getPath();
        if (path.startsWith("agent/")) {
            AuthScope required = requiredAgentScope(path, request.getMethod());
            if (!authService.hasScope(principal, required)) {
                abort(request, Response.Status.FORBIDDEN, "INSUFFICIENT_SCOPE", "The credential does not include scope " + required.value());
                return;
            }
        } else if (path.startsWith("proposals/") && !authService.hasScope(principal, AuthScope.MODEL_PROPOSE)) {
            abort(request, Response.Status.FORBIDDEN, "INSUFFICIENT_SCOPE", "The credential does not include scope model:propose");
            return;
        }
        Optional<UUID> workspaceId = workspaceId(path);
        if (workspaceId.isPresent() && !canAccess(principal, workspaceId.get(), request.getMethod(), path)) {
            abort(request, Response.Status.NOT_FOUND, "WORKSPACE_NOT_FOUND", "Workspace was not found or is not accessible");
            return;
        }
        authContext.set(principal);
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        authContext.clear();
    }

    private Optional<AuthenticatedPrincipal> authenticate(ContainerRequestContext request) {
        String apiToken = apiToken(request);
        if (apiToken != null) {
            return authService.authenticateApiToken(apiToken);
        }
        return authService.authenticateBasic(request.getHeaderString(HttpHeaders.AUTHORIZATION));
    }

    private static String apiToken(ContainerRequestContext request) {
        String header = request.getHeaderString("X-API-Token");
        if (header != null && !header.isBlank()) {
            return header;
        }
        String authorization = request.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authorization.substring(7).trim();
        }
        return null;
    }

    private boolean canAccess(AuthenticatedPrincipal principal, UUID workspaceId, String method, String path) {
        if (path.startsWith("agent/")) {
            if (path.contains("/proposals") && !HttpMethod.GET.equals(method)) {
                return workspaceAccess.canProposeWorkspace(principal.principalId(), workspaceId);
            }
            return workspaceAccess.canReadWorkspace(principal.principalId(), workspaceId);
        }
        if (path.startsWith("proposals/")) {
            return workspaceAccess.canProposeWorkspace(principal.principalId(), workspaceId);
        }
        return HttpMethod.GET.equals(method)
                ? workspaceAccess.canReadWorkspace(principal.principalId(), workspaceId)
                : workspaceAccess.canWriteWorkspace(principal.principalId(), workspaceId);
    }

    private static AuthScope requiredAgentScope(String path, String method) {
        if (!path.contains("/proposals")) {
            return AuthScope.AGENT_READ;
        }
        if (HttpMethod.GET.equals(method)) {
            return AuthScope.AGENT_READ;
        }
        if (path.endsWith("/apply") || path.endsWith("/reject")) {
            return AuthScope.MODEL_WRITE;
        }
        return AuthScope.MODEL_PROPOSE;
    }

    private static Optional<UUID> workspaceId(String path) {
        Matcher matcher = WORKSPACE_PATH.matcher(path);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(UUID.fromString(matcher.group(1)));
    }

    private static void abort(ContainerRequestContext request, Response.Status status, String code, String message) {
        request.abortWith(Response.status(status)
                .entity(new ApiError(code, message, Map.of(), Instant.now()))
                .build());
    }
}
