package com.example.c4editor.api;

import com.example.c4editor.domain.AgentProposalChangeAction;
import com.example.c4editor.domain.AgentProposalChangeStatus;
import com.example.c4editor.domain.AgentProposalStatus;
import com.example.c4editor.domain.ArchitectureElementType;
import com.example.c4editor.domain.DiagramViewType;
import com.example.c4editor.domain.LayoutDirection;
import com.example.c4editor.domain.LinkProvider;
import com.example.c4editor.domain.LinkType;
import com.example.c4editor.domain.MetadataValueType;
import com.example.c4editor.domain.RelationshipType;
import com.example.c4editor.domain.Severity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AgentProposalDtos {
    private AgentProposalDtos() {
    }

    public record AgentProposalRequest(
            Map<String, Object> source,
            String summary,
            List<AgentProposalChangeRequest> changes) {
    }

    public record AgentProposalChangeRequest(
            AgentProposalChangeAction action,
            String clientReference,
            UUID targetEntityId,
            ElementDraft element,
            RelationshipDraft relationship,
            LinkDraft link,
            MetadataDefinitionDraft metadataDefinition,
            ViewDraft view,
            List<Map<String, Object>> evidence) {
    }

    public record ElementDraft(
            ArchitectureElementType type,
            String name,
            String description,
            UUID parentElementId,
            String parentReference,
            String technology,
            Map<String, Object> metadata) {
    }

    public record RelationshipDraft(
            UUID sourceElementId,
            String sourceReference,
            UUID targetElementId,
            String targetReference,
            RelationshipType type,
            String description,
            String technology,
            String protocol,
            Map<String, Object> metadata) {
    }

    public record LinkDraft(
            UUID elementId,
            String elementReference,
            LinkProvider provider,
            LinkType type,
            String label,
            String url,
            String externalId,
            Map<String, Object> metadata) {
    }

    public record MetadataDefinitionDraft(
            String key,
            String label,
            String description,
            MetadataValueType valueType,
            boolean required,
            List<String> appliesTo,
            Object allowedValues,
            Object defaultValue,
            Map<String, Object> validationRules,
            int displayOrder) {
    }

    public record ViewDraft(
            String name,
            String description,
            DiagramViewType type,
            UUID scopeElementId,
            String scopeReference,
            LayoutDirection layoutDirection,
            Map<String, Object> settings) {
    }

    public record ProposalValidationIssue(
            Severity severity,
            String code,
            Integer sequenceNumber,
            String clientReference,
            String message,
            Map<String, Object> details) {
    }

    public record ProposalValidationResult(
            boolean valid,
            List<ProposalValidationIssue> warnings,
            List<ProposalValidationIssue> errors,
            Map<String, Integer> summary) {
    }

    public record AgentProposalSummaryResponse(
            UUID id,
            UUID workspaceId,
            AgentProposalStatus status,
            String summary,
            Map<String, Object> source,
            ProposalValidationResult validation,
            Instant createdAt,
            Instant updatedAt,
            Instant appliedAt,
            Instant rejectedAt,
            int changeCount) {
    }

    public record AgentProposalResponse(
            UUID id,
            UUID workspaceId,
            AgentProposalStatus status,
            String summary,
            Map<String, Object> source,
            ProposalValidationResult validation,
            Instant createdAt,
            Instant updatedAt,
            Instant appliedAt,
            Instant rejectedAt,
            List<AgentProposalChangeResponse> changes) {
    }

    public record AgentProposalChangeResponse(
            UUID id,
            int sequenceNumber,
            AgentProposalChangeAction action,
            AgentProposalChangeStatus status,
            String clientReference,
            String targetEntityType,
            UUID targetEntityId,
            UUID resultEntityId,
            Map<String, Object> payload,
            List<Map<String, Object>> evidence,
            ProposalValidationResult validation) {
    }
}
