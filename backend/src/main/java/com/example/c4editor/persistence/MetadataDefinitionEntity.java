package com.example.c4editor.persistence;

import com.example.c4editor.domain.MetadataValueType;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "metadata_definition", uniqueConstraints = @UniqueConstraint(columnNames = {"workspace_id", "key"}))
public class MetadataDefinitionEntity extends PanacheEntityBase {
    @Id
    @Column(name = "id", nullable = false)
    public UUID id;

    @Column(name = "workspace_id", nullable = false)
    public UUID workspaceId;

    @Column(name = "key", nullable = false)
    public String key;

    @Column(name = "label", nullable = false)
    public String label;

    @Column(name = "description")
    public String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false)
    public MetadataValueType valueType;

    @Column(name = "required", nullable = false)
    public boolean required;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "applies_to", columnDefinition = "jsonb", nullable = false)
    public List<String> appliesTo = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_values", columnDefinition = "jsonb")
    public List<String> allowedValues;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "default_value", columnDefinition = "jsonb")
    public Object defaultValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_rules", columnDefinition = "jsonb")
    public Map<String, Object> validationRules;

    @Column(name = "display_order", nullable = false)
    public int displayOrder;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}
