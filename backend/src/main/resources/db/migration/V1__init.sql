CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE workspace (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by TEXT NOT NULL DEFAULT 'local-user',
    updated_by TEXT NOT NULL DEFAULT 'local-user'
);

CREATE TABLE architecture_element (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspace(id) ON DELETE CASCADE,
    type TEXT NOT NULL,
    name TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    parent_element_id UUID NULL REFERENCES architecture_element(id) ON DELETE SET NULL,
    technology TEXT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by TEXT NOT NULL DEFAULT 'local-user',
    updated_by TEXT NOT NULL DEFAULT 'local-user'
);

CREATE TABLE architecture_relationship (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspace(id) ON DELETE CASCADE,
    source_element_id UUID NOT NULL REFERENCES architecture_element(id) ON DELETE CASCADE,
    target_element_id UUID NOT NULL REFERENCES architecture_element(id) ON DELETE CASCADE,
    type TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    technology TEXT NULL,
    protocol TEXT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by TEXT NOT NULL DEFAULT 'local-user',
    updated_by TEXT NOT NULL DEFAULT 'local-user'
);

CREATE TABLE diagram_view (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspace(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    type TEXT NOT NULL,
    scope_element_id UUID NULL REFERENCES architecture_element(id) ON DELETE SET NULL,
    layout_direction TEXT NOT NULL,
    settings JSONB NOT NULL DEFAULT '{}'::jsonb,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by TEXT NOT NULL DEFAULT 'local-user',
    updated_by TEXT NOT NULL DEFAULT 'local-user'
);

CREATE TABLE diagram_view_element (
    id UUID PRIMARY KEY,
    view_id UUID NOT NULL REFERENCES diagram_view(id) ON DELETE CASCADE,
    element_id UUID NOT NULL REFERENCES architecture_element(id) ON DELETE CASCADE,
    x DOUBLE PRECISION NOT NULL,
    y DOUBLE PRECISION NOT NULL,
    width DOUBLE PRECISION NOT NULL,
    height DOUBLE PRECISION NOT NULL,
    locked BOOLEAN NOT NULL DEFAULT FALSE,
    visible BOOLEAN NOT NULL DEFAULT TRUE,
    z_index INTEGER NOT NULL DEFAULT 0,
    display_settings JSONB NOT NULL DEFAULT '{}'::jsonb,
    UNIQUE(view_id, element_id)
);

CREATE TABLE diagram_view_relationship (
    id UUID PRIMARY KEY,
    view_id UUID NOT NULL REFERENCES diagram_view(id) ON DELETE CASCADE,
    relationship_id UUID NOT NULL REFERENCES architecture_relationship(id) ON DELETE CASCADE,
    visible BOOLEAN NOT NULL DEFAULT TRUE,
    display_settings JSONB NOT NULL DEFAULT '{}'::jsonb,
    UNIQUE(view_id, relationship_id)
);

CREATE TABLE external_link (
    id UUID PRIMARY KEY,
    element_id UUID NOT NULL REFERENCES architecture_element(id) ON DELETE CASCADE,
    provider TEXT NOT NULL,
    type TEXT NOT NULL,
    label TEXT NOT NULL,
    url TEXT NOT NULL,
    external_id TEXT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE metadata_definition (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspace(id) ON DELETE CASCADE,
    key TEXT NOT NULL,
    label TEXT NOT NULL,
    description TEXT NULL,
    value_type TEXT NOT NULL,
    required BOOLEAN NOT NULL DEFAULT FALSE,
    applies_to JSONB NOT NULL DEFAULT '[]'::jsonb,
    allowed_values JSONB NULL,
    default_value JSONB NULL,
    validation_rules JSONB NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    UNIQUE(workspace_id, key)
);

CREATE TABLE import_source_mapping (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspace(id) ON DELETE CASCADE,
    source_format TEXT NOT NULL,
    source_workspace_key TEXT NULL,
    source_entity_type TEXT NOT NULL,
    source_entity_id TEXT NOT NULL,
    canonical_entity_id UUID NOT NULL,
    last_imported_at TIMESTAMPTZ NOT NULL,
    source_checksum TEXT NULL,
    UNIQUE(workspace_id, source_format, source_entity_type, source_entity_id)
);

CREATE TABLE api_token (
    id UUID PRIMARY KEY,
    principal_id TEXT NOT NULL,
    token_name TEXT NOT NULL,
    token_hash TEXT NOT NULL UNIQUE,
    scopes TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NULL,
    last_used_at TIMESTAMPTZ NULL
);

CREATE TABLE agent_proposal (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspace(id) ON DELETE CASCADE,
    status TEXT NOT NULL,
    source JSONB NOT NULL DEFAULT '{}'::jsonb,
    summary TEXT NOT NULL DEFAULT '',
    validation JSONB NOT NULL DEFAULT '{}'::jsonb,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    applied_at TIMESTAMPTZ NULL,
    rejected_at TIMESTAMPTZ NULL,
    created_by TEXT NOT NULL DEFAULT 'local-user',
    updated_by TEXT NOT NULL DEFAULT 'local-user'
);

CREATE TABLE agent_proposal_change (
    id UUID PRIMARY KEY,
    proposal_id UUID NOT NULL REFERENCES agent_proposal(id) ON DELETE CASCADE,
    sequence_number INTEGER NOT NULL,
    action TEXT NOT NULL,
    status TEXT NOT NULL,
    client_reference TEXT NULL,
    target_entity_type TEXT NULL,
    target_entity_id UUID NULL,
    result_entity_id UUID NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    evidence JSONB NOT NULL DEFAULT '[]'::jsonb,
    validation JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE(proposal_id, sequence_number)
);

CREATE INDEX idx_workspace_name ON workspace (lower(name));
CREATE INDEX idx_element_workspace_type ON architecture_element (workspace_id, type);
CREATE INDEX idx_element_parent ON architecture_element (parent_element_id);
CREATE INDEX idx_element_name_search ON architecture_element (lower(name));
CREATE INDEX idx_element_metadata_gin ON architecture_element USING GIN (metadata);
CREATE INDEX idx_relationship_source ON architecture_relationship (source_element_id);
CREATE INDEX idx_relationship_target ON architecture_relationship (target_element_id);
CREATE INDEX idx_view_workspace ON diagram_view (workspace_id);
CREATE INDEX idx_view_element_view ON diagram_view_element (view_id);
CREATE INDEX idx_view_relationship_view ON diagram_view_relationship (view_id);
CREATE INDEX idx_external_link_element ON external_link (element_id);
CREATE INDEX idx_import_source_mapping_workspace ON import_source_mapping (workspace_id);
CREATE INDEX idx_import_source_mapping_source ON import_source_mapping (source_format, source_entity_type, source_entity_id);
CREATE INDEX idx_api_token_principal ON api_token (principal_id);
CREATE INDEX idx_api_token_enabled ON api_token (enabled);
CREATE INDEX idx_agent_proposal_workspace_status ON agent_proposal (workspace_id, status);
CREATE INDEX idx_agent_proposal_change_proposal ON agent_proposal_change (proposal_id, sequence_number);
CREATE INDEX idx_agent_proposal_change_target ON agent_proposal_change (target_entity_type, target_entity_id);
