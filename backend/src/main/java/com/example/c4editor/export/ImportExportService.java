package com.example.c4editor.export;

import com.example.c4editor.api.ApiException;
import com.example.c4editor.api.Dtos.CanonicalModel;
import com.example.c4editor.api.Dtos.ElementResponse;
import com.example.c4editor.api.Dtos.ImportResponse;
import com.example.c4editor.api.Dtos.LinkResponse;
import com.example.c4editor.api.Dtos.MetadataDefinitionResponse;
import com.example.c4editor.api.Dtos.RelationshipResponse;
import com.example.c4editor.api.Dtos.ViewElementResponse;
import com.example.c4editor.api.Dtos.ViewRelationshipResponse;
import com.example.c4editor.api.Dtos.ViewResponse;
import com.example.c4editor.api.Dtos.WorkspaceRequest;
import com.example.c4editor.application.C4ModelService;
import com.example.c4editor.domain.ArchitectureElementType;
import com.example.c4editor.persistence.ArchitectureElementEntity;
import com.example.c4editor.persistence.ArchitectureRelationshipEntity;
import com.example.c4editor.persistence.DiagramViewElementEntity;
import com.example.c4editor.persistence.DiagramViewEntity;
import com.example.c4editor.persistence.DiagramViewRelationshipEntity;
import com.example.c4editor.persistence.ExternalLinkEntity;
import com.example.c4editor.persistence.MetadataDefinitionEntity;
import com.example.c4editor.persistence.WorkspaceEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class ImportExportService {
    @Inject
    ObjectMapper objectMapper;

    @Inject
    C4ModelService modelService;

    private final YAMLMapper yamlMapper = new YAMLMapper();

    public String exportJson(UUID workspaceId) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(exportModel(workspaceId));
        } catch (Exception ex) {
            throw new ApiException(Response.Status.INTERNAL_SERVER_ERROR, "EXPORT_FAILED", "JSON export failed");
        }
    }

    public String exportYaml(UUID workspaceId) {
        try {
            return yamlMapper.writeValueAsString(exportModel(workspaceId));
        } catch (Exception ex) {
            throw new ApiException(Response.Status.INTERNAL_SERVER_ERROR, "EXPORT_FAILED", "YAML export failed");
        }
    }

    public CanonicalModel exportModel(UUID workspaceId) {
        var workspace = modelService.getWorkspace(workspaceId);
        List<ElementResponse> elements = modelService.listElements(workspaceId, null, null, null, null, null, null);
        List<RelationshipResponse> relationships = modelService.listRelationships(workspaceId);
        List<ViewResponse> views = modelService.listViews(workspaceId);
        List<LinkResponse> links = elements.stream().flatMap(e -> modelService.listLinks(workspaceId, e.id()).stream()).toList();
        List<MetadataDefinitionResponse> metadataDefinitions = modelService.listMetadataDefinitions(workspaceId);
        return new CanonicalModel("1.0", new WorkspaceRequest(workspace.name(), workspace.description(), null),
                elements, relationships, views, links, metadataDefinitions);
    }

    @Transactional
    public ImportResponse importModel(String payload) {
        CanonicalModel model = parse(payload);
        validate(model);
        WorkspaceEntity workspace = new WorkspaceEntity();
        workspace.name = model.workspace().name();
        workspace.description = model.workspace().description() == null ? "" : model.workspace().description();
        workspace.persist();

        Map<UUID, UUID> elementIds = new HashMap<>();
        for (ElementResponse source : safe(model.elements())) {
            ArchitectureElementEntity entity = new ArchitectureElementEntity();
            entity.workspaceId = workspace.id;
            entity.type = source.type();
            entity.name = source.name();
            entity.description = source.description() == null ? "" : source.description();
            entity.technology = source.technology();
            entity.metadata = map(source.metadata());
            entity.persist();
            elementIds.put(source.id(), entity.id);
        }
        for (ElementResponse source : safe(model.elements())) {
            if (source.parentElementId() != null) {
                ArchitectureElementEntity entity = ArchitectureElementEntity.findById(elementIds.get(source.id()));
                entity.parentElementId = elementIds.get(source.parentElementId());
            }
        }

        Map<UUID, UUID> relationshipIds = new HashMap<>();
        for (RelationshipResponse source : safe(model.relationships())) {
            ArchitectureRelationshipEntity entity = new ArchitectureRelationshipEntity();
            entity.workspaceId = workspace.id;
            entity.sourceElementId = elementIds.get(source.sourceElementId());
            entity.targetElementId = elementIds.get(source.targetElementId());
            entity.type = source.type();
            entity.description = source.description() == null ? "" : source.description();
            entity.technology = source.technology();
            entity.protocol = source.protocol();
            entity.metadata = map(source.metadata());
            entity.persist();
            relationshipIds.put(source.id(), entity.id);
        }

        Map<UUID, UUID> viewIds = new HashMap<>();
        for (ViewResponse source : safe(model.views())) {
            DiagramViewEntity entity = new DiagramViewEntity();
            entity.workspaceId = workspace.id;
            entity.name = source.name();
            entity.description = source.description() == null ? "" : source.description();
            entity.type = source.type();
            entity.scopeElementId = source.scopeElementId() == null ? null : elementIds.get(source.scopeElementId());
            entity.layoutDirection = source.layoutDirection();
            entity.settings = map(source.settings());
            entity.persist();
            viewIds.put(source.id(), entity.id);
        }
        for (ViewResponse source : safe(model.views())) {
            UUID newViewId = viewIds.get(source.id());
            for (ViewElementResponse member : safe(source.elements())) {
                DiagramViewElementEntity entity = new DiagramViewElementEntity();
                entity.viewId = newViewId;
                entity.elementId = elementIds.get(member.elementId());
                entity.x = member.x();
                entity.y = member.y();
                entity.width = member.width();
                entity.height = member.height();
                entity.locked = member.locked();
                entity.visible = member.visible();
                entity.zIndex = member.zIndex();
                entity.displaySettings = map(member.displaySettings());
                entity.persist();
            }
            for (ViewRelationshipResponse member : safe(source.relationships())) {
                DiagramViewRelationshipEntity entity = new DiagramViewRelationshipEntity();
                entity.viewId = newViewId;
                entity.relationshipId = relationshipIds.get(member.relationshipId());
                entity.visible = member.visible();
                entity.displaySettings = map(member.displaySettings());
                entity.persist();
            }
        }

        for (LinkResponse source : safe(model.links())) {
            ExternalLinkEntity entity = new ExternalLinkEntity();
            entity.elementId = elementIds.get(source.elementId());
            entity.provider = source.provider();
            entity.type = source.type();
            entity.label = source.label();
            entity.url = source.url();
            entity.externalId = source.externalId();
            entity.metadata = map(source.metadata());
            entity.persist();
        }

        for (MetadataDefinitionResponse source : safe(model.metadataDefinitions())) {
            MetadataDefinitionEntity entity = new MetadataDefinitionEntity();
            entity.workspaceId = workspace.id;
            entity.key = source.key();
            entity.label = source.label();
            entity.description = source.description();
            entity.valueType = source.valueType();
            entity.required = source.required();
            entity.appliesTo = source.appliesTo() == null ? List.of() : source.appliesTo();
            entity.allowedValues = stringList(source.allowedValues());
            entity.defaultValue = source.defaultValue();
            entity.validationRules = source.validationRules();
            entity.displayOrder = source.displayOrder();
            entity.persist();
        }

        return new ImportResponse(workspace.id, safe(model.elements()).size(), safe(model.relationships()).size(), safe(model.views()).size());
    }

    private CanonicalModel parse(String payload) {
        try {
            return objectMapper.readValue(payload, CanonicalModel.class);
        } catch (Exception ignored) {
            try {
                return yamlMapper.readValue(payload, CanonicalModel.class);
            } catch (Exception ex) {
                throw new ApiException(Response.Status.BAD_REQUEST, "MALFORMED_IMPORT", "Model import payload is not valid JSON or YAML");
            }
        }
    }

    private void validate(CanonicalModel model) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (model == null || model.workspace() == null || model.workspace().name() == null || model.workspace().name().isBlank()) {
            details.put("workspace", "Workspace name is required");
        }
        ensureUnique("elements", safe(model.elements()).stream().map(ElementResponse::id).toList(), details);
        ensureUnique("relationships", safe(model.relationships()).stream().map(RelationshipResponse::id).toList(), details);
        ensureUnique("views", safe(model.views()).stream().map(ViewResponse::id).toList(), details);
        Set<UUID> elementIds = new HashSet<>(safe(model.elements()).stream().map(ElementResponse::id).toList());
        Set<UUID> relationshipIds = new HashSet<>(safe(model.relationships()).stream().map(RelationshipResponse::id).toList());
        for (ElementResponse element : safe(model.elements())) {
            if (element.id() == null || element.type() == null || element.name() == null || element.name().isBlank()) {
                details.put("element", "Each element requires id, type and name");
            }
            if (element.parentElementId() != null && !elementIds.contains(element.parentElementId())) {
                details.put("parent:" + element.id(), "Parent element does not exist in import payload");
            }
        }
        validateHierarchyTypes(safe(model.elements()), details);
        for (RelationshipResponse relationship : safe(model.relationships())) {
            if (relationship.id() == null || relationship.sourceElementId() == null || relationship.targetElementId() == null || relationship.type() == null) {
                details.put("relationship", "Each relationship requires id, source, target and type");
            } else if (relationship.sourceElementId().equals(relationship.targetElementId())) {
                details.put("relationship:" + relationship.id(), "Self relationships are rejected");
            } else if (!elementIds.contains(relationship.sourceElementId()) || !elementIds.contains(relationship.targetElementId())) {
                details.put("relationship:" + relationship.id(), "Relationship references an unknown element");
            }
        }
        for (ViewResponse view : safe(model.views())) {
            if (view.id() == null || view.type() == null || view.name() == null || view.name().isBlank()) {
                details.put("view", "Each view requires id, type and name");
            }
            for (ViewElementResponse member : safe(view.elements())) {
                if (!elementIds.contains(member.elementId())) {
                    details.put("viewElement:" + member.elementId(), "View element references an unknown element");
                }
            }
            for (ViewRelationshipResponse member : safe(view.relationships())) {
                if (!relationshipIds.contains(member.relationshipId())) {
                    details.put("viewRelationship:" + member.relationshipId(), "View relationship references an unknown relationship");
                }
            }
        }
        for (LinkResponse link : safe(model.links())) {
            if (!elementIds.contains(link.elementId())) {
                details.put("link:" + link.id(), "External link references an unknown element");
            }
        }
        if (!details.isEmpty()) {
            throw new ApiException(Response.Status.BAD_REQUEST, "INVALID_IMPORT_MODEL", "Model import failed validation", details);
        }
        rejectParentCycles(safe(model.elements()));
    }

    private void validateHierarchyTypes(List<ElementResponse> elements, Map<String, Object> details) {
        Map<UUID, ElementResponse> elementById = new HashMap<>();
        for (ElementResponse element : elements) {
            if (element.id() != null) {
                elementById.put(element.id(), element);
            }
        }
        for (ElementResponse element : elements) {
            if (element.type() == null) {
                continue;
            }
            ElementResponse parent = element.parentElementId() == null ? null : elementById.get(element.parentElementId());
            boolean allowed = switch (element.type()) {
                case PERSON, SOFTWARE_SYSTEM, EXTERNAL_SYSTEM -> parent == null;
                case CONTAINER -> parent != null && parent.type() == ArchitectureElementType.SOFTWARE_SYSTEM;
                case COMPONENT -> parent != null && parent.type() == ArchitectureElementType.CONTAINER;
                case DATA_STORE -> parent != null && (parent.type() == ArchitectureElementType.SOFTWARE_SYSTEM || parent.type() == ArchitectureElementType.CONTAINER);
            };
            if (!allowed) {
                details.put("hierarchy:" + element.id(), "Parent element type is not valid for " + element.type());
            }
        }
    }

    private void rejectParentCycles(List<ElementResponse> elements) {
        Map<UUID, UUID> parents = new HashMap<>();
        for (ElementResponse element : elements) {
            parents.put(element.id(), element.parentElementId());
        }
        for (UUID id : parents.keySet()) {
            Set<UUID> seen = new HashSet<>();
            UUID cursor = parents.get(id);
            while (cursor != null) {
                if (!seen.add(cursor) || cursor.equals(id)) {
                    throw new ApiException(Response.Status.BAD_REQUEST, "CIRCULAR_PARENT", "Import contains a circular parent hierarchy");
                }
                cursor = parents.get(cursor);
            }
        }
    }

    private void ensureUnique(String key, List<UUID> values, Map<String, Object> details) {
        Set<UUID> seen = new HashSet<>();
        for (UUID value : values) {
            if (value != null && !seen.add(value)) {
                details.put(key, "Duplicate id: " + value);
            }
        }
    }

    private static <T> List<T> safe(List<T> value) {
        return value == null ? List.of() : value;
    }

    private static Map<String, Object> map(Map<String, Object> value) {
        return value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
    }

    private static List<String> stringList(Object source) {
        if (source == null) {
            return null;
        }
        if (source instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        if (source instanceof String text) {
            return text.lines().flatMap(line -> List.of(line.split(",")).stream()).map(String::trim).filter(value -> !value.isBlank()).toList();
        }
        return List.of(String.valueOf(source));
    }
}
