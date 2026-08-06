package com.example.c4editor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.c4editor.api.AgentDtos.AgentElement;
import com.example.c4editor.api.AgentDtos.AgentLimits;
import com.example.c4editor.api.AgentDtos.AgentTrace;
import com.example.c4editor.api.AgentDtos.GraphEnvelope;
import com.example.c4editor.api.AgentProposalDtos.AgentProposalChangeRequest;
import com.example.c4editor.api.AgentProposalDtos.AgentProposalRequest;
import com.example.c4editor.api.AgentProposalDtos.ElementDraft;
import com.example.c4editor.api.AgentProposalDtos.ProposalValidationResult;
import com.example.c4editor.api.AgentProposalDtos.RelationshipDraft;
import com.example.c4editor.api.ApiException;
import com.example.c4editor.api.Dtos.CanonicalModel;
import com.example.c4editor.api.Dtos.ElementResponse;
import com.example.c4editor.api.Dtos.WorkspaceRequest;
import com.example.c4editor.application.AgentQueryService;
import com.example.c4editor.application.AgentProposalService;
import com.example.c4editor.domain.AgentProposalChangeAction;
import com.example.c4editor.domain.ArchitectureElementType;
import com.example.c4editor.domain.LlmContextFormat;
import com.example.c4editor.domain.RelationshipType;
import com.example.c4editor.export.ImportExportService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ImportExportAndAgentTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void canonicalImportRejectsInvalidHierarchyTypes() throws Exception {
        UUID systemId = UUID.randomUUID();
        UUID componentId = UUID.randomUUID();
        CanonicalModel model = new CanonicalModel("1.0", new WorkspaceRequest("Imported", "", null),
                List.of(
                        element(systemId, ArchitectureElementType.SOFTWARE_SYSTEM, "System", null),
                        element(componentId, ArchitectureElementType.COMPONENT, "Component", systemId)),
                List.of(), List.of(), List.of(), List.of());

        ApiException error = assertThrows(ApiException.class, () -> validate(model));

        assertTrue(error.details.keySet().stream().anyMatch(key -> key.startsWith("hierarchy:")));
    }

    @Test
    void canonicalImportAcceptsValidContainerComponentHierarchy() {
        UUID systemId = UUID.randomUUID();
        UUID containerId = UUID.randomUUID();
        UUID componentId = UUID.randomUUID();
        CanonicalModel model = new CanonicalModel("1.0", new WorkspaceRequest("Imported", "", null),
                List.of(
                        element(systemId, ArchitectureElementType.SOFTWARE_SYSTEM, "System", null),
                        element(containerId, ArchitectureElementType.CONTAINER, "Container", systemId),
                        element(componentId, ArchitectureElementType.COMPONENT, "Component", containerId)),
                List.of(), List.of(), List.of(), List.of());

        assertDoesNotThrow(() -> validate(model));
    }

    @Test
    void agentJsonContextIsDeterministicJson() throws Exception {
        AgentQueryService service = new AgentQueryService();
        Field mapper = AgentQueryService.class.getDeclaredField("objectMapper");
        mapper.setAccessible(true);
        mapper.set(service, objectMapper);
        UUID workspaceId = UUID.randomUUID();
        UUID elementId = UUID.randomUUID();
        GraphEnvelope graph = new GraphEnvelope(
                List.of(new AgentElement(elementId, ArchitectureElementType.CONTAINER, "Governance Service", "Evaluates policy", "Quarkus", null, Map.of(), List.of())),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new AgentTrace(workspaceId, 12, Instant.parse("2026-08-06T08:00:00Z"), UUID.randomUUID(), List.of(elementId), List.of(), Map.of(), 1),
                new AgentLimits(500, 1_000),
                false);

        Method render = AgentQueryService.class.getDeclaredMethod("renderContext", LlmContextFormat.class, String.class, GraphEnvelope.class, boolean.class, boolean.class, boolean.class);
        render.setAccessible(true);
        String json = (String) render.invoke(service, LlmContextFormat.JSON, null, graph, true, true, true);

        JsonNode root = objectMapper.readTree(json);
        assertTrue(root.has("elements"));
        assertTrue(root.get("elements").get(0).get("id").asText().equals(elementId.toString()));
    }

    @Test
    void agentProposalValidationSupportsClientReferenceRelationships() throws Exception {
        AgentProposalService service = new AgentProposalService();
        Field mapper = AgentProposalService.class.getDeclaredField("objectMapper");
        mapper.setAccessible(true);
        mapper.set(service, objectMapper);
        AgentProposalRequest request = new AgentProposalRequest(Map.of("agent", "unit-test"), "Detected proposal",
                List.of(
                        new AgentProposalChangeRequest(AgentProposalChangeAction.CREATE_ELEMENT, "user", null,
                                new ElementDraft(ArchitectureElementType.PERSON, "Anonymous User", "", null, null, null, Map.of()),
                                null, null, null, null, List.of(Map.of("path", "apps/web/src/App.tsx"))),
                        new AgentProposalChangeRequest(AgentProposalChangeAction.CREATE_ELEMENT, "system", null,
                                new ElementDraft(ArchitectureElementType.SOFTWARE_SYSTEM, "Anonymous System", "", null, null, null, Map.of()),
                                null, null, null, null, List.of(Map.of("path", "services/system"))),
                        new AgentProposalChangeRequest(AgentProposalChangeAction.CREATE_RELATIONSHIP, "user-system", null,
                                null,
                                new RelationshipDraft(null, "user", null, "system", RelationshipType.USES, "Uses", "HTTPS", null, Map.of()),
                                null, null, null, List.of(Map.of("path", "apps/web/src/api.ts")))));

        Method validate = AgentProposalService.class.getDeclaredMethod("validateRequest", UUID.class, AgentProposalRequest.class);
        validate.setAccessible(true);
        ProposalValidationResult result = (ProposalValidationResult) validate.invoke(service, UUID.randomUUID(), request);

        assertTrue(result.valid());
        assertTrue(result.summary().get("CREATE_ELEMENT") == 2);
        assertTrue(result.summary().get("CREATE_RELATIONSHIP") == 1);
    }

    private void validate(CanonicalModel model) throws Exception {
        Method validate = ImportExportService.class.getDeclaredMethod("validate", CanonicalModel.class);
        validate.setAccessible(true);
        try {
            validate.invoke(new ImportExportService(), model);
        } catch (ReflectiveOperationException ex) {
            if (ex.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw ex;
        }
    }

    private ElementResponse element(UUID id, ArchitectureElementType type, String name, UUID parentId) {
        return new ElementResponse(id, UUID.randomUUID(), type, name, "", parentId, null, Map.of(), 0, Instant.now(), Instant.now(), 0);
    }
}
