package com.example.c4editor.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "api_token")
public class ApiTokenEntity extends PanacheEntityBase {
    @Id
    @Column(name = "id", nullable = false)
    public UUID id;

    @Column(name = "principal_id", nullable = false)
    public String principalId;

    @Column(name = "token_name", nullable = false)
    public String tokenName;

    @Column(name = "token_hash", nullable = false, unique = true)
    public String tokenHash;

    @Column(name = "scopes", nullable = false)
    public String scopes;

    @Column(name = "enabled", nullable = false)
    public boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "expires_at")
    public Instant expiresAt;

    @Column(name = "last_used_at")
    public Instant lastUsedAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
