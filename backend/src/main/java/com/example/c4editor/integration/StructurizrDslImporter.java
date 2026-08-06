package com.example.c4editor.integration;

import com.example.c4editor.api.Dtos.CanonicalModel;
import com.example.c4editor.api.Dtos.ElementResponse;
import com.example.c4editor.api.Dtos.ImportMessage;
import com.example.c4editor.api.Dtos.ImportOptions;
import com.example.c4editor.api.Dtos.ImportPreview;
import com.example.c4editor.api.Dtos.ImportSource;
import com.example.c4editor.api.Dtos.ImportSummary;
import com.example.c4editor.api.Dtos.ImportedWorkspace;
import com.example.c4editor.api.Dtos.LinkResponse;
import com.example.c4editor.api.Dtos.MetadataDefinitionResponse;
import com.example.c4editor.api.Dtos.RelationshipResponse;
import com.example.c4editor.api.Dtos.ViewElementResponse;
import com.example.c4editor.api.Dtos.ViewRelationshipResponse;
import com.example.c4editor.api.Dtos.ViewResponse;
import com.example.c4editor.api.Dtos.WorkspaceRequest;
import com.example.c4editor.domain.ArchitectureElementType;
import com.example.c4editor.domain.DiagramViewType;
import com.example.c4editor.domain.ImportFormat;
import com.example.c4editor.domain.LayoutDirection;
import com.example.c4editor.domain.LinkProvider;
import com.example.c4editor.domain.LinkType;
import com.example.c4editor.domain.RelationshipType;
import com.structurizr.Workspace;
import com.structurizr.dsl.StructurizrDslParser;
import com.structurizr.dsl.StructurizrDslParserException;
import com.structurizr.model.Component;
import com.structurizr.model.Container;
import com.structurizr.model.Element;
import com.structurizr.model.Person;
import com.structurizr.model.Relationship;
import com.structurizr.model.SoftwareSystem;
import com.structurizr.view.AutomaticLayout;
import com.structurizr.view.ComponentView;
import com.structurizr.view.ContainerView;
import com.structurizr.view.DynamicView;
import com.structurizr.view.ElementView;
import com.structurizr.view.ModelView;
import com.structurizr.view.RelationshipView;
import com.structurizr.view.SystemContextView;
import com.structurizr.view.View;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@ApplicationScoped
public final class StructurizrDslImporter implements ArchitectureModelImporter {
    private static final long MAX_UPLOAD_BYTES = 10L * 1024L * 1024L;
    private static final long MAX_EXTRACTED_BYTES = 25L * 1024L * 1024L;
    private static final int MAX_FILES = 100;
    private static final int MAX_MODEL_ITEMS = 5_000;

    @Override
    public ImportFormat supportedFormat() {
        return ImportFormat.STRUCTURIZR_DSL;
    }

    @Override
    public ImportPreview validate(ImportSource source) {
        try {
            ParsedStructurizr parsed = parse(source);
            return toImportedWorkspace(parsed, null).preview();
        } catch (StructurizrImportException ex) {
            return new ImportPreview(false, new WorkspaceRequest("Invalid Structurizr import", "", null),
                    new ImportSummary(0, 0, 0, 0, 0, 0), List.of(), List.of(ex.message));
        }
    }

    @Override
    public ImportedWorkspace importModel(ImportSource source, ImportOptions options) {
        ParsedStructurizr parsed = parse(source);
        return toImportedWorkspace(parsed, options);
    }

    private ParsedStructurizr parse(ImportSource source) {
        if (source.content() == null || source.content().length == 0) {
            throw invalid("EMPTY_UPLOAD", source.fileName(), null, "Upload is empty");
        }
        if (source.content().length > MAX_UPLOAD_BYTES) {
            throw invalid("UPLOAD_TOO_LARGE", source.fileName(), null, "Structurizr import exceeds the 10 MB upload limit");
        }
        try {
            Path root = Files.createTempDirectory("yac4e-structurizr-");
            Path workspaceFile = prepareSource(source, root);
            StructurizrDslParser parser = new StructurizrDslParser();
            parser.setRestricted(true);
            parser.parse(workspaceFile.toFile());
            Workspace workspace = parser.getWorkspace();
            if (workspace.getModel().getElements().size() + workspace.getModel().getRelationships().size() > MAX_MODEL_ITEMS) {
                throw invalid("MODEL_TOO_LARGE", source.fileName(), null, "Structurizr model exceeds the MVP import item limit");
            }
            return new ParsedStructurizr(workspace, parser, source.fileName(), source.checksum());
        } catch (StructurizrDslParserException ex) {
            throw new StructurizrImportException(new ImportMessage("STRUCTURIZR_SYNTAX_ERROR", source.fileName(), ex.getLineNumber(), null,
                    ex.getMessage() == null ? "Structurizr DSL syntax error" : ex.getMessage()));
        } catch (IOException ex) {
            throw invalid("IMPORT_IO_ERROR", source.fileName(), null, "Unable to read uploaded Structurizr content");
        }
    }

