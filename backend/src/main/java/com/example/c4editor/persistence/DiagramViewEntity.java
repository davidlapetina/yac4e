package com.example.c4editor.persistence;

import com.example.c4editor.domain.DiagramViewType;
import com.example.c4editor.domain.LayoutDirection;
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
@Table(name = "diagram_view")
public class DiagramViewEntity extends AuditedPanacheEntity {
    @Column(name = "workspace_id", nullable = false)
    public UUID workspaceId;

    @Column(name = "name", nullable = false)
    public String name;

    @Column(name = "description", nullable = false)
    public String description = "";

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    public DiagramViewType type;

    @Column(name = "scope_element_id")
    public UUID scopeElementId;

    @Enumerated(EnumType.STRING)
    @Column(name = "layout_direction", nullable = false)
    public LayoutDirection layoutDirection = LayoutDirection.LEFT_TO_RIGHT;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "settings", columnDefinition = "jsonb", nullable = false)
    public Map<String, Object> settings = new LinkedHashMap<>();
}
