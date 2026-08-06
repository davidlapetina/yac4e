package com.example.c4editor.persistence;

import com.example.c4editor.domain.AgentProposalStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "agent_proposal")
public class AgentProposalEntity extends AuditedPanacheEntity {
    @Column(name = "workspace_id", nullable = false)
    public UUID workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    public AgentProposalStatus status = AgentProposalStatus.PENDING;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source", columnDefinition = "jsonb", nullable = false)
    public Map<String, Object> source = new LinkedHashMap<>();

    @Column(name = "summary", nullable = false)
    public String summary = "";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation", columnDefinition = "jsonb", nullable = false)
    public Map<String, Object> validation = new LinkedHashMap<>();

    @Column(name = "applied_at")
    public Instant appliedAt;

    @Column(name = "rejected_at")
    public Instant rejectedAt;
}
