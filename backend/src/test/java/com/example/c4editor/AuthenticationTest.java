package com.example.c4editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.c4editor.application.auth.AuthCredentialType;
import com.example.c4editor.application.auth.AuthService;
import com.example.c4editor.domain.AuthScope;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AuthenticationTest {
    @Test
    void basicAuthAcceptsConfiguredDevelopmentAdmin() throws Exception {
        AuthService service = authService(true);
        String credentials = Base64.getEncoder().encodeToString("admin:admin".getBytes(StandardCharsets.UTF_8));

        var principal = service.authenticateBasic("Basic " + credentials);

        assertTrue(principal.isPresent());
        assertEquals("local-user", principal.get().principalId());
        assertEquals(AuthCredentialType.BASIC, principal.get().credentialType());
        assertTrue(principal.get().hasScope(AuthScope.AGENT_READ));
        assertTrue(principal.get().hasScope(AuthScope.MODEL_PROPOSE));
        assertTrue(principal.get().hasScope(AuthScope.MODEL_WRITE));
    }

    @Test
    void basicAuthRejectsWrongPassword() throws Exception {
        AuthService service = authService(true);
        String credentials = Base64.getEncoder().encodeToString("admin:wrong".getBytes(StandardCharsets.UTF_8));

        assertFalse(service.authenticateBasic("Basic " + credentials).isPresent());
    }

    @Test
    void disabledAuthReturnsDevelopmentPrincipal() throws Exception {
        AuthService service = authService(false);

        var principal = service.authenticateBasic(null);

        assertTrue(principal.isPresent());
        assertEquals(AuthCredentialType.DEVELOPMENT, principal.get().credentialType());
        assertTrue(principal.get().hasScope(AuthScope.AGENT_READ));
    }

    @Test
    void scopesUseStableExternalValues() throws Exception {
        AuthService service = authService(true);

        Set<AuthScope> scopes = service.scopes("agent:read, model:propose,model:write");

        assertEquals(Set.of(AuthScope.AGENT_READ, AuthScope.MODEL_PROPOSE, AuthScope.MODEL_WRITE), scopes);
        assertEquals("agent:read,model:propose,model:write", service.serializeScopes(scopes));
    }

    @Test
    void tokenHashIsDeterministicAndDoesNotStoreClearToken() throws Exception {
        AuthService service = authService(true);

        String first = service.tokenHash("yac4e-dev-agent-token");
        String second = service.tokenHash("yac4e-dev-agent-token");

        assertEquals(first, second);
        assertFalse(first.contains("yac4e-dev-agent-token"));
    }

    private AuthService authService(boolean enabled) throws Exception {
        AuthService service = new AuthService();
        set(service, "authEnabled", enabled);
        set(service, "basicUsername", "admin");
        set(service, "basicPassword", "admin");
        set(service, "basicPrincipalId", "local-user");
        return service;
    }

    private void set(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
