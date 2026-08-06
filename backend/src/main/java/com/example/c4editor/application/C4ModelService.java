package com.example.c4editor.application;

import com.example.c4editor.api.ApiException;
import com.example.c4editor.api.Dtos.ElementRequest;
import com.example.c4editor.api.Dtos.ElementResponse;
import com.example.c4editor.api.Dtos.LayoutRequest;
import com.example.c4editor.api.Dtos.LinkRequest;
import com.example.c4editor.api.Dtos.LinkResponse;
import com.example.c4editor.api.Dtos.MetadataDefinitionRequest;
import com.example.c4editor.api.Dtos.MetadataDefinitionResponse;
import com.example.c4editor.api.Dtos.RelationshipRequest;
import com.example.c4editor.api.Dtos.RelationshipResponse;
import com.example.c4editor.api.Dtos.ViewElementRequest;
import com.example.c4editor.api.Dtos.ViewElementResponse;
import com.example.c4editor.api.Dtos.ViewRelationshipRequest;
import com.example.c4editor.api.Dtos.ViewRelationshipResponse;
import com.example.c4editor.api.Dtos.ViewRequest;
import com.example.c4editor.api.Dtos.ViewResponse;
import com.example.c4editor.api.Dtos.WorkspaceRequest;
import com.example.c4editor.api.Dtos.WorkspaceResponse;
import com.example.c4editor.domain.ArchitectureElementType;
import com.example.c4editor.domain.LayoutDirection;
import com.example.c4editor.persistence.ArchitectureElementEntity;
import com.example.c4editor.persistence.ArchitectureRelationshipEntity;
import com.example.c4editor.persistence.DiagramViewElementEntity;
import com.example.c4editor.persistence.DiagramViewEntity;
import com.example.c4editor.persistence.DiagramViewRelationshipEntity;
import com.example.c4editor.persistence.ExternalLinkEntity;
import com.example.c4editor.persistence.MetadataDefinitionEntity;
import com.example.c4editor.persistence.WorkspaceEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@ApplicationScoped
public class C4ModelService {
    public List<WorkspaceResponse> listWorkspaces() {
        return WorkspaceEntity.<WorkspaceEntity>listAll().stream().map(Mapper::workspace).toList();
    }

    @Transactional
    public WorkspaceResponse createWorkspace(WorkspaceRequest request) {
        WorkspaceEntity entity = new WorkspaceEntity();
        entity.name = request.name();
        entity.description = value(request.description());
        entity.persist();
        return Mapper.workspace(entity);
    }

    public WorkspaceResponse getWorkspace(UUID workspaceId) {
        return Mapper.workspace(workspace(workspaceId));
    }

    @Transactional
    public WorkspaceResponse updateWorkspace(UUID workspaceId, WorkspaceRequest request) {
        WorkspaceEntity entity = workspace(workspaceId);
        checkVersion(entity.version, request.version());
        entity.name = request.name();
        entity.description = value(request.description());
        return Mapper.workspace(entity);
    }

    @Transactional
    public void deleteWorkspace(UUID workspaceId) {
        workspace(workspaceId).delete();
    }

    public List<ElementResponse> listElements(UUID workspaceId, ArchitectureElementType type, UUID parentId, String search, String tag,
            String owner, String lifecycleStatus) {
        workspace(workspaceId);
        return ArchitectureElementEntity.<ArchitectureElementEntity>list("workspaceId", workspaceId).stream()
                .filter(e -> type == null || e.type == type)
                .filter(e -> parentId == null || Objects.equals(e.parentElementId, parentId))
                .filter(e -> contains(e.name, search) || contains(e.description, search) || contains(e.technology, search) || containsJson(e.metadata, search))
                .filter(e -> tag == null || containsJson(e.metadata.get("classification"), tag))
                .filter(e -> owner == null || containsJson(e.metadata.get("ownership"), owner))
                .filter(e -> lifecycleStatus == null || containsJson(e.metadata.get("lifecycle"), lifecycleStatus))
                .map(Mapper::element)
                .toList();
    }

    @Transactional
    public ElementResponse createElement(UUID workspaceId, ElementRequest request) {
        workspace(workspaceId);
        validateMetadataShape(request.metadata());
        validateParent(workspaceId, null, request.type(), request.parentElementId());
        ArchitectureElementEntity entity = new ArchitectureElementEntity();
        entity.workspaceId = workspaceId;
        applyElement(entity, request);
        entity.persist();
        return Mapper.element(entity);
    }

