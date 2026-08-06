package com.example.c4editor.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "import_source_mapping", uniqueConstraints = @UniqueConstraint(columnNames = {"workspace_id", "source_format", "source_entity_type", "source_entity_id"}))
public class ImportSourceMappingEntity extends PanacheEntityBase {
    @Id
    @Column(name = "id", nullable = false)
    public UUID id;

    @Column(name = "workspace_id", nullable = false)
    public UUID workspaceId;

    @Column(name = "source_format", nullable = false)
    public String sourceFormat;

    @Column(name = "source_workspace_key")
    public String sourceWorkspaceKey;

    @Column(name = "source_entity_type", nullable = false)
    public String sourceEntityType;

    @Column(name = "source_entity_id", nullable = false)
    public String sourceEntityId;

    @Column(name = "canonical_entity_id", nullable = false)
    public UUID canonicalEntityId;

    @Column(name = "last_imported_at", nullable = false)
    public Instant lastImportedAt;

    @Column(name = "source_checksum")
    public String sourceChecksum;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (lastImportedAt == null) {
            lastImportedAt = Instant.now();
        }
    }
}
