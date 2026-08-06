package com.example.c4editor.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "diagram_view_relationship", uniqueConstraints = @UniqueConstraint(columnNames = {"view_id", "relationship_id"}))
public class DiagramViewRelationshipEntity extends PanacheEntityBase {
    @Id
    @Column(name = "id", nullable = false)
    public UUID id;

    @Column(name = "view_id", nullable = false)
    public UUID viewId;

    @Column(name = "relationship_id", nullable = false)
    public UUID relationshipId;

    @Column(name = "visible", nullable = false)
    public boolean visible = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "display_settings", columnDefinition = "jsonb", nullable = false)
    public Map<String, Object> displaySettings = new LinkedHashMap<>();

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}
