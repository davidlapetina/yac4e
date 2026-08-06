package com.example.c4editor.persistence;

import com.example.c4editor.domain.AgentProposalChangeAction;
import com.example.c4editor.domain.AgentProposalChangeStatus;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "agent_proposal_change")
public class AgentProposalChangeEntity extends PanacheEntityBase {
    @Id
    @Column(name = "id", nullable = false)
    public UUID id;

    @Column(name = "proposal_id", nullable = false)
    public UUID proposalId;

    @Column(name = "sequence_number", nullable = false)
    public int sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    public AgentProposalChangeAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    public AgentProposalChangeStatus status = AgentProposalChangeStatus.PENDING;

    @Column(name = "client_reference")
    public String clientReference;

    @Column(name = "target_entity_type")
    public String targetEntityType;

    @Column(name = "target_entity_id")
    public UUID targetEntityId;

    @Column(name = "result_entity_id")
    public UUID resultEntityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    public Map<String, Object> payload = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence", columnDefinition = "jsonb", nullable = false)
    public List<Map<String, Object>> evidence = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation", columnDefinition = "jsonb", nullable = false)
    public Map<String, Object> validation = new LinkedHashMap<>();

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
