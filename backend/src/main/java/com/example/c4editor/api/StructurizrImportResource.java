package com.example.c4editor.api;

import com.example.c4editor.api.Dtos.ImportOptions;
import com.example.c4editor.api.Dtos.ImportSource;
import com.example.c4editor.application.StructurizrImportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

@Path("/imports/structurizr")
@Produces(MediaType.APPLICATION_JSON)
public class StructurizrImportResource {
    @Inject
    StructurizrImportService service;

    @Inject
    ObjectMapper objectMapper;

    @POST
    @Path("/validate")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response validate(@RestForm("file") FileUpload file) throws Exception {
        return Response.ok(service.validate(source(file))).build();
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response importModel(@RestForm("file") FileUpload file, @RestForm("options") String optionsJson) throws Exception {
        ImportOptions options = optionsJson == null || optionsJson.isBlank()
                ? null
                : objectMapper.readValue(optionsJson, ImportOptions.class);
        return Response.status(Response.Status.CREATED).entity(service.importWorkspace(source(file), options)).build();
    }

    private ImportSource source(FileUpload file) throws Exception {
        if (file == null || file.uploadedFile() == null) {
            throw new ApiException(Response.Status.BAD_REQUEST, "FILE_REQUIRED", "A Structurizr DSL file or ZIP archive is required");
        }
        byte[] bytes = Files.readAllBytes(file.uploadedFile());
        return new ImportSource(file.fileName(), bytes, checksum(bytes));
    }

    private String checksum(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
