package com.example.c4editor.application;

import com.example.c4editor.persistence.WorkspaceEntity;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;

@ApplicationScoped
public class DevelopmentWorkspaceAccessService implements WorkspaceAccessService {
    @Override
    public boolean canReadWorkspace(String principalId, UUID workspaceId) {
        return isAuthenticated(principalId) && WorkspaceEntity.findById(workspaceId) != null;
    }

    @Override
    public boolean canWriteWorkspace(String principalId, UUID workspaceId) {
        return isAuthenticated(principalId) && WorkspaceEntity.findById(workspaceId) != null;
    }

    @Override
    public boolean canProposeWorkspace(String principalId, UUID workspaceId) {
        return isAuthenticated(principalId) && WorkspaceEntity.findById(workspaceId) != null;
    }

    private boolean isAuthenticated(String principalId) {
        return principalId != null && !principalId.isBlank();
    }
}
