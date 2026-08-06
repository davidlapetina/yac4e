package com.example.c4editor.application;

import com.example.c4editor.api.Dtos.ElementResponse;
import com.example.c4editor.api.Dtos.LinkResponse;
import com.example.c4editor.api.Dtos.MetadataDefinitionResponse;
import com.example.c4editor.api.Dtos.RelationshipResponse;
import com.example.c4editor.api.Dtos.ViewElementResponse;
import com.example.c4editor.api.Dtos.ViewRelationshipResponse;
import com.example.c4editor.api.Dtos.ViewResponse;
import com.example.c4editor.api.Dtos.WorkspaceResponse;
import com.example.c4editor.persistence.ArchitectureElementEntity;
import com.example.c4editor.persistence.ArchitectureRelationshipEntity;
import com.example.c4editor.persistence.DiagramViewElementEntity;
import com.example.c4editor.persistence.DiagramViewEntity;
import com.example.c4editor.persistence.DiagramViewRelationshipEntity;
import com.example.c4editor.persistence.ExternalLinkEntity;
import com.example.c4editor.persistence.MetadataDefinitionEntity;
import com.example.c4editor.persistence.WorkspaceEntity;
import java.util.List;

final class Mapper {
    private Mapper() {
    }

    static WorkspaceResponse workspace(WorkspaceEntity entity) {
        return new WorkspaceResponse(entity.id, entity.name, entity.description, entity.version, entity.createdAt, entity.updatedAt);
    }

    static ElementResponse element(ArchitectureElementEntity entity) {
        long linkCount = ExternalLinkEntity.count("elementId", entity.id);
        return new ElementResponse(entity.id, entity.workspaceId, entity.type, entity.name, entity.description, entity.parentElementId,
                entity.technology, entity.metadata, entity.version, entity.createdAt, entity.updatedAt, (int) linkCount);
    }

    static RelationshipResponse relationship(ArchitectureRelationshipEntity entity) {
        return new RelationshipResponse(entity.id, entity.workspaceId, entity.sourceElementId, entity.targetElementId, entity.type,
                entity.description, entity.technology, entity.protocol, entity.metadata, entity.version, entity.createdAt, entity.updatedAt);
    }

    static ViewResponse view(DiagramViewEntity entity) {
        List<ViewElementResponse> elements = DiagramViewElementEntity.<DiagramViewElementEntity>list("viewId", entity.id)
                .stream().map(Mapper::viewElement).toList();
        List<ViewRelationshipResponse> relationships = DiagramViewRelationshipEntity.<DiagramViewRelationshipEntity>list("viewId", entity.id)
                .stream().map(Mapper::viewRelationship).toList();
        return new ViewResponse(entity.id, entity.workspaceId, entity.name, entity.description, entity.type, entity.scopeElementId,
                entity.layoutDirection, entity.settings, entity.version, entity.createdAt, entity.updatedAt, elements, relationships);
    }

    static ViewElementResponse viewElement(DiagramViewElementEntity entity) {
        return new ViewElementResponse(entity.id, entity.viewId, entity.elementId, entity.x, entity.y, entity.width, entity.height,
                entity.locked, entity.visible, entity.zIndex, entity.displaySettings);
    }

    static ViewRelationshipResponse viewRelationship(DiagramViewRelationshipEntity entity) {
        return new ViewRelationshipResponse(entity.id, entity.viewId, entity.relationshipId, entity.visible, entity.displaySettings);
    }

    static LinkResponse link(ExternalLinkEntity entity) {
        return new LinkResponse(entity.id, entity.elementId, entity.provider, entity.type, entity.label, entity.url,
                entity.externalId, entity.metadata, entity.createdAt, entity.updatedAt);
    }

    static MetadataDefinitionResponse metadataDefinition(MetadataDefinitionEntity entity) {
        return new MetadataDefinitionResponse(entity.id, entity.workspaceId, entity.key, entity.label, entity.description,
                entity.valueType, entity.required, entity.appliesTo, entity.allowedValues, entity.defaultValue,
                entity.validationRules, entity.displayOrder);
    }
}
