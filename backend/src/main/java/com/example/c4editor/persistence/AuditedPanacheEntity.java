package com.example.c4editor.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@MappedSuperclass
public abstract class AuditedPanacheEntity extends PanacheEntityBase {
    @Id
    @Column(name = "id", nullable = false)
    public UUID id;

    @Version
    @Column(name = "version", nullable = false)
    public long version;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @Column(name = "created_by", nullable = false)
    public String createdBy = "local-user";

    @Column(name = "updated_by", nullable = false)
    public String updatedBy = "local-user";

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (createdBy == null || createdBy.isBlank()) {
            createdBy = "local-user";
        }
        updatedBy = "local-user";
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
        updatedBy = "local-user";
    }
}
