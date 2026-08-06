package com.example.c4editor.api;

import com.example.c4editor.domain.ArchitectureElementType;
import com.example.c4editor.domain.ConflictStrategy;
import com.example.c4editor.domain.DiagramViewType;
import com.example.c4editor.domain.ImportMode;
import com.example.c4editor.domain.LayoutDirection;
import com.example.c4editor.domain.LinkProvider;
import com.example.c4editor.domain.LinkType;
import com.example.c4editor.domain.MetadataValueType;
import com.example.c4editor.domain.RelationshipType;
import com.example.c4editor.domain.Severity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Dtos {
    private Dtos() {
    }

    public record ApiError(String code, String message, Map<String, Object> details, Instant timestamp) {
    }

    public record WorkspaceRequest(@NotBlank String name, String description, Long version) {
    }

    public record WorkspaceResponse(UUID id, String name, String description, long version, Instant createdAt, Instant updatedAt) {
    }

    public record ElementRequest(
            @NotNull ArchitectureElementType type,
            @NotBlank String name,
            String description,
            UUID parentElementId,
            String technology,
            Map<String, Object> metadata,
            Long version) {
    }

    public record ElementResponse(
            UUID id,
            UUID workspaceId,
            ArchitectureElementType type,
            String name,
            String description,
            UUID parentElementId,
            String technology,
            Map<String, Object> metadata,
            long version,
            Instant createdAt,
            Instant updatedAt,
            int linkCount) {
    }

    public record RelationshipRequest(
            @NotNull UUID sourceElementId,
            @NotNull UUID targetElementId,
            @NotNull RelationshipType type,
            String description,
            String technology,
            String protocol,
            Map<String, Object> metadata,
            Long version) {
    }

    public record RelationshipResponse(
            UUID id,
            UUID workspaceId,
            UUID sourceElementId,
            UUID targetElementId,
            RelationshipType type,
            String description,
            String technology,
            String protocol,
            Map<String, Object> metadata,
            long version,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record ViewRequest(
            @NotBlank String name,
            String description,
            @NotNull DiagramViewType type,
            UUID scopeElementId,
            LayoutDirection layoutDirection,
            Map<String, Object> settings,
            Long version) {
    }

    public record ViewResponse(
            UUID id,
            UUID workspaceId,
            String name,
            String description,
            DiagramViewType type,
            UUID scopeElementId,
            LayoutDirection layoutDirection,
            Map<String, Object> settings,
            long version,
            Instant createdAt,
            Instant updatedAt,
            List<ViewElementResponse> elements,
            List<ViewRelationshipResponse> relationships) {
    }

    public record ViewElementRequest(
            @NotNull UUID elementId,
            double x,
            double y,
            @Positive double width,
            @Positive double height,
            boolean locked,
            boolean visible,
            int zIndex,
            Map<String, Object> displaySettings) {
    }

    public record ViewElementResponse(
            UUID id,
            UUID viewId,
            UUID elementId,
            double x,
            double y,
            double width,
            double height,
            boolean locked,
            boolean visible,
            int zIndex,
            Map<String, Object> displaySettings) {
    }

    public record ViewRelationshipRequest(
            @NotNull UUID relationshipId,
            boolean visible,
            Map<String, Object> displaySettings) {
    }

    public record ViewRelationshipResponse(
            UUID id,
            UUID viewId,
            UUID relationshipId,
            boolean visible,
            Map<String, Object> displaySettings) {
    }

    public record LayoutRequest(
            long viewVersion,
            List<ViewElementRequest> elements,
            List<ViewRelationshipRequest> relationships) {
    }

    public record LinkRequest(
            @NotNull LinkProvider provider,
            @NotNull LinkType type,
            @NotBlank String label,
            @NotBlank String url,
            String externalId,
            Map<String, Object> metadata) {
    }

    public record LinkResponse(
            UUID id,
            UUID elementId,
            LinkProvider provider,
            LinkType type,
            String label,
            String url,
            String externalId,
            Map<String, Object> metadata,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record MetadataDefinitionRequest(
            @NotBlank String key,
            @NotBlank String label,
            String description,
            @NotNull MetadataValueType valueType,
            boolean required,
            List<String> appliesTo,
            Object allowedValues,
            Object defaultValue,
            Map<String, Object> validationRules,
            int displayOrder) {
    }

    public record MetadataDefinitionResponse(
            UUID id,
            UUID workspaceId,
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

    public record ValidationIssue(
            Severity severity,
            String code,
            UUID elementId,
            UUID relationshipId,
            String message,
            String recommendedAction) {
    }

    public record ValidationResponse(List<ValidationIssue> errors, List<ValidationIssue> warnings, List<ValidationIssue> info) {
    }

    public record SearchResult(
            String kind,
            UUID id,
            UUID elementId,
            UUID relationshipId,
            UUID viewId,
            String label,
            List<String> matchedFields,
            String snippet) {
    }

    public record SearchResponse(List<SearchResult> results) {
    }

    public record CanonicalModel(
            String schemaVersion,
            WorkspaceRequest workspace,
            List<ElementResponse> elements,
            List<RelationshipResponse> relationships,
            List<ViewResponse> views,
            List<LinkResponse> links,
            List<MetadataDefinitionResponse> metadataDefinitions) {
    }

    public record ImportResponse(UUID workspaceId, int elementCount, int relationshipCount, int viewCount) {
    }

    public record ImportSource(String fileName, byte[] content, String checksum) {
    }

    public record ImportOptions(ImportMode mode, String workspaceName, UUID targetWorkspaceId, ConflictStrategy conflictStrategy) {
    }

    public record ImportMessage(String code, String fileName, Integer line, Integer column, String message) {
    }

    public record ImportSummary(int people, int softwareSystems, int containers, int components, int relationships, int views) {
    }

    public record ImportPreview(boolean valid, WorkspaceRequest workspace, ImportSummary summary, List<ImportMessage> warnings, List<ImportMessage> errors) {
    }

    public record ImportedWorkspace(CanonicalModel model, ImportPreview preview, Map<String, String> sourceEntityIdsByCanonicalId) {
    }
}