    private Path prepareSource(ImportSource source, Path root) throws IOException {
        String name = source.fileName() == null ? "workspace.dsl" : source.fileName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".dsl")) {
            Path file = root.resolve("workspace.dsl");
            Files.write(file, source.content());
            return file;
        }
        if (!name.endsWith(".zip")) {
            throw invalid("UNSUPPORTED_FILE_TYPE", source.fileName(), null, "Upload must be a .dsl file or .zip package");
        }
        int files = 0;
        long extracted = 0;
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(source.content()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                files++;
                if (files > MAX_FILES) {
                    throw invalid("ZIP_TOO_MANY_FILES", source.fileName(), null, "ZIP package contains too many files");
                }
                Path target = root.resolve(entry.getName()).normalize();
                if (!target.startsWith(root) || entry.getName().startsWith("/") || entry.getName().contains("..")) {
                    throw invalid("ZIP_PATH_TRAVERSAL", source.fileName(), null, "ZIP entries must stay inside the import package");
                }
                if (!entry.getName().endsWith(".dsl")) {
                    throw invalid("UNSUPPORTED_ZIP_ENTRY", entry.getName(), null, "Only .dsl files are accepted inside Structurizr ZIP imports");
                }
                Files.createDirectories(target.getParent());
                byte[] bytes = zip.readAllBytes();
                extracted += bytes.length;
                if (extracted > MAX_EXTRACTED_BYTES) {
                    throw invalid("ZIP_TOO_LARGE", source.fileName(), null, "Extracted ZIP package exceeds the 25 MB limit");
                }
                Files.write(target, bytes);
            }
        }
        Path workspace = root.resolve("workspace.dsl");
        if (!Files.exists(workspace)) {
            throw invalid("MISSING_WORKSPACE_DSL", source.fileName(), null, "ZIP imports must contain a root workspace.dsl file");
        }
        return workspace;
    }

    private ImportedWorkspace toImportedWorkspace(ParsedStructurizr parsed, ImportOptions options) {
        Workspace workspace = parsed.workspace;
        Map<String, UUID> elementIds = new HashMap<>();
        List<ElementResponse> elements = new ArrayList<>();
        List<LinkResponse> links = new ArrayList<>();
        Map<String, String> sourceMappings = new LinkedHashMap<>();
        for (Element source : workspace.getModel().getElements()) {
            ArchitectureElementType type = typeOf(source);
            if (type == null) {
                continue;
            }
            UUID id = UUID.randomUUID();
            elementIds.put(source.getId(), id);
            UUID parentId = source.getParent() == null ? null : elementIds.get(source.getParent().getId());
            String sourceKey = parsed.parser.getIdentifiersRegister().findIdentifier(source);
            Map<String, Object> metadata = metadataFor(source, sourceKey);
            sourceMappings.put(id.toString(), source.getId());
            elements.add(new ElementResponse(id, null, type, source.getName(), text(source.getDescription()), parentId,
                    technology(source), metadata, 0, null, null, source.getUrl() == null ? 0 : 1));
            if (source.getUrl() != null && !source.getUrl().isBlank()) {
                links.add(new LinkResponse(UUID.randomUUID(), id, LinkProvider.OTHER, LinkType.DOCUMENTATION, "Structurizr URL",
                        source.getUrl(), source.getId(), Map.of("source", "structurizr"), Instant.now(), Instant.now()));
            }
        }
        List<RelationshipResponse> relationships = new ArrayList<>();
        Map<String, UUID> relationshipIds = new HashMap<>();
        for (Relationship source : workspace.getModel().getRelationships()) {
            UUID sourceId = elementIds.get(source.getSourceId());
            UUID targetId = elementIds.get(source.getDestinationId());
            if (sourceId == null || targetId == null || sourceId.equals(targetId)) {
                continue;
            }
            UUID id = UUID.randomUUID();
            relationshipIds.put(source.getId(), id);
            String sourceKey = parsed.parser.getIdentifiersRegister().findIdentifier(source);
            sourceMappings.put(id.toString(), source.getId());
            relationships.add(new RelationshipResponse(id, null, sourceId, targetId, RelationshipType.USES,
                    text(source.getDescription()), textOrNull(source.getTechnology()), textOrNull(source.getTechnology()),
                    relationshipMetadata(source, sourceKey), 0, null, null));
        }
        List<ViewResponse> views = new ArrayList<>();
        List<ImportMessage> warnings = new ArrayList<>();
        for (View source : workspace.getViews().getViews()) {
            if (!(source instanceof ModelView modelView)) {
                warnings.add(warning("UNSUPPORTED_VIEW_TYPE", parsed.fileName, source.getKey(), "View " + source.getName() + " is not model-backed and was not imported"));
                continue;
            }
            DiagramViewType type = viewType(source, warnings, parsed.fileName);
            UUID viewId = UUID.randomUUID();
            UUID scopeId = scopeId(source, elementIds);
            List<ViewElementResponse> members = new ArrayList<>();
            for (ElementView elementView : modelView.getElements()) {
                UUID elementId = elementIds.get(elementView.getId());
                if (elementId == null) {
                    continue;
                }
                double x = elementView.getX() == 0 ? 80 + members.size() * 320.0 : elementView.getX();
                double y = elementView.getY() == 0 ? 80 + (members.size() % 3) * 190.0 : elementView.getY();
                members.add(new ViewElementResponse(UUID.randomUUID(), viewId, elementId, x, y, 260, 150, false, true,
                        members.size() + 1, Map.of("structurizrElementViewId", elementView.getId())));
            }
            List<ViewRelationshipResponse> viewRelationships = new ArrayList<>();
            for (RelationshipView relationshipView : modelView.getRelationships()) {
                UUID relationshipId = relationshipIds.get(relationshipView.getId());
                if (relationshipId == null && relationshipView.getRelationship() != null) {
                    relationshipId = relationshipIds.get(relationshipView.getRelationship().getId());
                }
                if (relationshipId != null) {
                    viewRelationships.add(new ViewRelationshipResponse(UUID.randomUUID(), viewId, relationshipId, true,
                            Map.of("order", relationshipView.getOrder() == null ? "" : relationshipView.getOrder())));
                }
            }
            views.add(new ViewResponse(viewId, null, viewName(source), text(source.getDescription()), type, scopeId,
                    layoutDirection(modelView.getAutomaticLayout()), viewSettings(source), 0, null, null, members, viewRelationships));
        }
        addUnsupportedWarnings(workspace, warnings, parsed.fileName);
        String workspaceName = options != null && options.workspaceName() != null && !options.workspaceName().isBlank()
                ? options.workspaceName()
                : workspace.getName();
        CanonicalModel model = new CanonicalModel("1.0", new WorkspaceRequest(workspaceName, text(workspace.getDescription()), null),
                elements, relationships, views, links, List.<MetadataDefinitionResponse>of());
        ImportPreview preview = new ImportPreview(true, model.workspace(), summary(elements, relationships, views), warnings, List.of());
        return new ImportedWorkspace(model, preview, sourceMappings);
    }

    private ArchitectureElementType typeOf(Element source) {
        if (source instanceof Person) return ArchitectureElementType.PERSON;
        if (source instanceof SoftwareSystem) return ArchitectureElementType.SOFTWARE_SYSTEM;
        if (source instanceof Container) return ArchitectureElementType.CONTAINER;
        if (source instanceof Component) return ArchitectureElementType.COMPONENT;
        return null;
    }

    private UUID scopeId(View source, Map<String, UUID> elementIds) {
        if (source instanceof ComponentView componentView && componentView.getContainer() != null) {
            return elementIds.get(componentView.getContainer().getId());
        }
        if (source instanceof ModelView modelView && modelView.getSoftwareSystem() != null) {
            return elementIds.get(modelView.getSoftwareSystem().getId());
        }
        return null;
    }

    private String technology(Element source) {
        if (source instanceof Container container) return textOrNull(container.getTechnology());
        if (source instanceof Component component) return textOrNull(component.getTechnology());
        return null;
    }

    private DiagramViewType viewType(View source, List<ImportMessage> warnings, String fileName) {
        if (source instanceof SystemContextView) return DiagramViewType.SYSTEM_CONTEXT;
        if (source instanceof ContainerView) return DiagramViewType.CONTAINER;
        if (source instanceof ComponentView) return DiagramViewType.COMPONENT;
        if (source instanceof DynamicView) {
            warnings.add(warning("DYNAMIC_VIEW_AS_CUSTOM", fileName, source.getKey(), "Dynamic view " + source.getName() + " was imported as CUSTOM"));
        } else {
            warnings.add(warning("UNSUPPORTED_VIEW_TYPE", fileName, source.getKey(), "View " + source.getName() + " was imported as CUSTOM"));
        }
        return DiagramViewType.CUSTOM;
    }

    private LayoutDirection layoutDirection(AutomaticLayout layout) {
        if (layout == null || layout.getRankDirection() == null) return LayoutDirection.LEFT_TO_RIGHT;
        return switch (layout.getRankDirection().name()) {
            case "TopBottom" -> LayoutDirection.TOP_TO_BOTTOM;
            case "BottomTop" -> LayoutDirection.BOTTOM_TO_TOP;
            case "RightLeft" -> LayoutDirection.RIGHT_TO_LEFT;
            default -> LayoutDirection.LEFT_TO_RIGHT;
        };
    }

    private Map<String, Object> metadataFor(Element source, String sourceKey) {
        Map<String, Object> root = standardMetadata();
        root.put("importSource", Map.of("format", "STRUCTURIZR_DSL", "sourceId", source.getId(), "sourceKey", sourceKey == null ? "" : sourceKey));
        root.put("classification", Map.of("tags", new ArrayList<>(source.getTagsAsSet())));
        root.put("custom", Map.of("structurizr", Map.of(
                "canonicalName", source.getCanonicalName(),
                "properties", source.getProperties(),
                "url", source.getUrl() == null ? "" : source.getUrl())));
        return root;
    }

    private Map<String, Object> relationshipMetadata(Relationship source, String sourceKey) {
        return Map.of(
                "importSource", Map.of("format", "STRUCTURIZR_DSL", "sourceId", source.getId(), "sourceKey", sourceKey == null ? "" : sourceKey),
                "custom", Map.of("structurizr", Map.of(
                        "canonicalName", source.getCanonicalName(),
                        "tags", new ArrayList<>(source.getTagsAsSet()),
                        "properties", source.getProperties(),
                        "url", source.getUrl() == null ? "" : source.getUrl())));
    }

    private Map<String, Object> standardMetadata() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("ownership", new LinkedHashMap<>());
        root.put("lifecycle", Map.of("status", "IMPORTED"));
        root.put("operations", new LinkedHashMap<>());
        root.put("security", new LinkedHashMap<>());
        root.put("delivery", new LinkedHashMap<>());
        return root;
    }

    private Map<String, Object> viewSettings(View source) {
        return Map.of("structurizr", Map.of("key", source.getKey(), "title", source.getTitle() == null ? "" : source.getTitle(), "properties", source.getProperties()));
    }

    private ImportSummary summary(List<ElementResponse> elements, List<RelationshipResponse> relationships, List<ViewResponse> views) {
        return new ImportSummary(
                (int) elements.stream().filter(e -> e.type() == ArchitectureElementType.PERSON).count(),
                (int) elements.stream().filter(e -> e.type() == ArchitectureElementType.SOFTWARE_SYSTEM).count(),
                (int) elements.stream().filter(e -> e.type() == ArchitectureElementType.CONTAINER).count(),
                (int) elements.stream().filter(e -> e.type() == ArchitectureElementType.COMPONENT).count(),
                relationships.size(),
                views.size());
    }

    private void addUnsupportedWarnings(Workspace workspace, List<ImportMessage> warnings, String fileName) {
        if (!workspace.getModel().getDeploymentNodes().isEmpty()) {
            warnings.add(warning("UNSUPPORTED_DEPLOYMENT_NODES", fileName, null, "Deployment nodes are not imported by the MVP"));
        }
        if (!workspace.getViews().getDeploymentViews().isEmpty()) {
            warnings.add(warning("DEPLOYMENT_VIEW_AS_CUSTOM", fileName, null, "Deployment views are imported only when their elements map to canonical C4 elements"));
        }
        if (!workspace.getViews().getFilteredViews().isEmpty()) {
            warnings.add(warning("UNSUPPORTED_FILTERED_VIEWS", fileName, null, "Filtered views are not imported by the MVP"));
        }
    }

    private ImportMessage warning(String code, String fileName, String key, String message) {
        String suffix = key == null || key.isBlank() ? "" : " (" + key + ")";
        return new ImportMessage(code, fileName, null, null, message + suffix);
    }

    private String viewName(View source) {
        if (source.getTitle() != null && !source.getTitle().isBlank()) return source.getTitle();
        if (source.getName() != null && !source.getName().isBlank()) return source.getName();
        return source.getKey();
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    private String textOrNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private StructurizrImportException invalid(String code, String fileName, Integer line, String message) {
        return new StructurizrImportException(new ImportMessage(code, fileName, line, null, message));
    }

    private record ParsedStructurizr(Workspace workspace, StructurizrDslParser parser, String fileName, String checksum) {
    }

    private static final class StructurizrImportException extends RuntimeException {
        final ImportMessage message;

        private StructurizrImportException(ImportMessage message) {
            super(message.message());
            this.message = message;
        }
    }
}
