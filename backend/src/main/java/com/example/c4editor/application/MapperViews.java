package com.example.c4editor.application;

import com.example.c4editor.api.Dtos.ViewResponse;
import com.example.c4editor.persistence.DiagramViewElementEntity;
import com.example.c4editor.persistence.DiagramViewEntity;
import java.util.List;
import java.util.UUID;

final class MapperViews {
    private MapperViews() {
    }

    static List<ViewResponse> viewsForElement(UUID workspaceId, UUID elementId) {
        return DiagramViewElementEntity.<DiagramViewElementEntity>list("elementId", elementId).stream()
                .map(member -> DiagramViewEntity.<DiagramViewEntity>findById(member.viewId))
                .filter(view -> view != null && view.workspaceId.equals(workspaceId))
                .map(Mapper::view)
                .toList();
    }
}
