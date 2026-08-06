package com.example.c4editor.persistence;

import com.example.c4editor.domain.RelationshipType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "architecture_relationship")
public class ArchitectureRelationshipEntity extends AuditedPanacheEntity {
    @Column(name = "workspace_id", nullable = false)
    public UUID workspaceId;

    @Column(name = "source_element_id", nullable = false)
    public UUID sourceElementId;

    @Column(name = "target_element_id", nullable = false)
    public UUID targetElementId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    public RelationshipType type;

    @Column(name = "description", nullable = false)
    public String description = "";

    @Column(name = "technology")
    public String technology;

    @Column(name = "protocol")
    public String protocol;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    public Map<String, Object> metadata = new LinkedHashMap<>();
}
