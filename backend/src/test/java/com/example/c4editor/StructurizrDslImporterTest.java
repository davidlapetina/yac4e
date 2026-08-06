package com.example.c4editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.c4editor.api.Dtos.ImportSource;
import com.example.c4editor.integration.StructurizrDslImporter;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class StructurizrDslImporterTest {
    private final StructurizrDslImporter importer = new StructurizrDslImporter();

    @Test
    void minimalWorkspacePreviewReportsCounts() {
        String dsl = """
                workspace "Tiny" {
                    model {
                        user = person "User"
                        system = softwareSystem "System" {
                            api = container "API" "Handles requests" "Java"
                        }
                        user -> system "Uses"
                    }
                    views {
                        systemContext system "context" {
                            include *
                            autoLayout lr
                        }
                    }
                }
                """;

        var preview = importer.validate(new ImportSource("workspace.dsl", dsl.getBytes(StandardCharsets.UTF_8), "test"));

        assertTrue(preview.valid());
        assertEquals(1, preview.summary().people());
        assertEquals(1, preview.summary().softwareSystems());
        assertEquals(1, preview.summary().containers());
        assertEquals(1, preview.summary().views());
    }

    @Test
    void syntaxErrorsAreReportedWithoutPersistence() {
        var preview = importer.validate(new ImportSource("workspace.dsl", "workspace { model { broken".getBytes(StandardCharsets.UTF_8), "test"));

        assertFalse(preview.valid());
        assertEquals("STRUCTURIZR_SYNTAX_ERROR", preview.errors().getFirst().code());
    }

    @Test
    void zipPathTraversalIsRejected() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("../workspace.dsl"));
            zip.write("workspace \"Bad\" {}".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        var preview = importer.validate(new ImportSource("bad.zip", bytes.toByteArray(), "test"));

        assertFalse(preview.valid());
        assertEquals("ZIP_PATH_TRAVERSAL", preview.errors().getFirst().code());
    }
}
