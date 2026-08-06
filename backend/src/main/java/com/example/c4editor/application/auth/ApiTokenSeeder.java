package com.example.c4editor.application.auth;

import com.example.c4editor.domain.AuthScope;
import com.example.c4editor.persistence.ApiTokenEntity;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ApiTokenSeeder {
    private static final Logger LOG = Logger.getLogger(ApiTokenSeeder.class);

    @Inject
    AuthService authService;

    @ConfigProperty(name = "yac4e.auth.seed-agent-token.enabled", defaultValue = "true")
    boolean seedEnabled;

    @ConfigProperty(name = "yac4e.auth.seed-agent-token", defaultValue = "yac4e-dev-agent-token")
    String seedToken;

    @ConfigProperty(name = "yac4e.auth.seed-agent-token.name", defaultValue = "Development agent token")
    String seedTokenName;

    @ConfigProperty(name = "yac4e.auth.seed-agent-token.principal-id", defaultValue = "local-agent")
    String seedPrincipalId;

    @Transactional
    void seed(@Observes StartupEvent event) {
        if (!seedEnabled || seedToken == null || seedToken.isBlank()) {
            // Turning seeding off has to revoke what was seeded before, otherwise the previously
            // issued token keeps working and the setting achieves nothing.
            long revoked = ApiTokenEntity.delete("principalId", seedPrincipalId);
            if (revoked > 0) {
                LOG.infof("Seeding disabled: revoked %d seeded API token(s) for principal '%s'", revoked, seedPrincipalId);
            }
            return;
        }
        String tokenHash = authService.tokenHash(seedToken);
        // Rotating the configured token must revoke the previous one. Seeding previously only
        // skipped when the new hash already existed, so every change appended a row and left the
        // superseded token valid indefinitely.
        long revoked = ApiTokenEntity.delete("principalId = ?1 and tokenHash <> ?2", seedPrincipalId, tokenHash);
        if (revoked > 0) {
            LOG.infof("Revoked %d superseded API token(s) for principal '%s'", revoked, seedPrincipalId);
        }
        if (ApiTokenEntity.find("tokenHash", tokenHash).firstResult() != null) {
            return;
        }
        ApiTokenEntity token = new ApiTokenEntity();
        token.principalId = seedPrincipalId;
        token.tokenName = seedTokenName;
        token.tokenHash = tokenHash;
        token.scopes = authService.serializeScopes(Set.of(AuthScope.AGENT_READ, AuthScope.MODEL_PROPOSE, AuthScope.MODEL_WRITE));
        token.enabled = true;
        token.persist();
        LOG.infof("Seeded development API token '%s' for principal '%s'", seedTokenName, seedPrincipalId);
    }
}
