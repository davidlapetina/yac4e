package com.example.c4editor.api;

import com.example.c4editor.export.ImportExportService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/imports")
@Produces(MediaType.APPLICATION_JSON)
public class ImportResource {
    @Inject
    ImportExportService importExport;

    @POST
    @Path("/model")
    @Consumes({MediaType.APPLICATION_JSON, "application/yaml", "text/yaml", "text/plain"})
    public Response importModel(String payload) {
        return Response.status(Response.Status.CREATED).entity(importExport.importModel(payload)).build();
    }
}