    public ElementResponse getElement(UUID workspaceId, UUID elementId) {
        return Mapper.element(element(workspaceId, elementId));
    }

    @Transactional
    public ElementResponse updateElement(UUID workspaceId, UUID elementId, ElementRequest request) {
        ArchitectureElementEntity entity = element(workspaceId, elementId);
        checkVersion(entity.version, request.version());
        validateMetadataShape(request.metadata());
        validateParent(workspaceId, elementId, request.type(), request.parentElementId());
        applyElement(entity, request);
        return Mapper.element(entity);
    }

    @Transactional
    public void deleteElement(UUID workspaceId, UUID elementId) {
        element(workspaceId, elementId).delete();
    }

    public List<RelationshipResponse> listRelationships(UUID workspaceId) {
        workspace(workspaceId);
        return ArchitectureRelationshipEntity.<ArchitectureRelationshipEntity>list("workspaceId", workspaceId)
                .stream().map(Mapper::relationship).toList();
    }

    @Transactional
    public RelationshipResponse createRelationship(UUID workspaceId, RelationshipRequest request) {
        validateRelationship(workspaceId, request.sourceElementId(), request.targetElementId());
        ArchitectureRelationshipEntity entity = new ArchitectureRelationshipEntity();
        entity.workspaceId = workspaceId;
        applyRelationship(entity, request);
        entity.persist();
        return Mapper.relationship(entity);
    }

    public RelationshipResponse getRelationship(UUID workspaceId, UUID relationshipId) {
        return Mapper.relationship(relationship(workspaceId, relationshipId));
    }

    @Transactional
    public RelationshipResponse updateRelationship(UUID workspaceId, UUID relationshipId, RelationshipRequest request) {
        ArchitectureRelationshipEntity entity = relationship(workspaceId, relationshipId);
        checkVersion(entity.version, request.version());
        validateRelationship(workspaceId, request.sourceElementId(), request.targetElementId());
        applyRelationship(entity, request);
        return Mapper.relationship(entity);
    }

    @Transactional
    public void deleteRelationship(UUID workspaceId, UUID relationshipId) {
        relationship(workspaceId, relationshipId).delete();
    }

    public List<ViewResponse> listViews(UUID workspaceId) {
        workspace(workspaceId);
        return DiagramViewEntity.<DiagramViewEntity>list("workspaceId", workspaceId).stream().map(Mapper::view).toList();
    }

    @Transactional
    public ViewResponse createView(UUID workspaceId, ViewRequest request) {
        workspace(workspaceId);
        if (request.scopeElementId() != null) {
            element(workspaceId, request.scopeElementId());
        }
        DiagramViewEntity entity = new DiagramViewEntity();
        entity.workspaceId = workspaceId;
        applyView(entity, request);
        entity.persist();
        return Mapper.view(entity);
    }

    public ViewResponse getView(UUID workspaceId, UUID viewId) {
        return Mapper.view(view(workspaceId, viewId));
    }

    @Transactional
    public ViewResponse updateView(UUID workspaceId, UUID viewId, ViewRequest request) {
        DiagramViewEntity entity = view(workspaceId, viewId);
        checkVersion(entity.version, request.version());
        if (request.scopeElementId() != null) {
            element(workspaceId, request.scopeElementId());
        }
        applyView(entity, request);
        return Mapper.view(entity);
    }

    @Transactional
    public void deleteView(UUID workspaceId, UUID viewId) {
        view(workspaceId, viewId).delete();
    }

    @Transactional
    public ViewElementResponse addElementToView(UUID workspaceId, UUID viewId, ViewElementRequest request) {
        view(workspaceId, viewId);
        element(workspaceId, request.elementId());
        DiagramViewElementEntity existing = DiagramViewElementEntity.find("viewId = ?1 and elementId = ?2", viewId, request.elementId()).firstResult();
        DiagramViewElementEntity entity = existing == null ? new DiagramViewElementEntity() : existing;
        applyViewElement(entity, viewId, request);
        if (existing == null) {
            entity.persist();
        }
        return Mapper.viewElement(entity);
    }

