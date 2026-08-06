package com.example.c4editor.persistence;

import com.example.c4editor.domain.LinkProvider;
import com.example.c4editor.domain.LinkType;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "external_link")
public class ExternalLinkEntity extends PanacheEntityBase {
    @Id
    @Column(name = "id", nullable = false)
    public UUID id;

    @Column(name = "element_id", nullable = false)
    public UUID elementId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false)
    public LinkProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    public LinkType type;

    @Column(name = "label", nullable = false)
    public String label;

    @Column(name = "url", nullable = false)
    public String url;

    @Column(name = "external_id")
    public String externalId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    public Map<String, Object> metadata = new LinkedHashMap<>();

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
