package com.example.c4editor.api;

import com.example.c4editor.api.AgentProposalDtos.AgentProposalRequest;
import com.example.c4editor.application.AgentProposalService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/agent/workspaces/{workspaceId}/proposals")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "AI Agent Proposals", description = "Evidence-backed architecture change proposals submitted by external agents")
public class AgentProposalResource {
    @Inject
    AgentProposalService service;

    @GET
    @Operation(operationId = "listAgentProposals", summary = "List agent architecture proposals")
    public Response list(@PathParam("workspaceId") UUID workspaceId) {
        return Response.ok(service.list(workspaceId)).build();
    }

    @POST
    @Operation(operationId = "createAgentProposal", summary = "Create and persist an evidence-backed architecture proposal")
    public Response create(@PathParam("workspaceId") UUID workspaceId, @Valid AgentProposalRequest request) {
        return Response.status(Response.Status.CREATED).entity(service.create(workspaceId, request)).build();
    }

    @POST
    @Path("/validate")
    @Operation(operationId = "validateAgentProposal", summary = "Validate an architecture proposal without persisting it")
    public Response validate(@PathParam("workspaceId") UUID workspaceId, @Valid AgentProposalRequest request) {
        return Response.ok(service.validate(workspaceId, request)).build();
    }

    @GET
    @Path("/{proposalId}")
    @Operation(operationId = "getAgentProposal", summary = "Get an agent architecture proposal with its changes and evidence")
    public Response get(@PathParam("workspaceId") UUID workspaceId, @PathParam("proposalId") UUID proposalId) {
        return Response.ok(service.get(workspaceId, proposalId)).build();
    }

    @POST
    @Path("/{proposalId}/apply")
    @Operation(operationId = "applyAgentProposal", summary = "Apply a valid proposal transactionally to the canonical architecture model")
    public Response apply(@PathParam("workspaceId") UUID workspaceId, @PathParam("proposalId") UUID proposalId) {
        return Response.ok(service.apply(workspaceId, proposalId)).build();
    }

    @POST
    @Path("/{proposalId}/reject")
    @Operation(operationId = "rejectAgentProposal", summary = "Reject a pending or invalid agent architecture proposal")
    public Response reject(@PathParam("workspaceId") UUID workspaceId, @PathParam("proposalId") UUID proposalId) {
        return Response.ok(service.reject(workspaceId, proposalId)).build();
    }
}