    @Transactional
    public void removeElementFromView(UUID workspaceId, UUID viewId, UUID elementId) {
        view(workspaceId, viewId);
        DiagramViewElementEntity.delete("viewId = ?1 and elementId = ?2", viewId, elementId);
    }

    @Transactional
    public ViewRelationshipResponse addRelationshipToView(UUID workspaceId, UUID viewId, ViewRelationshipRequest request) {
        view(workspaceId, viewId);
        relationship(workspaceId, request.relationshipId());
        DiagramViewRelationshipEntity existing = DiagramViewRelationshipEntity.find("viewId = ?1 and relationshipId = ?2", viewId, request.relationshipId()).firstResult();
        DiagramViewRelationshipEntity entity = existing == null ? new DiagramViewRelationshipEntity() : existing;
        entity.viewId = viewId;
        entity.relationshipId = request.relationshipId();
        entity.visible = request.visible();
        entity.displaySettings = map(request.displaySettings());
        if (existing == null) {
            entity.persist();
        }
        return Mapper.viewRelationship(entity);
    }

    @Transactional
    public void removeRelationshipFromView(UUID workspaceId, UUID viewId, UUID relationshipId) {
        view(workspaceId, viewId);
        DiagramViewRelationshipEntity.delete("viewId = ?1 and relationshipId = ?2", viewId, relationshipId);
    }

    @Transactional
    public ViewResponse updateLayout(UUID workspaceId, UUID viewId, LayoutRequest request) {
        DiagramViewEntity targetView = view(workspaceId, viewId);
        checkVersion(targetView.version, request.viewVersion());
        if (request.elements() != null) {
            for (ViewElementRequest viewElement : request.elements()) {
                addElementToView(workspaceId, viewId, viewElement);
            }
        }
        if (request.relationships() != null) {
            for (ViewRelationshipRequest viewRelationship : request.relationships()) {
                addRelationshipToView(workspaceId, viewId, viewRelationship);
            }
        }
        targetView.updatedBy = "local-user";
        return Mapper.view(targetView);
    }

    public List<LinkResponse> listLinks(UUID workspaceId, UUID elementId) {
        element(workspaceId, elementId);
        return ExternalLinkEntity.<ExternalLinkEntity>list("elementId", elementId).stream().map(Mapper::link).toList();
    }

    @Transactional
    public LinkResponse createLink(UUID workspaceId, UUID elementId, LinkRequest request) {
        element(workspaceId, elementId);
        validateUrl(request.url());
        ExternalLinkEntity entity = new ExternalLinkEntity();
        entity.elementId = elementId;
        applyLink(entity, request);
        entity.persist();
        return Mapper.link(entity);
    }

    @Transactional
    public LinkResponse updateLink(UUID workspaceId, UUID elementId, UUID linkId, LinkRequest request) {
        element(workspaceId, elementId);
        validateUrl(request.url());
        ExternalLinkEntity entity = link(elementId, linkId);
        applyLink(entity, request);
        return Mapper.link(entity);
    }

    @Transactional
    public void deleteLink(UUID workspaceId, UUID elementId, UUID linkId) {
        element(workspaceId, elementId);
        link(elementId, linkId).delete();
    }

    public List<MetadataDefinitionResponse> listMetadataDefinitions(UUID workspaceId) {
        workspace(workspaceId);
        return MetadataDefinitionEntity.<MetadataDefinitionEntity>list("workspaceId", workspaceId)
                .stream().map(Mapper::metadataDefinition).toList();
    }

    @Transactional
    public MetadataDefinitionResponse createMetadataDefinition(UUID workspaceId, MetadataDefinitionRequest request) {
        workspace(workspaceId);
        MetadataDefinitionEntity entity = new MetadataDefinitionEntity();
        entity.workspaceId = workspaceId;
        applyMetadataDefinition(entity, request);
        entity.persist();
        return Mapper.metadataDefinition(entity);
    }

    @Transactional
    public MetadataDefinitionResponse updateMetadataDefinition(UUID workspaceId, UUID definitionId, MetadataDefinitionRequest request) {
        MetadataDefinitionEntity entity = metadataDefinition(workspaceId, definitionId);
        applyMetadataDefinition(entity, request);
        return Mapper.metadataDefinition(entity);
    }

    @Transactional
    public void deleteMetadataDefinition(UUID workspaceId, UUID definitionId) {
        metadataDefinition(workspaceId, definitionId).delete();
    }

