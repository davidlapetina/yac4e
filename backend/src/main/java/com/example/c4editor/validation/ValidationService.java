package com.example.c4editor.validation;

import com.example.c4editor.api.Dtos.ValidationIssue;
import com.example.c4editor.api.Dtos.ValidationResponse;
import com.example.c4editor.domain.ArchitectureElementType;
import com.example.c4editor.domain.Severity;
import com.example.c4editor.persistence.ArchitectureElementEntity;
import com.example.c4editor.persistence.ArchitectureRelationshipEntity;
import com.example.c4editor.persistence.DiagramViewElementEntity;
import com.example.c4editor.persistence.DiagramViewRelationshipEntity;
import com.example.c4editor.persistence.ExternalLinkEntity;
import com.example.c4editor.persistence.MetadataDefinitionEntity;
import com.example.c4editor.persistence.WorkspaceEntity;
import jakarta.enterprise.context.ApplicationScoped;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class ValidationService {
    public ValidationResponse validate(UUID workspaceId) {
        if (WorkspaceEntity.findById(workspaceId) == null) {
            return new ValidationResponse(List.of(new ValidationIssue(Severity.ERROR, "WORKSPACE_NOT_FOUND", null, null,
                    "Workspace was not found", "Open an existing workspace")), List.of(), List.of());
        }
        List<ValidationIssue> issues = new ArrayList<>();
        List<ArchitectureElementEntity> elements = ArchitectureElementEntity.list("workspaceId", workspaceId);
        List<ArchitectureRelationshipEntity> relationships = ArchitectureRelationshipEntity.list("workspaceId", workspaceId);
        List<MetadataDefinitionEntity> definitions = MetadataDefinitionEntity.list("workspaceId", workspaceId);
        for (ArchitectureElementEntity element : elements) {
            if (blank(element.description)) {
                issues.add(issue(Severity.WARNING, "MISSING_DESCRIPTION", element.id, null, element.name + " has no description", "Add a short purpose statement"));
            }
            if (missingPath(element.metadata, "ownership", "technicalOwner")) {
                issues.add(issue(Severity.WARNING, "MISSING_OWNER", element.id, null, element.name + " has no technical owner", "Set ownership.technicalOwner"));
            }
            if ((element.type == ArchitectureElementType.CONTAINER || element.type == ArchitectureElementType.COMPONENT) && blank(element.technology)) {
                issues.add(issue(Severity.WARNING, "MISSING_TECHNOLOGY", element.id, null, element.name + " has no technology", "Specify the primary technology"));
            }
            if (DiagramViewElementEntity.count("elementId", element.id) == 0) {
                issues.add(issue(Severity.INFO, "ELEMENT_NOT_IN_VIEW", element.id, null, element.name + " is not included in any view", "Add it to a diagram view"));
            }
            if (element.parentElementId != null && ArchitectureElementEntity.findById(element.parentElementId) == null) {
                issues.add(issue(Severity.ERROR, "BROKEN_PARENT", element.id, null, element.name + " references a missing parent", "Choose a valid parent"));
            }
            if (hasParentCycle(element)) {
                issues.add(issue(Severity.ERROR, "CIRCULAR_PARENT", element.id, null, element.name + " has a circular parent chain", "Break the parent loop"));
            }
            for (MetadataDefinitionEntity definition : definitions) {
                if (definition.required && applies(definition, element) && missingCustom(element.metadata, definition.key)) {
                    issues.add(issue(Severity.WARNING, "MISSING_REQUIRED_METADATA", element.id, null,
                            element.name + " is missing required custom metadata: " + definition.label, "Fill in custom." + definition.key));
                }
            }
            if ("PRODUCTION".equalsIgnoreCase(textAt(element.metadata, "lifecycle", "status"))) {
                if (missingPath(element.metadata, "operations", "runbookUrl")) {
                    issues.add(issue(Severity.WARNING, "PRODUCTION_WITHOUT_RUNBOOK", element.id, null, element.name + " is production without a runbook", "Add operations.runbookUrl"));
                }
                if (missingPath(element.metadata, "lifecycle", "reviewedAt")) {
                    issues.add(issue(Severity.WARNING, "PRODUCTION_WITHOUT_REVIEW", element.id, null, element.name + " is production without a review date", "Add lifecycle.reviewedAt"));
                }
            }
        }
        for (ArchitectureRelationshipEntity relationship : relationships) {
            if (DiagramViewRelationshipEntity.count("relationshipId", relationship.id) == 0) {
                issues.add(issue(Severity.INFO, "RELATIONSHIP_NOT_IN_VIEW", null, relationship.id,
                        "A relationship is not included in any view", "Add it to a diagram view"));
            }
        }
        List<ExternalLinkEntity> links = ExternalLinkEntity.listAll();
        for (ExternalLinkEntity link : links) {
            ArchitectureElementEntity element = ArchitectureElementEntity.findById(link.elementId);
            if (element != null && element.workspaceId.equals(workspaceId) && !validUrl(link.url)) {
                issues.add(issue(Severity.ERROR, "INVALID_EXTERNAL_URL", link.elementId, null, link.label + " has an invalid URL", "Use an absolute http(s) URL"));
            }
        }
        return new ValidationResponse(
                issues.stream().filter(i -> i.severity() == Severity.ERROR).toList(),
                issues.stream().filter(i -> i.severity() == Severity.WARNING).toList(),
                issues.stream().filter(i -> i.severity() == Severity.INFO).toList());
    }

    private ValidationIssue issue(Severity severity, String code, UUID elementId, UUID relationshipId, String message, String action) {
        return new ValidationIssue(severity, code, elementId, relationshipId, message, action);
    }

    private boolean applies(MetadataDefinitionEntity definition, ArchitectureElementEntity element) {
        return definition.appliesTo == null || definition.appliesTo.isEmpty() || definition.appliesTo.contains(element.type.name());
    }

    @SuppressWarnings("unchecked")
    private boolean missingCustom(Map<String, Object> metadata, String key) {
        Object custom = metadata == null ? null : metadata.get("custom");
        if (!(custom instanceof Map<?, ?> customMap)) {
            return true;
        }
        Object value = customMap.get(key);
        return value == null || value.toString().isBlank();
    }

    private boolean hasParentCycle(ArchitectureElementEntity element) {
        HashSet<UUID> seen = new HashSet<>();
        UUID cursor = element.parentElementId;
        while (cursor != null) {
            if (!seen.add(cursor) || cursor.equals(element.id)) {
                return true;
            }
            ArchitectureElementEntity parent = ArchitectureElementEntity.findById(cursor);
            cursor = parent == null ? null : parent.parentElementId;
        }
        return false;
    }

    private boolean validUrl(String value) {
        try {
            URI uri = URI.create(value);
            return uri.getScheme() != null && uri.getHost() != null;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private boolean missingPath(Map<String, Object> metadata, String first, String second) {
        return blank(textAt(metadata, first, second));
    }

    private String textAt(Map<String, Object> metadata, String first, String second) {
        if (metadata == null || !(metadata.get(first) instanceof Map<?, ?> map)) {
            return null;
        }
        Object value = map.get(second);
        return value == null ? null : value.toString();
    }
}
