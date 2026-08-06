package com.example.c4editor.application;

import com.example.c4editor.api.Dtos.SearchResponse;
import com.example.c4editor.api.Dtos.SearchResult;
import com.example.c4editor.persistence.ArchitectureElementEntity;
import com.example.c4editor.persistence.ArchitectureRelationshipEntity;
import com.example.c4editor.persistence.DiagramViewEntity;
import com.example.c4editor.persistence.ExternalLinkEntity;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class SearchService {
    public SearchResponse search(UUID workspaceId, String query) {
        String q = query == null ? "" : query.toLowerCase();
        List<SearchResult> results = new ArrayList<>();
        for (ArchitectureElementEntity element : ArchitectureElementEntity.<ArchitectureElementEntity>list("workspaceId", workspaceId)) {
            List<String> fields = matched(q, element.name, element.description, element.technology, element.metadata.toString());
            if (!fields.isEmpty()) {
                results.add(new SearchResult("ELEMENT", element.id, element.id, null, null, element.name, fields, element.description));
            }
        }
        for (ArchitectureRelationshipEntity relationship : ArchitectureRelationshipEntity.<ArchitectureRelationshipEntity>list("workspaceId", workspaceId)) {
            List<String> fields = matched(q, relationship.description, relationship.technology, relationship.protocol, relationship.metadata.toString());
            if (!fields.isEmpty()) {
                results.add(new SearchResult("RELATIONSHIP", relationship.id, null, relationship.id, null, relationship.type.name(), fields, relationship.description));
            }
        }
        for (DiagramViewEntity view : DiagramViewEntity.<DiagramViewEntity>list("workspaceId", workspaceId)) {
            List<String> fields = matched(q, view.name, view.description, view.type.name());
            if (!fields.isEmpty()) {
                results.add(new SearchResult("VIEW", view.id, null, null, view.id, view.name, fields, view.description));
            }
        }
        for (ExternalLinkEntity link : ExternalLinkEntity.<ExternalLinkEntity>listAll()) {
            ArchitectureElementEntity element = ArchitectureElementEntity.findById(link.elementId);
            if (element != null && element.workspaceId.equals(workspaceId)) {
                List<String> fields = matched(q, link.label, link.url, link.externalId, link.metadata.toString());
                if (!fields.isEmpty()) {
                    results.add(new SearchResult("EXTERNAL_LINK", link.id, link.elementId, null, null, link.label, fields, link.url));
                }
            }
        }
        return new SearchResponse(results);
    }

    private List<String> matched(String query, String... values) {
        if (query.isBlank()) {
            return List.of();
        }
        List<String> fields = new ArrayList<>();
        String[] names = {"name", "description", "technology", "metadata"};
        for (int i = 0; i < values.length; i++) {
            if (values[i] != null && values[i].toLowerCase().contains(query)) {
                fields.add(i < names.length ? names[i] : "field" + i);
            }
        }
        return fields;
    }
}
