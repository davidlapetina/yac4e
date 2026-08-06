package com.example.c4editor.application;

import com.example.c4editor.api.AgentProposalDtos.AgentProposalChangeRequest;
import com.example.c4editor.api.AgentProposalDtos.AgentProposalChangeResponse;
import com.example.c4editor.api.AgentProposalDtos.AgentProposalRequest;
import com.example.c4editor.api.AgentProposalDtos.AgentProposalResponse;
import com.example.c4editor.api.AgentProposalDtos.AgentProposalSummaryResponse;
import com.example.c4editor.api.AgentProposalDtos.ElementDraft;
import com.example.c4editor.api.AgentProposalDtos.LinkDraft;
import com.example.c4editor.api.AgentProposalDtos.MetadataDefinitionDraft;
import com.example.c4editor.api.AgentProposalDtos.ProposalValidationIssue;
import com.example.c4editor.api.AgentProposalDtos.ProposalValidationResult;
import com.example.c4editor.api.AgentProposalDtos.RelationshipDraft;
import com.example.c4editor.api.AgentProposalDtos.ViewDraft;
import com.example.c4editor.api.ApiException;
import com.example.c4editor.domain.AgentProposalChangeAction;
import com.example.c4editor.domain.AgentProposalChangeStatus;
import com.example.c4editor.domain.AgentProposalStatus;
import com.example.c4editor.domain.ArchitectureElementType;
import com.example.c4editor.domain.LayoutDirection;
import com.example.c4editor.domain.Severity;
import com.example.c4editor.persistence.AgentProposalChangeEntity;
import com.example.c4editor.persistence.AgentProposalEntity;
import com.example.c4editor.persistence.ArchitectureElementEntity;
import com.example.c4editor.persistence.ArchitectureRelationshipEntity;
import com.example.c4editor.persistence.DiagramViewEntity;
import com.example.c4editor.persistence.ExternalLinkEntity;
import com.example.c4editor.persistence.MetadataDefinitionEntity;
import com.example.c4editor.persistence.WorkspaceEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class AgentProposalService {
    private static final int MAX_CHANGES = 200;

    @Inject
    ObjectMapper objectMapper;

    public List<AgentProposalSummaryResponse> list(UUID workspaceId) {
        workspace(workspaceId);
        return AgentProposalEntity.<AgentProposalEntity>list("workspaceId", workspaceId).stream()
                .map(this::summaryResponse)
                .toList();
    }

    public AgentProposalResponse get(UUID workspaceId, UUID proposalId) {
        return response(proposal(workspaceId, proposalId));
    }

    public ProposalValidationResult validate(UUID workspaceId, AgentProposalRequest request) {
        workspace(workspaceId);
        return validateRequest(workspaceId, normalize(request));
    }

    @Transactional
    public AgentProposalResponse create(UUID workspaceId, AgentProposalRequest request) {
        workspace(workspaceId);
        AgentProposalRequest normalized = normalize(request);
        ProposalValidationResult validation = validateRequest(workspaceId, normalized);

        AgentProposalEntity proposal = new AgentProposalEntity();
        proposal.workspaceId = workspaceId;
        proposal.status = validation.valid() ? AgentProposalStatus.PENDING : AgentProposalStatus.VALIDATION_FAILED;
        proposal.source = map(normalized.source());
        proposal.summary = value(normalized.summary());
        proposal.validation = toMap(validation);
        proposal.persist();

        int sequence = 1;
        for (AgentProposalChangeRequest change : normalized.changes()) {
            AgentProposalChangeEntity entity = new AgentProposalChangeEntity();
            entity.proposalId = proposal.id;
            entity.sequenceNumber = sequence++;
            entity.action = change.action();
            entity.status = AgentProposalChangeStatus.PENDING;
            entity.clientReference = blankToNull(change.clientReference());
            entity.targetEntityId = change.targetEntityId();
            entity.targetEntityType = targetType(change.action());
            entity.payload = toMap(change);
            entity.evidence = evidence(change.evidence());
            entity.validation = Map.of();
            entity.persist();
        }
        return response(proposal);
    }

    @Transactional
    public AgentProposalResponse apply(UUID workspaceId, UUID proposalId) {
        AgentProposalEntity proposal = proposal(workspaceId, proposalId);
        if (proposal.status == AgentProposalStatus.APPLIED) {
            return response(proposal);
        }
        if (proposal.status == AgentProposalStatus.REJECTED) {
            throw new ApiException(Response.Status.CONFLICT, "PROPOSAL_REJECTED", "Rejected proposals cannot be applied");
        }

        List<AgentProposalChangeEntity> changeEntities = changes(proposal.id);
        AgentProposalRequest request = requestFrom(proposal, changeEntities);
        ProposalValidationResult validation = validateRequest(workspaceId, request);
        proposal.validation = toMap(validation);
        if (!validation.valid()) {
            proposal.status = AgentProposalStatus.VALIDATION_FAILED;
            throw new ApiException(Response.Status.BAD_REQUEST, "INVALID_PROPOSAL", "Proposal validation failed", toMap(validation));
        }

        Map<String, UUID> references = new LinkedHashMap<>();
        for (AgentProposalChangeEntity changeEntity : changeEntities) {
            AgentProposalChangeRequest change = fromPayload(changeEntity.payload);
            UUID resultId = applyChange(workspaceId, change, references);
            changeEntity.resultEntityId = resultId;
            changeEntity.status = AgentProposalChangeStatus.APPLIED;
            if (changeEntity.clientReference != null) {
                references.put(changeEntity.clientReference, resultId);
            }
        }

        proposal.status = AgentProposalStatus.APPLIED;
        proposal.appliedAt = Instant.now();
        return response(proposal);
    }

    @Transactional
    public AgentProposalResponse reject(UUID workspaceId, UUID proposalId) {
        AgentProposalEntity proposal = proposal(workspaceId, proposalId);
        if (proposal.status == AgentProposalStatus.APPLIED) {
            throw new ApiException(Response.Status.CONFLICT, "PROPOSAL_ALREADY_APPLIED", "Applied proposals cannot be rejected");
        }
        proposal.status = AgentProposalStatus.REJECTED;
        proposal.rejectedAt = Instant.now();
        for (AgentProposalChangeEntity change : changes(proposal.id)) {
            change.status = AgentProposalChangeStatus.REJECTED;
        }
        return response(proposal);
    }

    private UUID applyChange(UUID workspaceId, AgentProposalChangeRequest change, Map<String, UUID> references) {
        return switch (change.action()) {
            case CREATE_ELEMENT -> createElement(workspaceId, change.element(), references);
            case UPDATE_ELEMENT -> updateElement(workspaceId, change.targetEntityId(), change.element(), references);
            case CREATE_RELATIONSHIP -> createRelationship(workspaceId, change.relationship(), references);
            case UPDATE_RELATIONSHIP -> updateRelationship(workspaceId, change.targetEntityId(), change.relationship(), references);
            case CREATE_LINK -> createLink(workspaceId, change.link(), references);
            case CREATE_METADATA_DEFINITION -> createMetadataDefinition(workspaceId, change.metadataDefinition());
            case CREATE_VIEW -> createView(workspaceId, change.view(), references);
        };
    }

    private UUID createElement(UUID workspaceId, ElementDraft draft, Map<String, UUID> references) {
        ArchitectureElementEntity entity = new ArchitectureElementEntity();
        entity.workspaceId = workspaceId;
        entity.type = draft.type();
        entity.name = draft.name();
        entity.description = value(draft.description());
        entity.parentElementId = resolveNullableElement(draft.parentElementId(), draft.parentReference(), references);
        entity.technology = blankToNull(draft.technology());
        entity.metadata = map(draft.metadata());
        entity.persist();
        return entity.id;
    }

    private UUID updateElement(UUID workspaceId, UUID elementId, ElementDraft draft, Map<String, UUID> references) {
        ArchitectureElementEntity entity = element(workspaceId, elementId);
        if (draft.type() != null) {
            entity.type = draft.type();
        }
        if (draft.name() != null && !draft.name().isBlank()) {
            entity.name = draft.name();
        }
        if (draft.description() != null) {
            entity.description = draft.description();
        }
        if (draft.parentElementId() != null || draft.parentReference() != null) {
            entity.parentElementId = resolveNullableElement(draft.parentElementId(), draft.parentReference(), references);
        }
        if (draft.technology() != null) {
            entity.technology = blankToNull(draft.technology());
        }
        if (draft.metadata() != null) {
            entity.metadata = map(draft.metadata());
        }
        return entity.id;
    }

    private UUID createRelationship(UUID workspaceId, RelationshipDraft draft, Map<String, UUID> references) {
        ArchitectureRelationshipEntity entity = new ArchitectureRelationshipEntity();
        entity.workspaceId = workspaceId;
        entity.sourceElementId = resolveElement(draft.sourceElementId(), draft.sourceReference(), references);
        entity.targetElementId = resolveElement(draft.targetElementId(), draft.targetReference(), references);
        entity.type = draft.type();
        entity.description = value(draft.description());
        entity.technology = blankToNull(draft.technology());
        entity.protocol = blankToNull(draft.protocol());
        entity.metadata = map(draft.metadata());
        entity.persist();
        return entity.id;
    }

    private UUID updateRelationship(UUID workspaceId, UUID relationshipId, RelationshipDraft draft, Map<String, UUID> references) {
        ArchitectureRelationshipEntity entity = relationship(workspaceId, relationshipId);
        if (draft.sourceElementId() != null || draft.sourceReference() != null) {
            entity.sourceElementId = resolveElement(draft.sourceElementId(), draft.sourceReference(), references);
        }
        if (draft.targetElementId() != null || draft.targetReference() != null) {
            entity.targetElementId = resolveElement(draft.targetElementId(), draft.targetReference(), references);
        }
        if (draft.type() != null) {
            entity.type = draft.type();
        }
        if (draft.description() != null) {
            entity.description = draft.description();
        }
        if (draft.technology() != null) {
            entity.technology = blankToNull(draft.technology());
        }
        if (draft.protocol() != null) {
            entity.protocol = blankToNull(draft.protocol());
        }
        if (draft.metadata() != null) {
            entity.metadata = map(draft.metadata());
        }
        return entity.id;
    }

    private UUID createLink(UUID workspaceId, LinkDraft draft, Map<String, UUID> references) {
        UUID elementId = resolveElement(draft.elementId(), draft.elementReference(), references);
        element(workspaceId, elementId);
        ExternalLinkEntity entity = new ExternalLinkEntity();
        entity.elementId = elementId;
        entity.provider = draft.provider();
        entity.type = draft.type();
        entity.label = draft.label();
        entity.url = draft.url();
        entity.externalId = blankToNull(draft.externalId());
        entity.metadata = map(draft.metadata());
        entity.persist();
        return entity.id;
    }

    private UUID createMetadataDefinition(UUID workspaceId, MetadataDefinitionDraft draft) {
        MetadataDefinitionEntity entity = new MetadataDefinitionEntity();
        entity.workspaceId = workspaceId;
        entity.key = draft.key();
        entity.label = draft.label();
        entity.description = blankToNull(draft.description());
        entity.valueType = draft.valueType();
        entity.required = draft.required();
        entity.appliesTo = draft.appliesTo() == null ? List.of() : draft.appliesTo();
        entity.allowedValues = stringList(draft.allowedValues());
        entity.defaultValue = draft.defaultValue();
        entity.validationRules = draft.validationRules();
        entity.displayOrder = draft.displayOrder();
        entity.persist();
        return entity.id;
    }

    private UUID createView(UUID workspaceId, ViewDraft draft, Map<String, UUID> references) {
        DiagramViewEntity entity = new DiagramViewEntity();
        entity.workspaceId = workspaceId;
        entity.name = draft.name();
        entity.description = value(draft.description());
        entity.type = draft.type();
        entity.scopeElementId = resolveNullableElement(draft.scopeElementId(), draft.scopeReference(), references);
        entity.layoutDirection = draft.layoutDirection() == null ? LayoutDirection.LEFT_TO_RIGHT : draft.layoutDirection();
        entity.settings = map(draft.settings());
        entity.persist();
        return entity.id;
    }

    private ProposalValidationResult validateRequest(UUID workspaceId, AgentProposalRequest request) {
        List<ProposalValidationIssue> errors = new ArrayList<>();
        List<ProposalValidationIssue> warnings = new ArrayList<>();
        Map<String, ElementDraft> projectedElements = new LinkedHashMap<>();
        Set<String> references = new LinkedHashSet<>();
        Set<String> metadataKeys = new LinkedHashSet<>();
        List<AgentProposalChangeRequest> changes = request.changes() == null ? List.of() : request.changes();

        if (changes.isEmpty()) {
            errors.add(issue(Severity.ERROR, "EMPTY_PROPOSAL", null, null, "Proposal must contain at least one change", Map.of()));
        }
        if (changes.size() > MAX_CHANGES) {
            errors.add(issue(Severity.ERROR, "TOO_MANY_CHANGES", null, null, "Proposal exceeds the maximum change count", Map.of("maximum", MAX_CHANGES)));
        }

        int sequence = 1;
        for (AgentProposalChangeRequest change : changes) {
            if (change == null || change.action() == null) {
                errors.add(issue(Severity.ERROR, "MISSING_ACTION", sequence, null, "Proposal change action is required", Map.of()));
                sequence++;
                continue;
            }
            if (change.evidence() == null || change.evidence().isEmpty()) {
                warnings.add(issue(Severity.WARNING, "MISSING_EVIDENCE", sequence, change.clientReference(),
                        "Proposal change has no evidence references", Map.of()));
            }
            validateChange(workspaceId, sequence, change, projectedElements, references, errors);
            if (change.action() == AgentProposalChangeAction.CREATE_ELEMENT && change.clientReference() != null && change.element() != null) {
                projectedElements.put(change.clientReference(), change.element());
            }
            if (change.clientReference() != null && !references.add(change.clientReference())) {
                errors.add(issue(Severity.ERROR, "DUPLICATE_CLIENT_REFERENCE", sequence, change.clientReference(),
                        "Client references must be unique within a proposal", Map.of("clientReference", change.clientReference())));
            }
            if (change.action() == AgentProposalChangeAction.CREATE_METADATA_DEFINITION && change.metadataDefinition() != null
                    && change.metadataDefinition().key() != null && !metadataKeys.add(change.metadataDefinition().key())) {
                errors.add(issue(Severity.ERROR, "DUPLICATE_METADATA_DEFINITION", sequence, change.clientReference(),
                        "Metadata definition keys must be unique within a proposal", Map.of("key", change.metadataDefinition().key())));
            }
            sequence++;
        }

        Map<String, Integer> summary = new LinkedHashMap<>();
        for (AgentProposalChangeAction action : AgentProposalChangeAction.values()) {
            int count = (int) changes.stream().filter(change -> change != null && change.action() == action).count();
            if (count > 0) {
                summary.put(action.name(), count);
            }
        }
        return new ProposalValidationResult(errors.isEmpty(), warnings, errors, summary);
    }

    private void validateChange(UUID workspaceId, int sequence, AgentProposalChangeRequest change,
            Map<String, ElementDraft> projectedElements, Set<String> references, List<ProposalValidationIssue> errors) {
        switch (change.action()) {
            case CREATE_ELEMENT -> validateCreateElement(workspaceId, sequence, change, projectedElements, references, errors);
            case UPDATE_ELEMENT -> validateUpdateElement(workspaceId, sequence, change, projectedElements, references, errors);
            case CREATE_RELATIONSHIP -> validateCreateRelationship(workspaceId, sequence, change, projectedElements, references, errors);
            case UPDATE_RELATIONSHIP -> validateUpdateRelationship(workspaceId, sequence, change, projectedElements, references, errors);
            case CREATE_LINK -> validateCreateLink(workspaceId, sequence, change, projectedElements, references, errors);
            case CREATE_METADATA_DEFINITION -> validateCreateMetadataDefinition(workspaceId, sequence, change, errors);
            case CREATE_VIEW -> validateCreateView(workspaceId, sequence, change, projectedElements, references, errors);
        }
    }

    private void validateCreateElement(UUID workspaceId, int sequence, AgentProposalChangeRequest change,
            Map<String, ElementDraft> projectedElements, Set<String> references, List<ProposalValidationIssue> errors) {
        ElementDraft draft = change.element();
        if (draft == null) {
            errors.add(issue(Severity.ERROR, "MISSING_ELEMENT", sequence, change.clientReference(), "Element draft is required", Map.of()));
            return;
        }
        if (draft.type() == null) {
            errors.add(issue(Severity.ERROR, "MISSING_ELEMENT_TYPE", sequence, change.clientReference(), "Element type is required", Map.of()));
        }
        if (draft.name() == null || draft.name().isBlank()) {
            errors.add(issue(Severity.ERROR, "MISSING_ELEMENT_NAME", sequence, change.clientReference(), "Element name is required", Map.of()));
        }
        validateMetadataShape(sequence, change.clientReference(), draft.metadata(), errors);
        validateParent(workspaceId, sequence, change.clientReference(), null, draft.type(), draft.parentElementId(), draft.parentReference(),
                projectedElements, references, errors);
    }

    private void validateUpdateElement(UUID workspaceId, int sequence, AgentProposalChangeRequest change,
            Map<String, ElementDraft> projectedElements, Set<String> references, List<ProposalValidationIssue> errors) {
        ArchitectureElementEntity existing = targetElement(workspaceId, sequence, change, errors);
        if (change.element() == null || existing == null) {
            return;
        }
        ElementDraft draft = change.element();
        ArchitectureElementType targetType = draft.type() == null ? existing.type : draft.type();
        validateMetadataShape(sequence, change.clientReference(), draft.metadata(), errors);
        if (draft.parentElementId() != null || draft.parentReference() != null) {
            validateParent(workspaceId, sequence, change.clientReference(), existing.id, targetType, draft.parentElementId(),
                    draft.parentReference(), projectedElements, references, errors);
        }
    }

    private void validateCreateRelationship(UUID workspaceId, int sequence, AgentProposalChangeRequest change,
            Map<String, ElementDraft> projectedElements, Set<String> references, List<ProposalValidationIssue> errors) {
        RelationshipDraft draft = change.relationship();
        if (draft == null) {
            errors.add(issue(Severity.ERROR, "MISSING_RELATIONSHIP", sequence, change.clientReference(), "Relationship draft is required", Map.of()));
            return;
        }
        if (draft.type() == null) {
            errors.add(issue(Severity.ERROR, "MISSING_RELATIONSHIP_TYPE", sequence, change.clientReference(), "Relationship type is required", Map.of()));
        }
        validateEndpoint(workspaceId, sequence, change.clientReference(), "source", draft.sourceElementId(), draft.sourceReference(), references, errors);
        validateEndpoint(workspaceId, sequence, change.clientReference(), "target", draft.targetElementId(), draft.targetReference(), references, errors);
        validateNoSelfRelationship(sequence, change.clientReference(), draft.sourceElementId(), draft.sourceReference(), draft.targetElementId(), draft.targetReference(), errors);
    }

    private void validateUpdateRelationship(UUID workspaceId, int sequence, AgentProposalChangeRequest change,
            Map<String, ElementDraft> projectedElements, Set<String> references, List<ProposalValidationIssue> errors) {
        ArchitectureRelationshipEntity existing = targetRelationship(workspaceId, sequence, change, errors);
        RelationshipDraft draft = change.relationship();
        if (existing == null || draft == null) {
            return;
        }
        if (draft.sourceElementId() != null || draft.sourceReference() != null) {
            validateEndpoint(workspaceId, sequence, change.clientReference(), "source", draft.sourceElementId(), draft.sourceReference(), references, errors);
        }
        if (draft.targetElementId() != null || draft.targetReference() != null) {
            validateEndpoint(workspaceId, sequence, change.clientReference(), "target", draft.targetElementId(), draft.targetReference(), references, errors);
        }
    }

    private void validateCreateLink(UUID workspaceId, int sequence, AgentProposalChangeRequest change,
            Map<String, ElementDraft> projectedElements, Set<String> references, List<ProposalValidationIssue> errors) {
        LinkDraft draft = change.link();
        if (draft == null) {
            errors.add(issue(Severity.ERROR, "MISSING_LINK", sequence, change.clientReference(), "External link draft is required", Map.of()));
            return;
        }
        validateEndpoint(workspaceId, sequence, change.clientReference(), "element", draft.elementId(), draft.elementReference(), references, errors);
        if (draft.provider() == null || draft.type() == null) {
            errors.add(issue(Severity.ERROR, "MISSING_LINK_CLASSIFICATION", sequence, change.clientReference(), "Link provider and type are required", Map.of()));
        }
        if (draft.label() == null || draft.label().isBlank()) {
            errors.add(issue(Severity.ERROR, "MISSING_LINK_LABEL", sequence, change.clientReference(), "Link label is required", Map.of()));
        }
        validateUrl(sequence, change.clientReference(), draft.url(), errors);
    }

    private void validateCreateMetadataDefinition(UUID workspaceId, int sequence, AgentProposalChangeRequest change, List<ProposalValidationIssue> errors) {
        MetadataDefinitionDraft draft = change.metadataDefinition();
        if (draft == null) {
            errors.add(issue(Severity.ERROR, "MISSING_METADATA_DEFINITION", sequence, change.clientReference(), "Metadata definition draft is required", Map.of()));
            return;
        }
        if (draft.key() == null || draft.key().isBlank() || draft.label() == null || draft.label().isBlank() || draft.valueType() == null) {
            errors.add(issue(Severity.ERROR, "INVALID_METADATA_DEFINITION", sequence, change.clientReference(),
                    "Metadata definition key, label and value type are required", Map.of()));
        }
        if (draft.key() != null && MetadataDefinitionEntity.count("workspaceId = ?1 and key = ?2", workspaceId, draft.key()) > 0) {
            errors.add(issue(Severity.ERROR, "METADATA_DEFINITION_EXISTS", sequence, change.clientReference(),
                    "A metadata definition with this key already exists", Map.of("key", draft.key())));
        }
    }

    private void validateCreateView(UUID workspaceId, int sequence, AgentProposalChangeRequest change,
            Map<String, ElementDraft> projectedElements, Set<String> references, List<ProposalValidationIssue> errors) {
        ViewDraft draft = change.view();
        if (draft == null) {
            errors.add(issue(Severity.ERROR, "MISSING_VIEW", sequence, change.clientReference(), "Diagram view draft is required", Map.of()));
            return;
        }
        if (draft.name() == null || draft.name().isBlank() || draft.type() == null) {
            errors.add(issue(Severity.ERROR, "INVALID_VIEW", sequence, change.clientReference(), "View name and type are required", Map.of()));
        }
        if (draft.scopeElementId() != null || draft.scopeReference() != null) {
            validateEndpoint(workspaceId, sequence, change.clientReference(), "scope", draft.scopeElementId(), draft.scopeReference(), references, errors);
        }
    }

    private void validateParent(UUID workspaceId, int sequence, String clientReference, UUID elementId, ArchitectureElementType type,
            UUID parentElementId, String parentReference, Map<String, ElementDraft> projectedElements, Set<String> references,
            List<ProposalValidationIssue> errors) {
        if (type == null) {
            return;
        }
        if (parentElementId == null && parentReference == null) {
            if (type == ArchitectureElementType.COMPONENT) {
                errors.add(issue(Severity.ERROR, "INVALID_PARENT", sequence, clientReference, "A component must belong to a container", Map.of()));
            }
            return;
        }
        ArchitectureElementType parentType = parentType(workspaceId, parentElementId, parentReference, projectedElements, references);
        if (parentType == null) {
            errors.add(issue(Severity.ERROR, "UNKNOWN_PARENT", sequence, clientReference, "Parent element could not be resolved", Map.of()));
            return;
        }
        boolean allowed = switch (type) {
            case CONTAINER -> parentType == ArchitectureElementType.SOFTWARE_SYSTEM;
            case COMPONENT -> parentType == ArchitectureElementType.CONTAINER;
            case DATA_STORE -> parentType == ArchitectureElementType.SOFTWARE_SYSTEM || parentType == ArchitectureElementType.CONTAINER;
            case PERSON, SOFTWARE_SYSTEM, EXTERNAL_SYSTEM -> false;
        };
        if (!allowed) {
            errors.add(issue(Severity.ERROR, "INVALID_PARENT", sequence, clientReference, "Parent element type is not valid for " + type, Map.of("parentType", parentType)));
        }
        if (parentElementId != null && parentElementId.equals(elementId)) {
            errors.add(issue(Severity.ERROR, "CIRCULAR_PARENT", sequence, clientReference, "An element cannot be its own parent", Map.of()));
        }
    }

    private void validateEndpoint(UUID workspaceId, int sequence, String clientReference, String fieldName, UUID elementId, String reference,
            Set<String> references, List<ProposalValidationIssue> errors) {
        if (elementId == null && (reference == null || reference.isBlank())) {
            errors.add(issue(Severity.ERROR, "MISSING_REFERENCE", sequence, clientReference, "Missing " + fieldName + " element reference", Map.of("field", fieldName)));
            return;
        }
        if (elementId != null && ArchitectureElementEntity.count("id = ?1 and workspaceId = ?2", elementId, workspaceId) == 0) {
            errors.add(issue(Severity.ERROR, "ELEMENT_NOT_FOUND", sequence, clientReference, fieldName + " element was not found", Map.of("elementId", elementId)));
        }
        if (reference != null && !reference.isBlank() && !references.contains(reference)) {
            errors.add(issue(Severity.ERROR, "UNKNOWN_CLIENT_REFERENCE", sequence, clientReference, fieldName + " reference has not been created earlier in the proposal",
                    Map.of("reference", reference)));
        }
    }

    private void validateNoSelfRelationship(int sequence, String clientReference, UUID sourceId, String sourceReference, UUID targetId,
            String targetReference, List<ProposalValidationIssue> errors) {
        if (sourceId != null && sourceId.equals(targetId)) {
            errors.add(issue(Severity.ERROR, "SELF_RELATIONSHIP_REJECTED", sequence, clientReference, "Self relationships are not supported", Map.of()));
        }
        if (sourceReference != null && sourceReference.equals(targetReference)) {
            errors.add(issue(Severity.ERROR, "SELF_RELATIONSHIP_REJECTED", sequence, clientReference, "Self relationships are not supported", Map.of()));
        }
    }

    private void validateMetadataShape(int sequence, String clientReference, Map<String, Object> metadata, List<ProposalValidationIssue> errors) {
        if (metadata == null || metadata.isEmpty()) {
            return;
        }
        for (String key : metadata.keySet()) {
            if (!List.of("ownership", "classification", "lifecycle", "operations", "security", "delivery", "custom", "responsibilities", "importSource").contains(key)) {
                errors.add(issue(Severity.ERROR, "INVALID_METADATA", sequence, clientReference, "Metadata section is not supported: " + key, Map.of("section", key)));
            }
        }
    }

    private void validateUrl(int sequence, String clientReference, String url, List<ProposalValidationIssue> errors) {
        if (url == null || url.isBlank()) {
            errors.add(issue(Severity.ERROR, "MISSING_URL", sequence, clientReference, "External link URL is required", Map.of()));
            return;
        }
        try {
            URI parsed = URI.create(url);
            if (parsed.getScheme() == null || parsed.getHost() == null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException ex) {
            errors.add(issue(Severity.ERROR, "INVALID_URL", sequence, clientReference, "External link URL is invalid", Map.of("url", url)));
        }
    }

    private ArchitectureElementEntity targetElement(UUID workspaceId, int sequence, AgentProposalChangeRequest change,
            List<ProposalValidationIssue> errors) {
        if (change.targetEntityId() == null) {
            errors.add(issue(Severity.ERROR, "MISSING_TARGET", sequence, change.clientReference(), "Target element id is required", Map.of()));
            return null;
        }
        ArchitectureElementEntity entity = ArchitectureElementEntity.findById(change.targetEntityId());
        if (entity == null || !entity.workspaceId.equals(workspaceId)) {
            errors.add(issue(Severity.ERROR, "ELEMENT_NOT_FOUND", sequence, change.clientReference(), "Target element was not found", Map.of("elementId", change.targetEntityId())));
            return null;
        }
        return entity;
    }

    private ArchitectureRelationshipEntity targetRelationship(UUID workspaceId, int sequence, AgentProposalChangeRequest change,
            List<ProposalValidationIssue> errors) {
        if (change.targetEntityId() == null) {
            errors.add(issue(Severity.ERROR, "MISSING_TARGET", sequence, change.clientReference(), "Target relationship id is required", Map.of()));
            return null;
        }
        ArchitectureRelationshipEntity entity = ArchitectureRelationshipEntity.findById(change.targetEntityId());
        if (entity == null || !entity.workspaceId.equals(workspaceId)) {
            errors.add(issue(Severity.ERROR, "RELATIONSHIP_NOT_FOUND", sequence, change.clientReference(), "Target relationship was not found", Map.of("relationshipId", change.targetEntityId())));
            return null;
        }
        return entity;
    }

    private ArchitectureElementType parentType(UUID workspaceId, UUID parentElementId, String parentReference,
            Map<String, ElementDraft> projectedElements, Set<String> references) {
        if (parentElementId != null) {
            ArchitectureElementEntity parent = ArchitectureElementEntity.findById(parentElementId);
            return parent != null && parent.workspaceId.equals(workspaceId) ? parent.type : null;
        }
        if (parentReference != null && references.contains(parentReference)) {
            ElementDraft projected = projectedElements.get(parentReference);
            return projected == null ? null : projected.type();
        }
        return null;
    }

    private AgentProposalRequest normalize(AgentProposalRequest request) {
        if (request == null) {
            return new AgentProposalRequest(Map.of(), "", List.of());
        }
        return new AgentProposalRequest(map(request.source()), value(request.summary()),
                request.changes() == null ? List.of() : request.changes());
    }

    private AgentProposalRequest requestFrom(AgentProposalEntity proposal, List<AgentProposalChangeEntity> changes) {
        return new AgentProposalRequest(proposal.source, proposal.summary,
                changes.stream().map(change -> fromPayload(change.payload)).toList());
    }

    private AgentProposalChangeRequest fromPayload(Map<String, Object> payload) {
        return objectMapper.convertValue(payload, AgentProposalChangeRequest.class);
    }

    private AgentProposalSummaryResponse summaryResponse(AgentProposalEntity proposal) {
        int changeCount = (int) AgentProposalChangeEntity.count("proposalId", proposal.id);
        return new AgentProposalSummaryResponse(proposal.id, proposal.workspaceId, proposal.status, proposal.summary, proposal.source,
                validation(proposal.validation), proposal.createdAt, proposal.updatedAt, proposal.appliedAt, proposal.rejectedAt, changeCount);
    }

    private AgentProposalResponse response(AgentProposalEntity proposal) {
        return new AgentProposalResponse(proposal.id, proposal.workspaceId, proposal.status, proposal.summary, proposal.source,
                validation(proposal.validation), proposal.createdAt, proposal.updatedAt, proposal.appliedAt, proposal.rejectedAt,
                changes(proposal.id).stream().map(this::changeResponse).toList());
    }

    private AgentProposalChangeResponse changeResponse(AgentProposalChangeEntity change) {
        return new AgentProposalChangeResponse(change.id, change.sequenceNumber, change.action, change.status, change.clientReference,
                change.targetEntityType, change.targetEntityId, change.resultEntityId, change.payload, change.evidence, validation(change.validation));
    }

    private ProposalValidationResult validation(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return new ProposalValidationResult(true, List.of(), List.of(), Map.of());
        }
        return objectMapper.convertValue(map, ProposalValidationResult.class);
    }

    private Map<String, Object> toMap(Object value) {
        return objectMapper.convertValue(value, new TypeReference<>() {
        });
    }

    private List<Map<String, Object>> evidence(List<Map<String, Object>> source) {
        return source == null ? new ArrayList<>() : source.stream().map(AgentProposalService::map).toList();
    }

    private WorkspaceEntity workspace(UUID workspaceId) {
        WorkspaceEntity entity = WorkspaceEntity.findById(workspaceId);
        if (entity == null) {
            throw new ApiException(Response.Status.NOT_FOUND, "WORKSPACE_NOT_FOUND", "Workspace was not found");
        }
        return entity;
    }

    private AgentProposalEntity proposal(UUID workspaceId, UUID proposalId) {
        AgentProposalEntity entity = AgentProposalEntity.findById(proposalId);
        if (entity == null || !entity.workspaceId.equals(workspaceId)) {
            throw new ApiException(Response.Status.NOT_FOUND, "PROPOSAL_NOT_FOUND", "Agent proposal was not found");
        }
        return entity;
    }

    private List<AgentProposalChangeEntity> changes(UUID proposalId) {
        return AgentProposalChangeEntity.<AgentProposalChangeEntity>list("proposalId = ?1 order by sequenceNumber", proposalId);
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

    private UUID resolveNullableElement(UUID elementId, String reference, Map<String, UUID> references) {
        if (elementId != null) {
            return elementId;
        }
        return reference == null ? null : references.get(reference);
    }

    private UUID resolveElement(UUID elementId, String reference, Map<String, UUID> references) {
        UUID resolved = resolveNullableElement(elementId, reference, references);
        if (resolved == null) {
            throw new ApiException(Response.Status.BAD_REQUEST, "UNKNOWN_CLIENT_REFERENCE", "Element reference could not be resolved");
        }
        return resolved;
    }

    private static String targetType(AgentProposalChangeAction action) {
        return switch (action) {
            case CREATE_ELEMENT, UPDATE_ELEMENT -> "ELEMENT";
            case CREATE_RELATIONSHIP, UPDATE_RELATIONSHIP -> "RELATIONSHIP";
            case CREATE_LINK -> "LINK";
            case CREATE_METADATA_DEFINITION -> "METADATA_DEFINITION";
            case CREATE_VIEW -> "VIEW";
        };
    }

    private static ProposalValidationIssue issue(Severity severity, String code, Integer sequenceNumber, String clientReference,
            String message, Map<String, Object> details) {
        return new ProposalValidationIssue(severity, code, sequenceNumber, clientReference, message, details);
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
}
