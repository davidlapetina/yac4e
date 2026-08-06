package com.example.c4editor.application;

import com.example.c4editor.api.AgentDtos.AgentElement;
import com.example.c4editor.api.AgentDtos.AgentLimits;
import com.example.c4editor.api.AgentDtos.AgentRelationship;
import com.example.c4editor.api.AgentDtos.AgentTrace;
import com.example.c4editor.api.AgentDtos.ArchitectureQueryRequest;
import com.example.c4editor.api.AgentDtos.ArchitectureQueryResponse;
import com.example.c4editor.api.AgentDtos.ArchitectureQueryResult;
import com.example.c4editor.api.AgentDtos.BatchContextRequest;
import com.example.c4editor.api.AgentDtos.DependencyResponse;
import com.example.c4editor.api.AgentDtos.ElementContext;
import com.example.c4editor.api.AgentDtos.ExternalResourceResponse;
import com.example.c4editor.api.AgentDtos.ExternalResourcesPage;
import com.example.c4editor.api.AgentDtos.GraphEnvelope;
import com.example.c4editor.api.AgentDtos.GraphPath;
import com.example.c4editor.api.AgentDtos.ImpactAnalysisRequest;
import com.example.c4editor.api.AgentDtos.ImpactAnalysisResponse;
import com.example.c4editor.api.AgentDtos.LlmContextRequest;
import com.example.c4editor.api.AgentDtos.LlmContextResponse;
import com.example.c4editor.api.AgentDtos.QueryFilters;
import com.example.c4editor.api.AgentDtos.QueryInclude;
import com.example.c4editor.api.AgentDtos.ResolveReferenceMatch;
import com.example.c4editor.api.AgentDtos.ResolveReferenceRequest;
import com.example.c4editor.api.AgentDtos.ResolveReferenceResponse;
import com.example.c4editor.api.AgentDtos.ValidationQueryRequest;
import com.example.c4editor.api.AgentDtos.ValidationQueryResponse;
import com.example.c4editor.api.AgentDtos.WorkspaceSummary;
import com.example.c4editor.api.ApiException;
import com.example.c4editor.api.Dtos.LinkResponse;
import com.example.c4editor.api.Dtos.ValidationIssue;
import com.example.c4editor.application.auth.AuthRequestContext;
import com.example.c4editor.domain.ArchitectureElementType;
import com.example.c4editor.domain.DependencyDirection;
import com.example.c4editor.domain.LlmContextFormat;
import com.example.c4editor.domain.RelationshipType;
import com.example.c4editor.domain.Severity;
import com.example.c4editor.persistence.ArchitectureElementEntity;
import com.example.c4editor.persistence.ArchitectureRelationshipEntity;
import com.example.c4editor.persistence.DiagramViewEntity;
import com.example.c4editor.persistence.ExternalLinkEntity;
import com.example.c4editor.persistence.WorkspaceEntity;
import com.example.c4editor.validation.ValidationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class AgentQueryService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_ELEMENTS = 500;
    private static final int MAX_RELATIONSHIPS = 1_000;
    private static final int MAX_DEPTH = 5;
    private static final int MAX_LLM_CHARS = 100_000;

    @Inject
    WorkspaceAccessService access;

    @Inject
    AuthRequestContext authContext;

    @Inject
    ValidationService validationService;

    @Inject
    ObjectMapper objectMapper;

    public WorkspaceSummary summary(UUID workspaceId) {
        WorkspaceEntity workspace = requireRead(workspaceId);
        List<ArchitectureElementEntity> elements = elements(workspaceId);
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("people", count(elements, ArchitectureElementType.PERSON));
        counts.put("softwareSystems", count(elements, ArchitectureElementType.SOFTWARE_SYSTEM));
        counts.put("containers", count(elements, ArchitectureElementType.CONTAINER));
        counts.put("components", count(elements, ArchitectureElementType.COMPONENT));
        counts.put("dataStores", count(elements, ArchitectureElementType.DATA_STORE));
        counts.put("externalSystems", count(elements, ArchitectureElementType.EXTERNAL_SYSTEM));
        counts.put("relationships", ArchitectureRelationshipEntity.count("workspaceId", workspaceId));
        counts.put("views", DiagramViewEntity.count("workspaceId", workspaceId));
        var validation = validationService.validate(workspaceId);
        return new WorkspaceSummary(Mapper.workspace(workspace), counts, distinctMetadata(elements, "classification", "domain"),
                elements.stream().map(e -> e.technology).filter(Objects::nonNull).filter(s -> !s.isBlank()).distinct().sorted().toList(),
                Map.of("errors", validation.errors().size(), "warnings", validation.warnings().size()), workspace.updatedAt,
                trace(workspace, List.of(), List.of(), Map.of(), 0), false);
    }

    public ElementContext elementContext(UUID workspaceId, UUID elementId, boolean includeParents, boolean includeChildren,
            boolean includeIncoming, boolean includeOutgoing, boolean includeLinks, boolean includeMetadata, boolean includeViews,
            boolean includeValidation, int depth, int maxRelationships) {
        WorkspaceEntity workspace = requireRead(workspaceId);
        checkDepth(depth);
        checkRelationshipLimit(maxRelationships);
        ArchitectureElementEntity root = element(workspaceId, elementId);
        Set<UUID> includedRelationships = new LinkedHashSet<>();
        List<AgentRelationship> incoming = includeIncoming ? related(workspaceId, elementId, DependencyDirection.INCOMING, Set.of(), maxRelationships, includedRelationships) : List.of();
        List<AgentRelationship> outgoing = includeOutgoing ? related(workspaceId, elementId, DependencyDirection.OUTGOING, Set.of(), maxRelationships, includedRelationships) : List.of();
        List<AgentElement> parents = includeParents ? parents(root, includeMetadata, depth) : List.of();
        List<AgentElement> children = includeChildren ? children(workspaceId, elementId, includeMetadata, depth, MAX_ELEMENTS) : List.of();
        List<LinkResponse> links = includeLinks ? links(elementId) : List.of();
        List<ValidationIssue> issues = includeValidation ? validationIssues(workspaceId).stream().filter(i -> elementId.equals(i.elementId())).toList() : List.of();
        List<com.example.c4editor.api.Dtos.ViewResponse> views = includeViews ? MapperViews.viewsForElement(workspaceId, elementId) : List.of();
        List<UUID> elementIds = new ArrayList<>();
        elementIds.add(elementId);
        parents.forEach(e -> elementIds.add(e.id()));
        children.forEach(e -> elementIds.add(e.id()));
        return new ElementContext(agentElement(root, includeMetadata), parents, children, incoming, outgoing, links, views, issues,
                trace(workspace, elementIds, new ArrayList<>(includedRelationships), Map.of("elementId", elementId), depth),
                new AgentLimits(MAX_ELEMENTS, maxRelationships), false);
    }

    public GraphEnvelope batchContext(UUID workspaceId, BatchContextRequest request) {
        WorkspaceEntity workspace = requireRead(workspaceId);
        int maxElements = limit(request.maximumElements(), MAX_ELEMENTS, "maximumElements");
        int maxRelationships = limit(request.maximumRelationships(), MAX_RELATIONSHIPS, "maximumRelationships");
        int depth = depth(request.relationshipDepth());
        boolean includeMetadata = request.include() == null || request.include().metadata();
        Set<UUID> elementIds = new LinkedHashSet<>(request.elementIds() == null ? List.of() : request.elementIds());
        Set<UUID> relationshipIds = new LinkedHashSet<>();
        List<GraphPath> paths = new ArrayList<>();
        boolean truncated = false;
        if (request.include() == null || request.include().parents()) {
            for (UUID id : List.copyOf(elementIds)) parents(element(workspaceId, id), includeMetadata, 5).forEach(e -> elementIds.add(e.id()));
        }
        if (request.include() == null || request.include().children()) {
            for (UUID id : List.copyOf(elementIds)) children(workspaceId, id, includeMetadata, 5, maxElements).forEach(e -> elementIds.add(e.id()));
        }
        if (request.include() == null || request.include().relationships()) {
            for (UUID id : List.copyOf(elementIds)) {
                Traversal traversal = traverse(workspaceId, id, DependencyDirection.BOTH, depth, Set.of(), maxElements, maxRelationships);
                elementIds.addAll(traversal.elementIds);
                relationshipIds.addAll(traversal.relationshipIds);
                paths.addAll(traversal.paths);
                truncated |= traversal.truncated;
            }
        }
        truncated |= elementIds.size() > maxElements || relationshipIds.size() > maxRelationships;
        List<AgentElement> resultElements = elementIds.stream().limit(maxElements).map(id -> agentElement(element(workspaceId, id), includeMetadata)).toList();
        List<AgentRelationship> resultRelationships = relationshipIds.stream().limit(maxRelationships).map(id -> agentRelationship(relationship(workspaceId, id), includeMetadata)).toList();
        List<LinkResponse> links = request.include() != null && request.include().externalLinks()
                ? elementIds.stream().flatMap(id -> links(id).stream()).toList()
                : List.of();
        List<ValidationIssue> issues = request.include() == null || request.include().validation()
                ? validationIssues(workspaceId).stream().filter(i -> i.elementId() != null && elementIds.contains(i.elementId())).toList()
                : List.of();
        return new GraphEnvelope(resultElements, resultRelationships, paths, links, issues,
                trace(workspace, resultElements.stream().map(AgentElement::id).toList(), resultRelationships.stream().map(AgentRelationship::id).toList(),
                        Map.of("elementIds", request.elementIds() == null ? List.of() : request.elementIds()), depth),
                new AgentLimits(maxElements, maxRelationships), truncated);
    }

    public ArchitectureQueryResponse query(UUID workspaceId, ArchitectureQueryRequest request) {
        WorkspaceEntity workspace = requireRead(workspaceId);
        int page = Math.max(0, request.page() == null ? 0 : request.page());
        int pageSize = pageSize(request.pageSize());
        QueryFilters filters = request.filters();
        QueryInclude include = request.include();
        boolean includeMetadata = include == null || include.metadata();
        boolean includeRelationships = include != null && include.relationships();
        boolean includeLinks = include != null && include.links();
        boolean includeValidation = include != null && include.validation();
        // Resolve the shared collections once. Doing this per element turned a single query into
        // one full validation pass and one relationship scan for every matched element.
        List<ArchitectureRelationshipEntity> workspaceRelationships = includeRelationships ? relationships(workspaceId) : List.of();
        Map<UUID, List<ValidationIssue>> issuesByElement = includeValidation
                ? validationIssues(workspaceId).stream().filter(i -> i.elementId() != null).collect(Collectors.groupingBy(ValidationIssue::elementId))
                : Map.of();
        List<ArchitectureQueryResult> matched = elements(workspaceId).stream()
                .filter(e -> matchesFilters(e, filters))
                .map(e -> new ArchitectureQueryResult(agentElement(e, includeMetadata), matchedFields(e, request.text(), filters),
                        includeRelationships ? workspaceRelationships.stream()
                                .filter(r -> r.sourceElementId.equals(e.id) || r.targetElementId.equals(e.id))
                                .map(r -> agentRelationship(r, includeMetadata)).toList() : List.of(),
                        includeLinks ? links(e.id) : List.of(),
                        includeValidation ? issuesByElement.getOrDefault(e.id, List.of()) : List.of()))
                .filter(r -> request.text() == null || request.text().isBlank() || !r.matchedFields().isEmpty())
                .sorted(Comparator.comparing(r -> r.element().name()))
                .toList();
        List<ArchitectureQueryResult> pageItems = matched.stream().skip((long) page * pageSize).limit(pageSize).toList();
        return new ArchitectureQueryResponse(pageItems, page, pageSize, matched.size(),
                trace(workspace, pageItems.stream().map(r -> r.element().id()).toList(),
                        pageItems.stream().flatMap(r -> r.relationships().stream().map(AgentRelationship::id)).toList(),
                        Map.of("filters", filters == null ? Map.of() : filters), 0), matched.size() > (long) page * pageSize + pageItems.size());
    }

    public DependencyResponse dependencies(UUID workspaceId, UUID elementId, DependencyDirection direction, int depth,
            Set<RelationshipType> relationshipTypes, boolean includeExternalSystems, int maximumElements) {
        WorkspaceEntity workspace = requireRead(workspaceId);
        checkDepth(depth);
        int maxElements = limit(maximumElements, MAX_ELEMENTS, "maximumElements");
        element(workspaceId, elementId);
        Set<RelationshipType> types = relationshipTypes == null ? Set.of() : relationshipTypes;
        Traversal traversal = traverse(workspaceId, elementId, direction == null ? DependencyDirection.OUTGOING : direction, depth, types, maxElements, MAX_RELATIONSHIPS);
        List<AgentElement> resultElements = traversal.elementIds.stream()
                .map(id -> element(workspaceId, id))
                .filter(e -> includeExternalSystems || e.type != ArchitectureElementType.EXTERNAL_SYSTEM)
                .map(e -> agentElement(e, true))
                .toList();
        // Relationships and paths must not reference elements that the filter above removed,
        // otherwise the response contains dangling endpoint ids.
        Set<UUID> retainedElementIds = resultElements.stream().map(AgentElement::id).collect(Collectors.toCollection(LinkedHashSet::new));
        retainedElementIds.add(elementId);
        List<AgentRelationship> resultRelationships = traversal.relationshipIds.stream()
                .map(id -> relationship(workspaceId, id))
                .filter(r -> retainedElementIds.contains(r.sourceElementId) && retainedElementIds.contains(r.targetElementId))
                .map(r -> agentRelationship(r, true))
                .toList();
        List<GraphPath> resultPaths = traversal.paths.stream().filter(p -> retainedElementIds.containsAll(p.elementIds())).toList();
        List<UUID> traceElementIds = new ArrayList<>();
        traceElementIds.add(elementId);
        resultElements.stream().map(AgentElement::id).filter(id -> !traceElementIds.contains(id)).forEach(traceElementIds::add);
        return new DependencyResponse(elementId, direction == null ? DependencyDirection.OUTGOING : direction, depth, resultElements,
                resultRelationships, resultPaths, trace(workspace, traceElementIds,
                resultRelationships.stream().map(AgentRelationship::id).toList(), Map.of("relationshipTypes", types), depth),
                new AgentLimits(maxElements, MAX_RELATIONSHIPS), traversal.truncated);
    }

    public ImpactAnalysisResponse impact(UUID workspaceId, ImpactAnalysisRequest request) {
        WorkspaceEntity workspace = requireRead(workspaceId);
        int depth = depth(request.maximumDepth());
        Set<RelationshipType> types = request.relationshipTypes() == null ? Set.of() : new HashSet<>(request.relationshipTypes());
        Set<DependencyDirection> directions = request.directions() == null || request.directions().isEmpty()
                ? Set.of(DependencyDirection.INCOMING, DependencyDirection.OUTGOING)
                : new HashSet<>(request.directions());
        Set<UUID> direct = new LinkedHashSet<>();
        Set<UUID> transitive = new LinkedHashSet<>();
        Set<UUID> relationships = new LinkedHashSet<>();
        List<GraphPath> paths = new ArrayList<>();
        boolean truncated = false;
        for (UUID changed : request.changedElementIds() == null ? List.<UUID>of() : request.changedElementIds()) {
            element(workspaceId, changed);
            for (DependencyDirection direction : directions) {
                Traversal traversal = traverse(workspaceId, changed, direction, depth, types, MAX_ELEMENTS, MAX_RELATIONSHIPS);
                for (GraphPath path : traversal.paths) {
                    if (path.elementIds().size() > 1) {
                        UUID impacted = path.elementIds().get(1);
                        direct.add(impacted);
                        if (path.elementIds().size() > 2) {
                            transitive.add(path.elementIds().get(path.elementIds().size() - 1));
                        }
                    }
                }
                relationships.addAll(traversal.relationshipIds);
                paths.addAll(traversal.paths);
                truncated |= traversal.truncated;
            }
        }
        List<AgentElement> changedElements = safeIds(request.changedElementIds()).stream().map(id -> agentElement(element(workspaceId, id), true)).toList();
        List<AgentElement> directElements = direct.stream().filter(id -> !safeIds(request.changedElementIds()).contains(id)).map(id -> agentElement(element(workspaceId, id), true)).toList();
        List<AgentElement> transitiveElements = transitive.stream().filter(id -> !direct.contains(id)).filter(id -> !safeIds(request.changedElementIds()).contains(id)).map(id -> agentElement(element(workspaceId, id), true)).toList();
        Set<UUID> impactedIds = new LinkedHashSet<>();
        directElements.forEach(e -> impactedIds.add(e.id()));
        transitiveElements.forEach(e -> impactedIds.add(e.id()));
        List<String> owners = request.includeOwners() ? impactedIds.stream().map(id -> textAt(element(workspaceId, id).metadata, "ownership", "team")).filter(Objects::nonNull).distinct().sorted().toList() : List.of();
        List<com.example.c4editor.api.Dtos.ViewResponse> views = request.includeViews() ? impactedIds.stream().flatMap(id -> MapperViews.viewsForElement(workspaceId, id).stream()).distinct().toList() : List.of();
        List<LinkResponse> links = request.includeLinks() ? impactedIds.stream().flatMap(id -> links(id).stream()).toList() : List.of();
        return new ImpactAnalysisResponse(changedElements, directElements, transitiveElements, owners, views, links, paths,
                List.of("Impact analysis is derived only from stored architecture relationships."),
                trace(workspace, impactedIds.stream().toList(), relationships.stream().toList(), Map.of("directions", directions), depth),
                new AgentLimits(MAX_ELEMENTS, MAX_RELATIONSHIPS), truncated);
    }

    public ValidationQueryResponse validationQuery(UUID workspaceId, ValidationQueryRequest request) {
        WorkspaceEntity workspace = requireRead(workspaceId);
        int page = Math.max(0, request.page() == null ? 0 : request.page());
        int pageSize = pageSize(request.pageSize());
        List<ValidationIssue> all = validationIssues(workspaceId).stream()
                .filter(i -> request.severities() == null || request.severities().isEmpty() || request.severities().contains(i.severity()))
                .filter(i -> request.ruleCodes() == null || request.ruleCodes().isEmpty() || request.ruleCodes().contains(i.code()))
                .filter(i -> request.elementTypes() == null || request.elementTypes().isEmpty() || (i.elementId() != null && request.elementTypes().contains(element(workspaceId, i.elementId()).type)))
                .filter(i -> request.lifecycleStatuses() == null || request.lifecycleStatuses().isEmpty() || (i.elementId() != null && request.lifecycleStatuses().contains(textAt(element(workspaceId, i.elementId()).metadata, "lifecycle", "status"))))
                .toList();
        List<ValidationIssue> items = all.stream().skip((long) page * pageSize).limit(pageSize).toList();
        return new ValidationQueryResponse(items, page, pageSize, all.size(), trace(workspace, items.stream().map(ValidationIssue::elementId).filter(Objects::nonNull).toList(), List.of(), Map.of(), 0), all.size() > (long) page * pageSize + items.size());
    }

    public ExternalResourcesPage externalResources(UUID workspaceId, String provider, String type, String externalId, String url,
            UUID elementId, String search, int page, int pageSize) {
        WorkspaceEntity workspace = requireRead(workspaceId);
        int size = pageSize(pageSize);
        int p = Math.max(0, page);
        List<ExternalResourceResponse> all = new ArrayList<>();
        for (ExternalLinkEntity link : ExternalLinkEntity.<ExternalLinkEntity>listAll()) {
            ArchitectureElementEntity element = ArchitectureElementEntity.findById(link.elementId);
            if (element == null || !element.workspaceId.equals(workspaceId)) continue;
            if (provider != null && !provider.equals(link.provider.name())) continue;
            if (type != null && !type.equals(link.type.name())) continue;
            if (externalId != null && !externalId.equals(link.externalId)) continue;
            if (url != null && !normalize(url).equals(normalize(link.url))) continue;
            if (elementId != null && !elementId.equals(link.elementId)) continue;
            if (search != null && !search.isBlank() && !(contains(link.label, search) || contains(link.url, search) || contains(link.externalId, search) || contains(element.name, search))) continue;
            all.add(new ExternalResourceResponse(Mapper.link(link), agentElement(element, false), workspaceId, element.type, element.name));
        }
        List<ExternalResourceResponse> items = all.stream().skip((long) p * size).limit(size).toList();
        return new ExternalResourcesPage(items, p, size, all.size(), trace(workspace, items.stream().map(r -> r.element().id()).toList(), List.of(), Map.of(), 0), all.size() > (long) p * size + items.size());
    }

    public ResolveReferenceResponse resolveReference(UUID workspaceId, ResolveReferenceRequest request) {
        WorkspaceEntity workspace = requireRead(workspaceId);
        String ref = request.reference() == null ? "" : request.reference().trim();
        String normalized = normalize(ref);
        List<ResolveReferenceMatch> matches = new ArrayList<>();
        for (ExternalLinkEntity link : ExternalLinkEntity.<ExternalLinkEntity>listAll()) {
            ArchitectureElementEntity element = ArchitectureElementEntity.findById(link.elementId);
            if (element == null || !element.workspaceId.equals(workspaceId)) continue;
            String matchType = null;
            if (ref.equals(link.externalId)) matchType = "EXACT_EXTERNAL_ID";
            else if (ref.equals(link.url)) matchType = "EXACT_URL";
            else if (normalized.equals(normalize(link.url))) matchType = "NORMALIZED_URL";
            else if (ref.equalsIgnoreCase(link.label)) matchType = "EXACT_LABEL";
            if (matchType != null) matches.add(new ResolveReferenceMatch(Mapper.link(link), agentElement(element, false), 1.0, matchType));
        }
        return new ResolveReferenceResponse(matches, trace(workspace, matches.stream().map(m -> m.element().id()).toList(), List.of(), Map.of("reference", ref), 0), false);
    }

    public LlmContextResponse llmContext(UUID workspaceId, LlmContextRequest request) {
        int maxChars = limit(request.maximumCharacters(), MAX_LLM_CHARS, "maximumCharacters");
        GraphEnvelope graph = batchContext(workspaceId, new BatchContextRequest(request.elementIds(), null, depth(request.relationshipDepth()), MAX_ELEMENTS, MAX_RELATIONSHIPS));
        String content = renderContext(request.format() == null ? LlmContextFormat.MARKDOWN : request.format(), request.query(), graph, request.includeMetadata(), request.includeLinks(), request.includeValidation());
        boolean truncated = graph.truncated() || content.length() > maxChars;
        if (content.length() > maxChars) {
            // Keep the result within maximumCharacters: reserving a fixed 32 chars overshot the
            // budget whenever the caller asked for a small limit.
            String marker = "\n[TRUNCATED]";
            content = maxChars <= marker.length()
                    ? content.substring(0, maxChars)
                    : content.substring(0, maxChars - marker.length()) + marker;
        }
        return new LlmContextResponse(request.format() == null ? LlmContextFormat.MARKDOWN : request.format(), content,
                graph.trace().includedElementIds(), graph.trace().includedRelationshipIds(), truncated, graph.trace());
    }

    private Traversal traverse(UUID workspaceId, UUID root, DependencyDirection direction, int maxDepth, Set<RelationshipType> types, int maxElements, int maxRelationships) {
        Set<UUID> elementIds = new LinkedHashSet<>();
        Set<UUID> relationshipIds = new LinkedHashSet<>();
        List<GraphPath> paths = new ArrayList<>();
        ArrayDeque<PathState> queue = new ArrayDeque<>();
        queue.add(new PathState(root, List.of(root), List.of(), 0));
        boolean truncated = false;
        while (!queue.isEmpty()) {
            PathState state = queue.removeFirst();
            if (state.depth >= maxDepth) continue;
            for (ArchitectureRelationshipEntity relationship : adjacent(workspaceId, state.elementId, direction, types)) {
                UUID next = relationship.sourceElementId.equals(state.elementId) ? relationship.targetElementId : relationship.sourceElementId;
                if (state.elementPath.contains(next)) continue;
                List<UUID> nextElementPath = append(state.elementPath, next);
                List<UUID> nextRelationshipPath = append(state.relationshipPath, relationship.id);
                elementIds.add(next);
                relationshipIds.add(relationship.id);
                paths.add(new GraphPath(nextElementPath, nextRelationshipPath));
                if (elementIds.size() > maxElements || relationshipIds.size() > maxRelationships) {
                    truncated = true;
                    return new Traversal(elementIds.stream().limit(maxElements).collect(Collectors.toCollection(LinkedHashSet::new)),
                            relationshipIds.stream().limit(maxRelationships).collect(Collectors.toCollection(LinkedHashSet::new)), paths, true);
                }
                queue.add(new PathState(next, nextElementPath, nextRelationshipPath, state.depth + 1));
            }
        }
        return new Traversal(elementIds, relationshipIds, paths, truncated);
    }

    private List<ArchitectureRelationshipEntity> adjacent(UUID workspaceId, UUID elementId, DependencyDirection direction, Set<RelationshipType> types) {
        return relationships(workspaceId).stream()
                .filter(r -> types == null || types.isEmpty() || types.contains(r.type))
                .filter(r -> switch (direction) {
                    case INCOMING -> r.targetElementId.equals(elementId);
                    case OUTGOING -> r.sourceElementId.equals(elementId);
                    case BOTH -> r.sourceElementId.equals(elementId) || r.targetElementId.equals(elementId);
                })
                .sorted(Comparator.comparing(r -> r.id.toString()))
                .toList();
    }

    private AgentElement agentElement(ArchitectureElementEntity entity, boolean includeMetadata) {
        return new AgentElement(entity.id, entity.type, entity.name, entity.description, entity.technology, entity.parentElementId,
                includeMetadata ? entity.metadata : Map.of(), responsibilities(entity.metadata));
    }

    private AgentRelationship agentRelationship(ArchitectureRelationshipEntity entity, boolean includeMetadata) {
        return new AgentRelationship(entity.id, entity.sourceElementId, entity.targetElementId, entity.type, entity.description,
                entity.technology, entity.protocol, includeMetadata ? entity.metadata : Map.of());
    }

    private List<AgentRelationship> related(UUID workspaceId, UUID elementId, DependencyDirection direction, Set<RelationshipType> types, int limit, Set<UUID> included) {
        return adjacent(workspaceId, elementId, direction, types).stream().limit(limit).peek(r -> included.add(r.id)).map(r -> agentRelationship(r, true)).toList();
    }

    private List<AgentRelationship> allRelated(UUID workspaceId, UUID elementId, boolean includeMetadata) {
        return relationships(workspaceId).stream().filter(r -> r.sourceElementId.equals(elementId) || r.targetElementId.equals(elementId)).map(r -> agentRelationship(r, includeMetadata)).toList();
    }

    private List<AgentElement> parents(ArchitectureElementEntity root, boolean includeMetadata, int depth) {
        List<AgentElement> parents = new ArrayList<>();
        UUID cursor = root.parentElementId;
        int remaining = depth;
        while (cursor != null && remaining-- > 0) {
            ArchitectureElementEntity parent = ArchitectureElementEntity.findById(cursor);
            if (parent == null) break;
            parents.add(agentElement(parent, includeMetadata));
            cursor = parent.parentElementId;
        }
        return parents;
    }

    private List<AgentElement> children(UUID workspaceId, UUID root, boolean includeMetadata, int depth, int maxElements) {
        List<AgentElement> result = new ArrayList<>();
        // Index the hierarchy once instead of rescanning every element for each queue entry, and
        // track visited ids so a cyclic parent chain cannot emit the same child repeatedly.
        Map<UUID, List<ArchitectureElementEntity>> childrenByParent = elements(workspaceId).stream()
                .filter(e -> e.parentElementId != null)
                .collect(Collectors.groupingBy(e -> e.parentElementId));
        Set<UUID> visited = new HashSet<>();
        visited.add(root);
        ArrayDeque<PathState> queue = new ArrayDeque<>();
        queue.add(new PathState(root, List.of(root), List.of(), 0));
        while (!queue.isEmpty() && result.size() < maxElements) {
            PathState state = queue.removeFirst();
            if (state.depth >= depth) continue;
            for (ArchitectureElementEntity child : childrenByParent.getOrDefault(state.elementId, List.of())) {
                if (!visited.add(child.id)) continue;
                result.add(agentElement(child, includeMetadata));
                if (result.size() >= maxElements) break;
                queue.add(new PathState(child.id, append(state.elementPath, child.id), List.of(), state.depth + 1));
            }
        }
        return result;
    }

    private boolean matchesFilters(ArchitectureElementEntity e, QueryFilters f) {
        if (f == null) return true;
        return (f.elementTypes() == null || f.elementTypes().isEmpty() || f.elementTypes().contains(e.type))
                && containsAny(textAt(e.metadata, "classification", "domain"), f.domains())
                && containsAny(e.technology, f.technologies())
                && containsAny(textAt(e.metadata, "lifecycle", "status"), f.lifecycleStatuses())
                && containsAny(textAt(e.metadata, "classification", "criticality"), f.criticalities())
                && tagsContain(e.metadata, f.tags())
                && matchesBoolean(textAt(e.metadata, "security", "internetExposed"), f.internetExposed())
                && matchesBoolean(textAt(e.metadata, "security", "storesPersonalData"), f.storesPersonalData())
                && matchesPresence(textAt(e.metadata, "ownership", "technicalOwner"), f.hasOwner())
                && matchesPresence(textAt(e.metadata, "operations", "runbookUrl"), f.hasRunbook());
    }

    private List<String> matchedFields(ArchitectureElementEntity e, String text, QueryFilters filters) {
        List<String> fields = new ArrayList<>();
        if (text != null && !text.isBlank()) {
            if (contains(e.name, text)) fields.add("name");
            if (contains(e.description, text)) fields.add("description");
            if (contains(e.technology, text)) fields.add("technology");
            if (contains(e.metadata.toString(), text)) fields.add("metadata");
        }
        if (filters != null && filters.tags() != null && !filters.tags().isEmpty() && tagsContain(e.metadata, filters.tags())) {
            fields.add("metadata.classification.tags");
        }
        return fields;
    }

    private String renderContext(LlmContextFormat format, String query, GraphEnvelope graph, boolean includeMetadata, boolean includeLinks, boolean includeValidation) {
        if (format == LlmContextFormat.JSON) {
            try {
                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(graph);
            } catch (JsonProcessingException ex) {
                throw new ApiException(Response.Status.INTERNAL_SERVER_ERROR, "LLM_CONTEXT_RENDER_FAILED", "Unable to render JSON context");
            }
        }
        StringBuilder out = new StringBuilder();
        if (format == LlmContextFormat.MARKDOWN) {
            out.append("# Architecture context\n");
            if (query != null && !query.isBlank()) out.append("\nQuery: ").append(query).append('\n');
            out.append("\n## Elements\n");
            graph.elements().forEach(e -> out.append("- ").append(e.name()).append(" [").append(e.id()).append("] ").append(e.type()).append(": ").append(e.description()).append('\n'));
            out.append("\n## Relationships\n");
            graph.relationships().forEach(r -> out.append("- ").append(r.sourceElementId()).append(" -> ").append(r.targetElementId()).append(" ").append(r.type()).append(": ").append(r.description()).append('\n'));
        } else {
            graph.elements().forEach(e -> out.append(e.type()).append(' ').append(e.name()).append(" | ").append(e.description()).append(" | ").append(e.id()).append('\n'));
            graph.relationships().forEach(r -> out.append("REL ").append(r.sourceElementId()).append(" -> ").append(r.targetElementId()).append(" ").append(r.type()).append(" | ").append(r.id()).append('\n'));
        }
        if (includeLinks && !graph.externalLinks().isEmpty()) out.append("\nLinks: ").append(graph.externalLinks()).append('\n');
        if (includeValidation && !graph.validationIssues().isEmpty()) out.append("\nValidation: ").append(graph.validationIssues()).append('\n');
        if (includeMetadata) out.append("\nTrace: ").append(graph.trace()).append('\n');
        return out.toString();
    }

    private WorkspaceEntity requireRead(UUID workspaceId) {
        if (!access.canReadWorkspace(authContext.principalIdOrDevelopment(), workspaceId)) {
            throw new ApiException(Response.Status.NOT_FOUND, "WORKSPACE_NOT_FOUND", "Workspace was not found or is not readable");
        }
        return WorkspaceEntity.findById(workspaceId);
    }

    private ArchitectureElementEntity element(UUID workspaceId, UUID elementId) {
        ArchitectureElementEntity entity = ArchitectureElementEntity.findById(elementId);
        if (entity == null || !entity.workspaceId.equals(workspaceId)) throw new ApiException(Response.Status.NOT_FOUND, "ELEMENT_NOT_FOUND", "Architecture element was not found");
        return entity;
    }

    private ArchitectureRelationshipEntity relationship(UUID workspaceId, UUID relationshipId) {
        ArchitectureRelationshipEntity entity = ArchitectureRelationshipEntity.findById(relationshipId);
        if (entity == null || !entity.workspaceId.equals(workspaceId)) throw new ApiException(Response.Status.NOT_FOUND, "RELATIONSHIP_NOT_FOUND", "Relationship was not found");
        return entity;
    }

    private List<ArchitectureElementEntity> elements(UUID workspaceId) {
        return ArchitectureElementEntity.<ArchitectureElementEntity>list("workspaceId", workspaceId).stream().sorted(Comparator.comparing(e -> e.name)).toList();
    }

    private List<ArchitectureRelationshipEntity> relationships(UUID workspaceId) {
        return ArchitectureRelationshipEntity.<ArchitectureRelationshipEntity>list("workspaceId", workspaceId).stream().sorted(Comparator.comparing(r -> r.id.toString())).toList();
    }

    private List<LinkResponse> links(UUID elementId) {
        return ExternalLinkEntity.<ExternalLinkEntity>list("elementId", elementId).stream().map(Mapper::link).toList();
    }

    private List<ValidationIssue> validationIssues(UUID workspaceId) {
        var response = validationService.validate(workspaceId);
        List<ValidationIssue> issues = new ArrayList<>();
        issues.addAll(response.errors());
        issues.addAll(response.warnings());
        issues.addAll(response.info());
        return issues;
    }

    private AgentTrace trace(WorkspaceEntity workspace, List<UUID> elements, List<UUID> relationships, Map<String, Object> filters, int depth) {
        return new AgentTrace(workspace.id, workspace.version, Instant.now(), UUID.randomUUID(),
                elements.stream().filter(Objects::nonNull).distinct().toList(),
                relationships.stream().filter(Objects::nonNull).distinct().toList(), filters, depth);
    }

    private void checkDepth(int depth) {
        if (depth < 0 || depth > MAX_DEPTH) throw new ApiException(Response.Status.BAD_REQUEST, "INVALID_DEPTH", "Traversal depth must be between 0 and 5");
    }

    private int depth(Integer value) {
        int depth = value == null ? 1 : value;
        checkDepth(depth);
        return depth;
    }

    private int limit(Integer value, int max, String name) {
        int limit = value == null ? max : value;
        if (limit <= 0 || limit > max) throw new ApiException(Response.Status.BAD_REQUEST, "INVALID_LIMIT", name + " must be between 1 and " + max);
        return limit;
    }

    private void checkRelationshipLimit(int value) {
        if (value <= 0 || value > MAX_RELATIONSHIPS) throw new ApiException(Response.Status.BAD_REQUEST, "INVALID_LIMIT", "maxRelationships must be between 1 and 1000");
    }

    private int pageSize(Integer value) {
        return limit(value == null ? 50 : value, MAX_PAGE_SIZE, "pageSize");
    }

    private static long count(List<ArchitectureElementEntity> elements, ArchitectureElementType type) {
        return elements.stream().filter(e -> e.type == type).count();
    }

    private static List<String> distinctMetadata(List<ArchitectureElementEntity> elements, String section, String key) {
        return elements.stream().map(e -> textAt(e.metadata, section, key)).filter(Objects::nonNull).distinct().sorted().toList();
    }

    private static String textAt(Map<String, Object> metadata, String section, String key) {
        if (metadata == null || !(metadata.get(section) instanceof Map<?, ?> map)) return null;
        Object value = map.get(key);
        return value == null || value.toString().isBlank() ? null : value.toString();
    }

    @SuppressWarnings("unchecked")
    private static List<String> responsibilities(Map<String, Object> metadata) {
        Object value = metadata == null ? null : metadata.get("responsibilities");
        if (value instanceof List<?> list) return list.stream().map(Object::toString).toList();
        return List.of();
    }

    private static boolean containsAny(String value, List<String> filters) {
        return filters == null || filters.isEmpty() || (value != null && filters.stream().anyMatch(f -> value.toLowerCase(Locale.ROOT).contains(f.toLowerCase(Locale.ROOT))));
    }

    private static boolean contains(String value, String query) {
        return value != null && query != null && value.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }

    private static boolean tagsContain(Map<String, Object> metadata, List<String> tags) {
        if (tags == null || tags.isEmpty()) return true;
        // Match against classification.tags specifically. Matching the stringified classification
        // map meant a tag filter also matched domain, criticality, or any other field in it.
        if (metadata == null || !(metadata.get("classification") instanceof Map<?, ?> classification)) return false;
        if (!(classification.get("tags") instanceof List<?> actual)) return false;
        Set<String> present = actual.stream().filter(Objects::nonNull).map(v -> v.toString().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        return tags.stream().allMatch(tag -> present.contains(tag.toLowerCase(Locale.ROOT)));
    }

    private static boolean matchesBoolean(String actual, Boolean expected) {
        return expected == null || Objects.equals(Boolean.parseBoolean(actual), expected);
    }

    private static boolean matchesPresence(String value, Boolean expected) {
        return expected == null || (expected == (value != null && !value.isBlank()));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("/+$", "").toLowerCase(Locale.ROOT);
    }

    private static <T> List<T> append(List<T> source, T value) {
        List<T> result = new ArrayList<>(source);
        result.add(value);
        return result;
    }

    private static List<UUID> safeIds(List<UUID> ids) {
        return ids == null ? List.of() : ids;
    }

    private record PathState(UUID elementId, List<UUID> elementPath, List<UUID> relationshipPath, int depth) {
    }

    private record Traversal(Set<UUID> elementIds, Set<UUID> relationshipIds, List<GraphPath> paths, boolean truncated) {
    }
}