    private WorkspaceEntity workspace(UUID workspaceId) {
        WorkspaceEntity entity = WorkspaceEntity.findById(workspaceId);
        if (entity == null) {
            throw new ApiException(Response.Status.NOT_FOUND, "WORKSPACE_NOT_FOUND", "Workspace was not found");
        }
        return entity;
    }

    private ArchitectureElementEntity element(UUID workspaceId, UUID elementId) {
        ArchitectureElementEntity entity = ArchitectureElementEntity.findById(elementId);
        if (entity == null || !entity.workspaceId.equals(workspaceId)) {
            throw new ApiException(Response.Status.NOT_FOUND, "ELEMENT_NOT_FOUND", "Architecture element was not found");
        }
        return entity;
    }

    private ArchitectureRelationshipEntity relationship(UUID workspaceId, UUID relationshipId) {
        ArchitectureRelationshipEntity entity = ArchitectureRelationshipEntity.findById(relationshipId);
        if (entity == null || !entity.workspaceId.equals(workspaceId)) {
            throw new ApiException(Response.Status.NOT_FOUND, "RELATIONSHIP_NOT_FOUND", "Architecture relationship was not found");
        }
        return entity;
    }

    private DiagramViewEntity view(UUID workspaceId, UUID viewId) {
        DiagramViewEntity entity = DiagramViewEntity.findById(viewId);
        if (entity == null || !entity.workspaceId.equals(workspaceId)) {
            throw new ApiException(Response.Status.NOT_FOUND, "VIEW_NOT_FOUND", "Diagram view was not found");
        }
        return entity;
    }

    private ExternalLinkEntity link(UUID elementId, UUID linkId) {
        ExternalLinkEntity entity = ExternalLinkEntity.findById(linkId);
        if (entity == null || !entity.elementId.equals(elementId)) {
            throw new ApiException(Response.Status.NOT_FOUND, "LINK_NOT_FOUND", "External link was not found");
        }
        return entity;
    }

    private MetadataDefinitionEntity metadataDefinition(UUID workspaceId, UUID definitionId) {
        MetadataDefinitionEntity entity = MetadataDefinitionEntity.findById(definitionId);
        if (entity == null || !entity.workspaceId.equals(workspaceId)) {
            throw new ApiException(Response.Status.NOT_FOUND, "METADATA_DEFINITION_NOT_FOUND", "Metadata definition was not found");
        }
        return entity;
    }

    private void validateParent(UUID workspaceId, UUID elementId, ArchitectureElementType type, UUID parentElementId) {
        if (parentElementId == null) {
            if (type == ArchitectureElementType.COMPONENT) {
                throw new ApiException(Response.Status.BAD_REQUEST, "INVALID_PARENT", "A component must belong to a container");
            }
            return;
        }
        if (parentElementId.equals(elementId)) {
            throw new ApiException(Response.Status.BAD_REQUEST, "CIRCULAR_PARENT", "An element cannot be its own parent");
        }
        ArchitectureElementEntity parent = element(workspaceId, parentElementId);
        boolean allowed = switch (type) {
            case CONTAINER -> parent.type == ArchitectureElementType.SOFTWARE_SYSTEM;
            case COMPONENT -> parent.type == ArchitectureElementType.CONTAINER;
            case DATA_STORE -> parent.type == ArchitectureElementType.SOFTWARE_SYSTEM || parent.type == ArchitectureElementType.CONTAINER;
            case PERSON, SOFTWARE_SYSTEM, EXTERNAL_SYSTEM -> false;
        };
        if (!allowed) {
            throw new ApiException(Response.Status.BAD_REQUEST, "INVALID_PARENT", "Parent element type is not valid for " + type);
        }
        ArrayDeque<UUID> seen = new ArrayDeque<>();
        UUID cursor = parentElementId;
        while (cursor != null) {
            if (cursor.equals(elementId) || seen.contains(cursor)) {
                throw new ApiException(Response.Status.BAD_REQUEST, "CIRCULAR_PARENT", "Parent hierarchy cannot be circular");
            }
            seen.add(cursor);
            ArchitectureElementEntity current = ArchitectureElementEntity.findById(cursor);
            cursor = current == null ? null : current.parentElementId;
        }
    }

