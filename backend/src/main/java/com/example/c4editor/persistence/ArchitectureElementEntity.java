package com.example.c4editor.persistence;

import com.example.c4editor.domain.ArchitectureElementType;
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
@Table(name = "architecture_element")
public class ArchitectureElementEntity extends AuditedPanacheEntity {
    @Column(name = "workspace_id", nullable = false)
    public UUID workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    public ArchitectureElementType type;

    @Column(name = "name", nullable = false)
    public String name;

    @Column(name = "description", nullable = false)
    public String description = "";

    @Column(name = "parent_element_id")
    public UUID parentElementId;

    @Column(name = "technology")
    public String technology;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    public Map<String, Object> metadata = new LinkedHashMap<>();
}
