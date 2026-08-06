package com.example.c4editor.application.auth;

import com.example.c4editor.domain.AuthScope;
import com.example.c4editor.persistence.ApiTokenEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class AuthService {
    @ConfigProperty(name = "yac4e.auth.enabled", defaultValue = "true")
    boolean authEnabled;

    @ConfigProperty(name = "yac4e.auth.basic.username", defaultValue = "admin")
    String basicUsername;

    @ConfigProperty(name = "yac4e.auth.basic.password", defaultValue = "admin")
    String basicPassword;

    @ConfigProperty(name = "yac4e.auth.basic.principal-id", defaultValue = "local-user")
    String basicPrincipalId;

    public boolean authEnabled() {
        return authEnabled;
    }

    public Optional<AuthenticatedPrincipal> authenticateBasic(String authorizationHeader) {
        if (!authEnabled) {
            return Optional.of(developmentPrincipal());
        }
        if (authorizationHeader == null || !authorizationHeader.regionMatches(true, 0, "Basic ", 0, 6)) {
            return Optional.empty();
        }
        String encoded = authorizationHeader.substring(6).trim();
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        int separator = decoded.indexOf(':');
        if (separator < 0) {
            return Optional.empty();
        }
        String username = decoded.substring(0, separator);
        String password = decoded.substring(separator + 1);
        if (constantEquals(username, basicUsername) && constantEquals(password, basicPassword)) {
            return Optional.of(new AuthenticatedPrincipal(basicPrincipalId, AuthCredentialType.BASIC, Set.of(AuthScope.values())));
        }
        return Optional.empty();
    }

    @Transactional
    public Optional<AuthenticatedPrincipal> authenticateApiToken(String token) {
        if (!authEnabled) {
            return Optional.of(developmentPrincipal());
        }
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        ApiTokenEntity entity = ApiTokenEntity.find("tokenHash", sha256(token.trim())).firstResult();
        if (entity == null || !entity.enabled || (entity.expiresAt != null && entity.expiresAt.isBefore(Instant.now()))) {
            return Optional.empty();
        }
        entity.lastUsedAt = Instant.now();
        return Optional.of(new AuthenticatedPrincipal(entity.principalId, AuthCredentialType.API_TOKEN, scopes(entity.scopes)));
    }

    public boolean hasScope(AuthenticatedPrincipal principal, AuthScope scope) {
        return principal != null && principal.hasScope(scope);
    }

    public String tokenHash(String token) {
        return sha256(token);
    }

    public Set<AuthScope> scopes(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(scope -> !scope.isBlank())
                .map(AuthScope::fromValue)
                .collect(Collectors.toUnmodifiableSet());
    }

    public String serializeScopes(Set<AuthScope> scopes) {
        return scopes.stream().map(AuthScope::value).sorted().collect(Collectors.joining(","));
    }

    private AuthenticatedPrincipal developmentPrincipal() {
        return new AuthenticatedPrincipal("local-user", AuthCredentialType.DEVELOPMENT, Set.of(AuthScope.values()));
    }

    private static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                out.append(String.format(Locale.ROOT, "%02x", b));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static boolean constantEquals(String a, String b) {
        return MessageDigest.isEqual(
                (a == null ? "" : a).getBytes(StandardCharsets.UTF_8),
                (b == null ? "" : b).getBytes(StandardCharsets.UTF_8));
    }
}
