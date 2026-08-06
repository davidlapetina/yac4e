package com.example.c4editor.application.auth;

import com.example.c4editor.domain.AuthScope;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class AuthRequestContext {
    private static final String DEVELOPMENT_PRINCIPAL = "local-user";
    private final ThreadLocal<AuthenticatedPrincipal> current = new ThreadLocal<>();

    public void set(AuthenticatedPrincipal principal) {
        current.set(principal);
    }

    public Optional<AuthenticatedPrincipal> current() {
        return Optional.ofNullable(current.get());
    }

    public String principalIdOrDevelopment() {
        return current().map(AuthenticatedPrincipal::principalId).orElse(DEVELOPMENT_PRINCIPAL);
    }

    public void clear() {
        current.remove();
    }

    public AuthenticatedPrincipal developmentPrincipal() {
        return new AuthenticatedPrincipal(DEVELOPMENT_PRINCIPAL, AuthCredentialType.DEVELOPMENT, Set.of(AuthScope.values()));
    }
}
