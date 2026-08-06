package com.example.c4editor.api;

import com.example.c4editor.api.AgentDtos.ArchitectureQueryRequest;
import com.example.c4editor.api.AgentDtos.BatchContextRequest;
import com.example.c4editor.api.AgentDtos.ImpactAnalysisRequest;
import com.example.c4editor.api.AgentDtos.LlmContextRequest;
import com.example.c4editor.api.AgentDtos.ResolveReferenceRequest;
import com.example.c4editor.api.AgentDtos.ValidationQueryRequest;
import com.example.c4editor.application.AgentQueryService;
import com.example.c4editor.domain.DependencyDirection;
import com.example.c4editor.domain.RelationshipType;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/agent/workspaces/{workspaceId}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "AI Agent API", description = "Read-only architecture graph and context API for automated analysis")
public class AgentResource {
    @Inject
    AgentQueryService service;

    @GET
    @Path("/summary")
    @Operation(operationId = "getWorkspaceSummary", summary = "Get compact workspace summary")
    public Response getWorkspaceSummary(@PathParam("workspaceId") UUID workspaceId) {
        return Response.ok(service.summary(workspaceId)).build();
    }

    @GET
    @Path("/elements/{elementId}/context")
    @Operation(operationId = "getElementContext", summary = "Get coherent context for one architecture element")
    public Response getElementContext(@PathParam("workspaceId") UUID workspaceId, @PathParam("elementId") UUID elementId,
            @QueryParam("includeParents") @DefaultValue("true") boolean includeParents,
            @QueryParam("includeChildren") @DefaultValue("true") boolean includeChildren,
            @QueryParam("includeIncoming") @DefaultValue("true") boolean includeIncoming,
            @QueryParam("includeOutgoing") @DefaultValue("true") boolean includeOutgoing,
            @QueryParam("includeLinks") @DefaultValue("true") boolean includeLinks,
            @QueryParam("includeMetadata") @DefaultValue("true") boolean includeMetadata,
            @QueryParam("includeViews") @DefaultValue("true") boolean includeViews,
            @QueryParam("includeValidation") @DefaultValue("true") boolean includeValidation,
            @QueryParam("depth") @DefaultValue("1") int depth,
            @QueryParam("maxRelationships") @DefaultValue("100") int maxRelationships) {
        return Response.ok(service.elementContext(workspaceId, elementId, includeParents, includeChildren, includeIncoming,
                includeOutgoing, includeLinks, includeMetadata, includeViews, includeValidation, depth, maxRelationships)).build();
    }

    @POST
    @Path("/context")
    @Operation(operationId = "getBatchElementContext", summary = "Get deduplicated graph context for several architecture elements")
    public Response getBatchContext(@PathParam("workspaceId") UUID workspaceId, BatchContextRequest request) {
        return Response.ok(service.batchContext(workspaceId, request)).build();
    }

    @POST
    @Path("/query")
    @Operation(operationId = "queryArchitecture", summary = "Run deterministic structured architecture query")
    public Response queryArchitecture(@PathParam("workspaceId") UUID workspaceId, ArchitectureQueryRequest request) {
        return Response.ok(service.query(workspaceId, request)).build();
    }

    @GET
    @Path("/elements/{elementId}/dependencies")
    @Operation(operationId = "getElementDependencies", summary = "Get bounded direct or transitive dependencies")
    public Response getElementDependencies(@PathParam("workspaceId") UUID workspaceId, @PathParam("elementId") UUID elementId,
            @QueryParam("direction") @DefaultValue("OUTGOING") DependencyDirection direction,
            @QueryParam("depth") @DefaultValue("1") int depth,
            @QueryParam("relationshipTypes") String relationshipTypes,
            @QueryParam("includeExternalSystems") @DefaultValue("true") boolean includeExternalSystems,
            @QueryParam("maximumElements") @DefaultValue("200") int maximumElements) {
        return Response.ok(service.dependencies(workspaceId, elementId, direction, depth, relationshipTypes(relationshipTypes),
                includeExternalSystems, maximumElements)).build();
    }

    @POST
    @Path("/impact-analysis")
    @Operation(operationId = "analyzeArchitectureImpact", summary = "Run deterministic graph impact analysis")
    public Response analyzeArchitectureImpact(@PathParam("workspaceId") UUID workspaceId, ImpactAnalysisRequest request) {
        return Response.ok(service.impact(workspaceId, request)).build();
    }

    @POST
    @Path("/validation/query")
    @Operation(operationId = "queryArchitectureValidation", summary = "Query validation issues by governance filters")
    public Response queryArchitectureValidation(@PathParam("workspaceId") UUID workspaceId, ValidationQueryRequest request) {
        return Response.ok(service.validationQuery(workspaceId, request)).build();
    }

    @GET
    @Path("/external-resources")
    @Operation(operationId = "getExternalResources", summary = "Find external resources linked to architecture elements")
    public Response getExternalResources(@PathParam("workspaceId") UUID workspaceId, @QueryParam("provider") String provider,
            @QueryParam("type") String type, @QueryParam("externalId") String externalId, @QueryParam("url") String url,
            @QueryParam("elementId") UUID elementId, @QueryParam("search") String search,
            @QueryParam("page") @DefaultValue("0") int page, @QueryParam("pageSize") @DefaultValue("50") int pageSize) {
        return Response.ok(service.externalResources(workspaceId, provider, type, externalId, url, elementId, search, page, pageSize)).build();
    }

    @POST
    @Path("/resolve-reference")
    @Operation(operationId = "resolveExternalReference", summary = "Resolve an external reference to linked architecture records")
    public Response resolveExternalReference(@PathParam("workspaceId") UUID workspaceId, ResolveReferenceRequest request) {
        return Response.ok(service.resolveReference(workspaceId, request)).build();
    }

    @POST
    @Path("/llm-context")
    @Operation(operationId = "generateLlmContext", summary = "Generate deterministic LLM-ready architecture context")
    public Response generateLlmContext(@PathParam("workspaceId") UUID workspaceId, LlmContextRequest request) {
        return Response.ok(service.llmContext(workspaceId, request)).build();
    }

    private Set<RelationshipType> relationshipTypes(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(RelationshipType::valueOf)
                .collect(Collectors.toSet());
    }
}
