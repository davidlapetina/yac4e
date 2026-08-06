package com.example.c4editor.application.auth;

import com.example.c4editor.domain.AuthScope;
import java.util.Set;

public record AuthenticatedPrincipal(
        String principalId,
        AuthCredentialType credentialType,
        Set<AuthScope> scopes
) {
    public boolean hasScope(AuthScope scope) {
        return scopes != null && scopes.contains(scope);
    }
}