    private void validateRelationship(UUID workspaceId, UUID sourceElementId, UUID targetElementId) {
        if (sourceElementId.equals(targetElementId)) {
            throw new ApiException(Response.Status.BAD_REQUEST, "SELF_RELATIONSHIP_REJECTED", "Self relationships are not supported");
        }
        element(workspaceId, sourceElementId);
        element(workspaceId, targetElementId);
    }

    private void checkVersion(long current, Long requested) {
        if (requested != null && requested != current) {
            throw new ApiException(Response.Status.CONFLICT, "STALE_VERSION", "The submitted version is stale",
                    Map.of("currentVersion", current, "submittedVersion", requested));
        }
    }

    private void validateMetadataShape(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return;
        }
        for (String key : metadata.keySet()) {
            if (!List.of("ownership", "classification", "lifecycle", "operations", "security", "delivery", "custom", "responsibilities").contains(key)) {
                throw new ApiException(Response.Status.BAD_REQUEST, "INVALID_METADATA", "Metadata section is not supported: " + key);
            }
        }
    }

    private void validateUrl(String url) {
        try {
            URI parsed = URI.create(url);
            if (parsed.getScheme() == null || parsed.getHost() == null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException ex) {
            throw new ApiException(Response.Status.BAD_REQUEST, "INVALID_URL", "External link URL is invalid");
        }
    }

    private void applyElement(ArchitectureElementEntity entity, ElementRequest request) {
        entity.type = request.type();
        entity.name = request.name();
        entity.description = value(request.description());
        entity.parentElementId = request.parentElementId();
        entity.technology = blankToNull(request.technology());
        entity.metadata = map(request.metadata());
    }

    private void applyRelationship(ArchitectureRelationshipEntity entity, RelationshipRequest request) {
        entity.sourceElementId = request.sourceElementId();
        entity.targetElementId = request.targetElementId();
        entity.type = request.type();
        entity.description = value(request.description());
        entity.technology = blankToNull(request.technology());
        entity.protocol = blankToNull(request.protocol());
        entity.metadata = map(request.metadata());
    }

    private void applyView(DiagramViewEntity entity, ViewRequest request) {
        entity.name = request.name();
        entity.description = value(request.description());
        entity.type = request.type();
        entity.scopeElementId = request.scopeElementId();
        entity.layoutDirection = request.layoutDirection() == null ? LayoutDirection.LEFT_TO_RIGHT : request.layoutDirection();
        entity.settings = map(request.settings());
    }

    private void applyViewElement(DiagramViewElementEntity entity, UUID viewId, ViewElementRequest request) {
        entity.viewId = viewId;
        entity.elementId = request.elementId();
        entity.x = request.x();
        entity.y = request.y();
        entity.width = request.width() <= 0 ? 260 : request.width();
        entity.height = request.height() <= 0 ? 150 : request.height();
        entity.locked = request.locked();
        entity.visible = request.visible();
        entity.zIndex = request.zIndex();
        entity.displaySettings = map(request.displaySettings());
    }

    private void applyLink(ExternalLinkEntity entity, LinkRequest request) {
        entity.provider = request.provider();
        entity.type = request.type();
        entity.label = request.label();
        entity.url = request.url();
        entity.externalId = blankToNull(request.externalId());
        entity.metadata = map(request.metadata());
    }

    private void applyMetadataDefinition(MetadataDefinitionEntity entity, MetadataDefinitionRequest request) {
        entity.key = request.key();
        entity.label = request.label();
        entity.description = blankToNull(request.description());
        entity.valueType = request.valueType();
        entity.required = request.required();
        entity.appliesTo = request.appliesTo() == null ? List.of() : request.appliesTo();
        entity.allowedValues = stringList(request.allowedValues());
        entity.defaultValue = request.defaultValue();
        entity.validationRules = request.validationRules();
        entity.displayOrder = request.displayOrder();
    }

    private static String value(String text) {
        return text == null ? "" : text;
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }

    private static Map<String, Object> map(Map<String, Object> source) {
        return source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
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

    private static boolean contains(String value, String query) {
        return query == null || query.isBlank() || (value != null && value.toLowerCase().contains(query.toLowerCase()));
    }

    private static boolean containsJson(Object value, String query) {
        return query == null || query.isBlank() || (value != null && value.toString().toLowerCase().contains(query.toLowerCase()));
    }
}
