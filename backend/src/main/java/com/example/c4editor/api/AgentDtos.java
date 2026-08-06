package com.example.c4editor.api;

import com.example.c4editor.api.Dtos.ElementResponse;
import com.example.c4editor.api.Dtos.LinkResponse;
import com.example.c4editor.api.Dtos.RelationshipResponse;
import com.example.c4editor.api.Dtos.ValidationIssue;
import com.example.c4editor.api.Dtos.ViewResponse;
import com.example.c4editor.api.Dtos.WorkspaceResponse;
import com.example.c4editor.domain.ArchitectureElementType;
import com.example.c4editor.domain.DependencyDirection;
import com.example.c4editor.domain.LlmContextFormat;
import com.example.c4editor.domain.RelationshipType;
import com.example.c4editor.domain.Severity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AgentDtos {
    private AgentDtos() {
    }

    public record AgentTrace(UUID workspaceId, long workspaceVersion, Instant generatedAt, UUID requestId,
            List<UUID> includedElementIds, List<UUID> includedRelationshipIds, Map<String, Object> appliedFilters, int traversalDepth) {
    }

    public record AgentLimits(int maximumElements, int maximumRelationships) {
    }

    public record AgentElement(UUID id, ArchitectureElementType type, String name, String description, String technology,
            UUID parentElementId, Map<String, Object> metadata, List<String> responsibilities) {
    }

    public record AgentRelationship(UUID id, UUID sourceElementId, UUID targetElementId, RelationshipType type, String description,
            String technology, String protocol, Map<String, Object> metadata) {
    }

    public record GraphPath(List<UUID> elementIds, List<UUID> relationshipIds) {
    }

    public record GraphEnvelope(List<AgentElement> elements, List<AgentRelationship> relationships, List<GraphPath> paths,
            List<LinkResponse> externalLinks, List<ValidationIssue> validationIssues, AgentTrace trace, AgentLimits limits, boolean truncated) {
    }

    public record WorkspaceSummary(WorkspaceResponse workspace, Map<String, Long> counts, List<String> domains, List<String> technologies,
            Map<String, Integer> validation, Instant updatedAt, AgentTrace trace, boolean truncated) {
    }

    public record ElementContext(AgentElement element, List<AgentElement> parents, List<AgentElement> children,
            List<AgentRelationship> incomingRelationships, List<AgentRelationship> outgoingRelationships,
            List<LinkResponse> externalLinks, List<ViewResponse> views, List<ValidationIssue> validationIssues,
            AgentTrace trace, AgentLimits limits, boolean truncated) {
    }

    public record BatchContextRequest(List<UUID> elementIds, BatchInclude include, Integer relationshipDepth,
            Integer maximumElements, Integer maximumRelationships) {
    }

    public record BatchInclude(boolean parents, boolean children, boolean relationships, boolean externalLinks, boolean metadata,
            boolean views, boolean validation) {
    }

    public record ArchitectureQueryRequest(QueryFilters filters, String text, QueryInclude include, Integer page, Integer pageSize) {
    }

    public record QueryFilters(List<ArchitectureElementType> elementTypes, List<String> domains, List<String> technologies,
            List<String> lifecycleStatuses, List<String> criticalities, List<String> tags, Boolean internetExposed,
            Boolean storesPersonalData, Boolean hasOwner, Boolean hasRunbook) {
    }

    public record QueryInclude(boolean metadata, boolean links, boolean relationships, boolean validation) {
    }

    public record ArchitectureQueryResult(AgentElement element, List<String> matchedFields, List<AgentRelationship> relationships,
            List<LinkResponse> externalLinks, List<ValidationIssue> validationIssues) {
    }

    public record ArchitectureQueryResponse(List<ArchitectureQueryResult> results, int page, int pageSize, int total,
            AgentTrace trace, boolean truncated) {
    }

    public record DependencyResponse(UUID rootElementId, DependencyDirection direction, int depth, List<AgentElement> elements,
            List<AgentRelationship> relationships, List<GraphPath> paths, AgentTrace trace, AgentLimits limits, boolean truncated) {
    }

    public record ImpactAnalysisRequest(List<UUID> changedElementIds, List<DependencyDirection> directions,
            List<RelationshipType> relationshipTypes, Integer maximumDepth, boolean includeOwners, boolean includeViews, boolean includeLinks) {
    }

    public record ImpactAnalysisResponse(List<AgentElement> changedElements, List<AgentElement> directlyImpacted,
            List<AgentElement> transitivelyImpacted, List<String> affectedOwners, List<ViewResponse> affectedViews,
            List<LinkResponse> affectedExternalResources, List<GraphPath> paths, List<String> warnings,
            AgentTrace trace, AgentLimits limits, boolean truncated) {
    }

    public record ValidationQueryRequest(List<Severity> severities, List<String> ruleCodes,
            List<ArchitectureElementType> elementTypes, List<String> lifecycleStatuses, Integer page, Integer pageSize) {
    }

    public record ValidationQueryResponse(List<ValidationIssue> results, int page, int pageSize, int total, AgentTrace trace, boolean truncated) {
    }

    public record ExternalResourceResponse(LinkResponse externalLink, AgentElement element, UUID workspaceId,
            ArchitectureElementType elementType, String elementName) {
    }

    public record ExternalResourcesPage(List<ExternalResourceResponse> results, int page, int pageSize, int total, AgentTrace trace, boolean truncated) {
    }

    public record ResolveReferenceRequest(String reference) {
    }

    public record ResolveReferenceMatch(LinkResponse externalLink, AgentElement element, double confidence, String matchType) {
    }

    public record ResolveReferenceResponse(List<ResolveReferenceMatch> matches, AgentTrace trace, boolean truncated) {
    }

    public record LlmContextRequest(List<UUID> elementIds, String query, Integer relationshipDepth, boolean includeMetadata,
            boolean includeLinks, boolean includeValidation, Integer maximumCharacters, LlmContextFormat format) {
    }

    public record LlmContextResponse(LlmContextFormat format, String content, List<UUID> includedElementIds,
            List<UUID> includedRelationshipIds, boolean truncated, AgentTrace trace) {
    }
}
