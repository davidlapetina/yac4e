package com.example.c4editor.api;

import com.example.c4editor.api.Dtos.ElementRequest;
import com.example.c4editor.api.Dtos.LayoutRequest;
import com.example.c4editor.api.Dtos.LinkRequest;
import com.example.c4editor.api.Dtos.MetadataDefinitionRequest;
import com.example.c4editor.api.Dtos.RelationshipRequest;
import com.example.c4editor.api.Dtos.ViewElementRequest;
import com.example.c4editor.api.Dtos.ViewRelationshipRequest;
import com.example.c4editor.api.Dtos.ViewRequest;
import com.example.c4editor.api.Dtos.WorkspaceRequest;
import com.example.c4editor.application.C4ModelService;
import com.example.c4editor.application.SearchService;
import com.example.c4editor.domain.ArchitectureElementType;
import com.example.c4editor.export.ImportExportService;
import com.example.c4editor.validation.ValidationService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;

@Path("")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WorkspaceResource {
    @Inject
    C4ModelService model;

    @Inject
    ValidationService validation;

    @Inject
    SearchService search;

    @Inject
    ImportExportService importExport;

    @GET
    @Path("/workspaces")
    public Response listWorkspaces() {
        return Response.ok(model.listWorkspaces()).build();
    }

    @POST
    @Path("/workspaces")
    public Response createWorkspace(@Valid WorkspaceRequest request) {
        return Response.status(Response.Status.CREATED).entity(model.createWorkspace(request)).build();
    }

    @GET
    @Path("/workspaces/{workspaceId}")
    public Response getWorkspace(@PathParam("workspaceId") UUID workspaceId) {
        return Response.ok(model.getWorkspace(workspaceId)).build();
    }

    @PUT
    @Path("/workspaces/{workspaceId}")
    public Response updateWorkspace(@PathParam("workspaceId") UUID workspaceId, @Valid WorkspaceRequest request) {
        return Response.ok(model.updateWorkspace(workspaceId, request)).build();
    }

    @DELETE
    @Path("/workspaces/{workspaceId}")
    public Response deleteWorkspace(@PathParam("workspaceId") UUID workspaceId) {
        model.deleteWorkspace(workspaceId);
        return Response.noContent().build();
    }

    @GET
    @Path("/workspaces/{workspaceId}/elements")
    public Response listElements(@PathParam("workspaceId") UUID workspaceId, @QueryParam("type") ArchitectureElementType type,
            @QueryParam("parentId") UUID parentId, @QueryParam("search") String searchText, @QueryParam("tag") String tag,
            @QueryParam("owner") String owner, @QueryParam("lifecycleStatus") String lifecycleStatus) {
        return Response.ok(model.listElements(workspaceId, type, parentId, searchText, tag, owner, lifecycleStatus)).build();
    }

    @POST
    @Path("/workspaces/{workspaceId}/elements")
    public Response createElement(@PathParam("workspaceId") UUID workspaceId, @Valid ElementRequest request) {
        return Response.status(Response.Status.CREATED).entity(model.createElement(workspaceId, request)).build();
    }

    @GET
    @Path("/workspaces/{workspaceId}/elements/{elementId}")
    public Response getElement(@PathParam("workspaceId") UUID workspaceId, @PathParam("elementId") UUID elementId) {
        return Response.ok(model.getElement(workspaceId, elementId)).build();
    }

    @PUT
    @Path("/workspaces/{workspaceId}/elements/{elementId}")
    public Response updateElement(@PathParam("workspaceId") UUID workspaceId, @PathParam("elementId") UUID elementId,
            @Valid ElementRequest request) {
        return Response.ok(model.updateElement(workspaceId, elementId, request)).build();
    }

    @DELETE
    @Path("/workspaces/{workspaceId}/elements/{elementId}")
    public Response deleteElement(@PathParam("workspaceId") UUID workspaceId, @PathParam("elementId") UUID elementId) {
        model.deleteElement(workspaceId, elementId);
        return Response.noContent().build();
    }

    @GET
    @Path("/workspaces/{workspaceId}/relationships")
    public Response listRelationships(@PathParam("workspaceId") UUID workspaceId) {
        return Response.ok(model.listRelationships(workspaceId)).build();
    }

    @POST
    @Path("/workspaces/{workspaceId}/relationships")
    public Response createRelationship(@PathParam("workspaceId") UUID workspaceId, @Valid RelationshipRequest request) {
        return Response.status(Response.Status.CREATED).entity(model.createRelationship(workspaceId, request)).build();
    }

    @GET
    @Path("/workspaces/{workspaceId}/relationships/{relationshipId}")
    public Response getRelationship(@PathParam("workspaceId") UUID workspaceId, @PathParam("relationshipId") UUID relationshipId) {
        return Response.ok(model.getRelationship(workspaceId, relationshipId)).build();
    }

    @PUT
    @Path("/workspaces/{workspaceId}/relationships/{relationshipId}")
    public Response updateRelationship(@PathParam("workspaceId") UUID workspaceId, @PathParam("relationshipId") UUID relationshipId,
            @Valid RelationshipRequest request) {
        return Response.ok(model.updateRelationship(workspaceId, relationshipId, request)).build();
    }

    @DELETE
    @Path("/workspaces/{workspaceId}/relationships/{relationshipId}")
    public Response deleteRelationship(@PathParam("workspaceId") UUID workspaceId, @PathParam("relationshipId") UUID relationshipId) {
        model.deleteRelationship(workspaceId, relationshipId);
        return Response.noContent().build();
    }

    @GET
    @Path("/workspaces/{workspaceId}/views")
    public Response listViews(@PathParam("workspaceId") UUID workspaceId) {
        return Response.ok(model.listViews(workspaceId)).build();
    }

    @POST
    @Path("/workspaces/{workspaceId}/views")
    public Response createView(@PathParam("workspaceId") UUID workspaceId, @Valid ViewRequest request) {
        return Response.status(Response.Status.CREATED).entity(model.createView(workspaceId, request)).build();
    }

    @GET
    @Path("/workspaces/{workspaceId}/views/{viewId}")
    public Response getView(@PathParam("workspaceId") UUID workspaceId, @PathParam("viewId") UUID viewId) {
        return Response.ok(model.getView(workspaceId, viewId)).build();
    }

    @PUT
    @Path("/workspaces/{workspaceId}/views/{viewId}")
    public Response updateView(@PathParam("workspaceId") UUID workspaceId, @PathParam("viewId") UUID viewId, @Valid ViewRequest request) {
        return Response.ok(model.updateView(workspaceId, viewId, request)).build();
    }

    @DELETE
    @Path("/workspaces/{workspaceId}/views/{viewId}")
    public Response deleteView(@PathParam("workspaceId") UUID workspaceId, @PathParam("viewId") UUID viewId) {
        model.deleteView(workspaceId, viewId);
        return Response.noContent().build();
    }

    @PUT
    @Path("/workspaces/{workspaceId}/views/{viewId}/layout")
    public Response updateLayout(@PathParam("workspaceId") UUID workspaceId, @PathParam("viewId") UUID viewId, @Valid LayoutRequest request) {
        return Response.ok(model.updateLayout(workspaceId, viewId, request)).build();
    }

    @POST
    @Path("/workspaces/{workspaceId}/views/{viewId}/elements")
    public Response addElementToView(@PathParam("workspaceId") UUID workspaceId, @PathParam("viewId") UUID viewId,
            @Valid ViewElementRequest request) {
        return Response.status(Response.Status.CREATED).entity(model.addElementToView(workspaceId, viewId, request)).build();
    }

    @DELETE
    @Path("/workspaces/{workspaceId}/views/{viewId}/elements/{elementId}")
    public Response removeElementFromView(@PathParam("workspaceId") UUID workspaceId, @PathParam("viewId") UUID viewId,
            @PathParam("elementId") UUID elementId) {
        model.removeElementFromView(workspaceId, viewId, elementId);
        return Response.noContent().build();
    }

    @POST
    @Path("/workspaces/{workspaceId}/views/{viewId}/relationships")
    public Response addRelationshipToView(@PathParam("workspaceId") UUID workspaceId, @PathParam("viewId") UUID viewId,
            @Valid ViewRelationshipRequest request) {
        return Response.status(Response.Status.CREATED).entity(model.addRelationshipToView(workspaceId, viewId, request)).build();
    }

    @DELETE
    @Path("/workspaces/{workspaceId}/views/{viewId}/relationships/{relationshipId}")
    public Response removeRelationshipFromView(@PathParam("workspaceId") UUID workspaceId, @PathParam("viewId") UUID viewId,
            @PathParam("relationshipId") UUID relationshipId) {
        model.removeRelationshipFromView(workspaceId, viewId, relationshipId);
        return Response.noContent().build();
    }

    @GET
    @Path("/workspaces/{workspaceId}/elements/{elementId}/links")
    public Response listLinks(@PathParam("workspaceId") UUID workspaceId, @PathParam("elementId") UUID elementId) {
        return Response.ok(model.listLinks(workspaceId, elementId)).build();
    }

    @POST
    @Path("/workspaces/{workspaceId}/elements/{elementId}/links")
    public Response createLink(@PathParam("workspaceId") UUID workspaceId, @PathParam("elementId") UUID elementId,
            @Valid LinkRequest request) {
        return Response.status(Response.Status.CREATED).entity(model.createLink(workspaceId, elementId, request)).build();
    }

    @PUT
    @Path("/workspaces/{workspaceId}/elements/{elementId}/links/{linkId}")
    public Response updateLink(@PathParam("workspaceId") UUID workspaceId, @PathParam("elementId") UUID elementId,
            @PathParam("linkId") UUID linkId, @Valid LinkRequest request) {
        return Response.ok(model.updateLink(workspaceId, elementId, linkId, request)).build();
    }

    @DELETE
    @Path("/workspaces/{workspaceId}/elements/{elementId}/links/{linkId}")
    public Response deleteLink(@PathParam("workspaceId") UUID workspaceId, @PathParam("elementId") UUID elementId,
            @PathParam("linkId") UUID linkId) {
        model.deleteLink(workspaceId, elementId, linkId);
        return Response.noContent().build();
    }

    @GET
    @Path("/workspaces/{workspaceId}/metadata-definitions")
    public Response listMetadataDefinitions(@PathParam("workspaceId") UUID workspaceId) {
        return Response.ok(model.listMetadataDefinitions(workspaceId)).build();
    }

    @POST
    @Path("/workspaces/{workspaceId}/metadata-definitions")
    public Response createMetadataDefinition(@PathParam("workspaceId") UUID workspaceId, @Valid MetadataDefinitionRequest request) {
        return Response.status(Response.Status.CREATED).entity(model.createMetadataDefinition(workspaceId, request)).build();
    }

    @PUT
    @Path("/workspaces/{workspaceId}/metadata-definitions/{definitionId}")
    public Response updateMetadataDefinition(@PathParam("workspaceId") UUID workspaceId, @PathParam("definitionId") UUID definitionId,
            @Valid MetadataDefinitionRequest request) {
        return Response.ok(model.updateMetadataDefinition(workspaceId, definitionId, request)).build();
    }

    @DELETE
    @Path("/workspaces/{workspaceId}/metadata-definitions/{definitionId}")
    public Response deleteMetadataDefinition(@PathParam("workspaceId") UUID workspaceId, @PathParam("definitionId") UUID definitionId) {
        model.deleteMetadataDefinition(workspaceId, definitionId);
        return Response.noContent().build();
    }

    @GET
    @Path("/workspaces/{workspaceId}/validation")
    public Response validate(@PathParam("workspaceId") UUID workspaceId) {
        return Response.ok(validation.validate(workspaceId)).build();
    }

    @GET
    @Path("/workspaces/{workspaceId}/search")
    public Response search(@PathParam("workspaceId") UUID workspaceId, @QueryParam("q") String query) {
        return Response.ok(search.search(workspaceId, query)).build();
    }

    @GET
    @Path("/workspaces/{workspaceId}/exports/model.json")
    @Produces(MediaType.APPLICATION_JSON)
    public Response exportJson(@PathParam("workspaceId") UUID workspaceId) {
        return Response.ok(importExport.exportJson(workspaceId))
                .header("Content-Disposition", "attachment; filename=\"yac4e-model.json\"")
                .build();
    }

    @GET
    @Path("/workspaces/{workspaceId}/exports/model.yaml")
    @Produces("application/yaml")
    public Response exportYaml(@PathParam("workspaceId") UUID workspaceId) {
        return Response.ok(importExport.exportYaml(workspaceId))
                .header("Content-Disposition", "attachment; filename=\"yac4e-model.yaml\"")
                .build();
    }
}
