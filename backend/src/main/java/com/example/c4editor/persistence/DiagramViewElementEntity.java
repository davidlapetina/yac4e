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
@Table(name = "diagram_view_element", uniqueConstraints = @UniqueConstraint(columnNames = {"view_id", "element_id"}))
public class DiagramViewElementEntity extends PanacheEntityBase {
    @Id
    @Column(name = "id", nullable = false)
    public UUID id;

    @Column(name = "view_id", nullable = false)
    public UUID viewId;

    @Column(name = "element_id", nullable = false)
    public UUID elementId;

    @Column(name = "x", nullable = false)
    public double x;

    @Column(name = "y", nullable = false)
    public double y;

    @Column(name = "width", nullable = false)
    public double width = 260;

    @Column(name = "height", nullable = false)
    public double height = 150;

    @Column(name = "locked", nullable = false)
    public boolean locked;

    @Column(name = "visible", nullable = false)
    public boolean visible = true;

    @Column(name = "z_index", nullable = false)
    public int zIndex;

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
