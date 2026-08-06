package com.example.c4editor.application;

import com.example.c4editor.api.ApiException;
import com.example.c4editor.api.Dtos.CanonicalModel;
import com.example.c4editor.api.Dtos.ElementResponse;
import com.example.c4editor.api.Dtos.ImportOptions;
import com.example.c4editor.api.Dtos.ImportPreview;
import com.example.c4editor.api.Dtos.ImportResponse;
import com.example.c4editor.api.Dtos.ImportSource;
import com.example.c4editor.api.Dtos.ImportedWorkspace;
import com.example.c4editor.api.Dtos.LinkResponse;
import com.example.c4editor.api.Dtos.RelationshipResponse;
import com.example.c4editor.api.Dtos.ViewElementResponse;
import com.example.c4editor.api.Dtos.ViewRelationshipResponse;
import com.example.c4editor.api.Dtos.ViewResponse;
import com.example.c4editor.domain.ImportMode;
import com.example.c4editor.integration.StructurizrDslImporter;
import com.example.c4editor.persistence.ArchitectureElementEntity;
import com.example.c4editor.persistence.ArchitectureRelationshipEntity;
import com.example.c4editor.persistence.DiagramViewElementEntity;
import com.example.c4editor.persistence.DiagramViewEntity;
import com.example.c4editor.persistence.DiagramViewRelationshipEntity;
import com.example.c4editor.persistence.ExternalLinkEntity;
import com.example.c4editor.persistence.ImportSourceMappingEntity;
import com.example.c4editor.persistence.MetadataDefinitionEntity;
import com.example.c4editor.persistence.WorkspaceEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class StructurizrImportService {
    @Inject
    StructurizrDslImporter importer;

    public ImportPreview validate(ImportSource source) {
        return importer.validate(source);
    }

    @Transactional
    public ImportResponse importWorkspace(ImportSource source, ImportOptions options) {
        ImportOptions effectiveOptions = normalize(options);
        if (effectiveOptions.mode() == ImportMode.MERGE) {
            throw new ApiException(Response.Status.NOT_IMPLEMENTED, "MERGE_NOT_SUPPORTED", "Structurizr merge import is deferred for this MVP");
        }
        ImportedWorkspace imported = importer.importModel(source, effectiveOptions);
        if (!imported.preview().valid()) {
            throw new ApiException(Response.Status.BAD_REQUEST, "INVALID_STRUCTURIZR_IMPORT", "Structurizr import failed validation",
                    Map.of("errors", imported.preview().errors()));
        }
        WorkspaceEntity workspace = switch (effectiveOptions.mode()) {
            case CREATE_NEW -> createWorkspace(imported.model());
            case REPLACE -> replaceWorkspace(effectiveOptions.targetWorkspaceId(), imported.model());
            case MERGE -> throw new ApiException(Response.Status.NOT_IMPLEMENTED, "MERGE_NOT_SUPPORTED", "Structurizr merge import is deferred for this MVP");
        };
        persistModel(workspace, imported.model(), imported.sourceEntityIdsByCanonicalId(), source.checksum());
        return new ImportResponse(workspace.id, imported.model().elements().size(), imported.model().relationships().size(), imported.model().views().size());
    }

    private ImportOptions normalize(ImportOptions options) {
        if (options == null || options.mode() == null) {
            return new ImportOptions(ImportMode.CREATE_NEW, null, null, null);
        }
        if (options.mode() == ImportMode.REPLACE && options.targetWorkspaceId() == null) {
            throw new ApiException(Response.Status.BAD_REQUEST, "TARGET_WORKSPACE_REQUIRED", "Replace import requires targetWorkspaceId");
        }
        return options;
    }

    private WorkspaceEntity createWorkspace(CanonicalModel model) {
        WorkspaceEntity workspace = new WorkspaceEntity();
        workspace.name = model.workspace().name();
        workspace.description = model.workspace().description() == null ? "" : model.workspace().description();
        workspace.persist();
        return workspace;
    }

    private WorkspaceEntity replaceWorkspace(UUID workspaceId, CanonicalModel model) {
        WorkspaceEntity workspace = WorkspaceEntity.findById(workspaceId);
        if (workspace == null) {
            throw new ApiException(Response.Status.NOT_FOUND, "WORKSPACE_NOT_FOUND", "Target workspace was not found");
        }
        List<ArchitectureElementEntity> elements = ArchitectureElementEntity.list("workspaceId", workspace.id);
        List<DiagramViewEntity> views = DiagramViewEntity.list("workspaceId", workspace.id);
        for (DiagramViewEntity view : views) {
            DiagramViewRelationshipEntity.delete("viewId", view.id);
            DiagramViewElementEntity.delete("viewId", view.id);
        }
        DiagramViewEntity.delete("workspaceId", workspace.id);
        ArchitectureRelationshipEntity.delete("workspaceId", workspace.id);
        for (ArchitectureElementEntity element : elements) {
            ExternalLinkEntity.delete("elementId", element.id);
        }
        ArchitectureElementEntity.delete("workspaceId", workspace.id);
        MetadataDefinitionEntity.delete("workspaceId", workspace.id);
        ImportSourceMappingEntity.delete("workspaceId", workspace.id);
        workspace.name = model.workspace().name();
        workspace.description = model.workspace().description() == null ? "" : model.workspace().description();
        return workspace;
    }

    private void persistModel(WorkspaceEntity workspace, CanonicalModel model, Map<String, String> sourceIds, String checksum) {
        for (ElementResponse source : safe(model.elements())) {
            ArchitectureElementEntity entity = new ArchitectureElementEntity();
            entity.id = source.id();
            entity.workspaceId = workspace.id;
            entity.type = source.type();
            entity.name = source.name();
            entity.description = source.description() == null ? "" : source.description();
            entity.parentElementId = source.parentElementId();
            entity.technology = source.technology();
            entity.metadata = map(source.metadata());
            entity.persist();
            mapping(workspace.id, "ELEMENT", source.id(), sourceIds.get(source.id().toString()), checksum);
        }
        for (RelationshipResponse source : safe(model.relationships())) {
            ArchitectureRelationshipEntity entity = new ArchitectureRelationshipEntity();
            entity.id = source.id();
            entity.workspaceId = workspace.id;
            entity.sourceElementId = source.sourceElementId();
            entity.targetElementId = source.targetElementId();
            entity.type = source.type();
            entity.description = source.description() == null ? "" : source.description();
            entity.technology = source.technology();
            entity.protocol = source.protocol();
            entity.metadata = map(source.metadata());
            entity.persist();
            mapping(workspace.id, "RELATIONSHIP", source.id(), sourceIds.get(source.id().toString()), checksum);
        }
        for (ViewResponse source : safe(model.views())) {
            DiagramViewEntity entity = new DiagramViewEntity();
            entity.id = source.id();
            entity.workspaceId = workspace.id;
            entity.name = source.name();
            entity.description = source.description() == null ? "" : source.description();
            entity.type = source.type();
            entity.scopeElementId = source.scopeElementId();
            entity.layoutDirection = source.layoutDirection();
            entity.settings = map(source.settings());
            entity.persist();
            for (ViewElementResponse member : safe(source.elements())) {
                DiagramViewElementEntity viewElement = new DiagramViewElementEntity();
                viewElement.id = member.id();
                viewElement.viewId = entity.id;
                viewElement.elementId = member.elementId();
                viewElement.x = member.x();
                viewElement.y = member.y();
                viewElement.width = member.width();
                viewElement.height = member.height();
                viewElement.locked = member.locked();
                viewElement.visible = member.visible();
                viewElement.zIndex = member.zIndex();
                viewElement.displaySettings = map(member.displaySettings());
                viewElement.persist();
            }
            for (ViewRelationshipResponse member : safe(source.relationships())) {
                DiagramViewRelationshipEntity viewRelationship = new DiagramViewRelationshipEntity();
                viewRelationship.id = member.id();
                viewRelationship.viewId = entity.id;
                viewRelationship.relationshipId = member.relationshipId();
                viewRelationship.visible = member.visible();
                viewRelationship.displaySettings = map(member.displaySettings());
                viewRelationship.persist();
            }
        }
        for (LinkResponse source : safe(model.links())) {
            ExternalLinkEntity entity = new ExternalLinkEntity();
            entity.id = source.id();
            entity.elementId = source.elementId();
            entity.provider = source.provider();
            entity.type = source.type();
            entity.label = source.label();
            entity.url = source.url();
            entity.externalId = source.externalId();
            entity.metadata = map(source.metadata());
            entity.persist();
        }
    }

    private void mapping(UUID workspaceId, String sourceEntityType, UUID canonicalEntityId, String sourceEntityId, String checksum) {
        if (sourceEntityId == null || sourceEntityId.isBlank()) {
            return;
        }
        ImportSourceMappingEntity entity = new ImportSourceMappingEntity();
        entity.workspaceId = workspaceId;
        entity.sourceFormat = "STRUCTURIZR_DSL";
        entity.sourceWorkspaceKey = null;
        entity.sourceEntityType = sourceEntityType;
        entity.sourceEntityId = sourceEntityId;
        entity.canonicalEntityId = canonicalEntityId;
        entity.lastImportedAt = Instant.now();
        entity.sourceChecksum = checksum;
        entity.persist();
    }

    private static <T> List<T> safe(List<T> value) {
        return value == null ? List.of() : value;
    }

    private static Map<String, Object> map(Map<String, Object> source) {
        return source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
    }
}
