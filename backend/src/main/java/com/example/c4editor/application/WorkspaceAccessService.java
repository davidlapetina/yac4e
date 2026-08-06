package com.example.c4editor.application;

import java.util.UUID;

public interface WorkspaceAccessService {
    boolean canReadWorkspace(String principalId, UUID workspaceId);

    boolean canWriteWorkspace(String principalId, UUID workspaceId);

    boolean canProposeWorkspace(String principalId, UUID workspaceId);
}
