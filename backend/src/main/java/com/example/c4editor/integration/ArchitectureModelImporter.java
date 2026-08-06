package com.example.c4editor.integration;

import com.example.c4editor.api.Dtos.ImportOptions;
import com.example.c4editor.api.Dtos.ImportPreview;
import com.example.c4editor.api.Dtos.ImportSource;
import com.example.c4editor.api.Dtos.ImportedWorkspace;
import com.example.c4editor.domain.ImportFormat;

public interface ArchitectureModelImporter {
    ImportFormat supportedFormat();

    ImportPreview validate(ImportSource source);

    ImportedWorkspace importModel(ImportSource source, ImportOptions options);
}
