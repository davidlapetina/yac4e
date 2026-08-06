# Architecture

YaC4e separates the canonical architecture model from diagram views.

Architecture elements and relationships represent real model facts. Diagram views project selected elements and relationships with view-specific coordinates, dimensions, visibility, and display settings. Canvas coordinates are stored only in `diagram_view_element`.

Backend package roles:

- `api`: REST resources, DTOs, error mapping
- `application`: transactional services, graph/agent query logic, seed data
- `domain`: enums and domain vocabulary
- `persistence`: Panache entities
- `integration`: external import adapters, including Structurizr DSL
- `export`: canonical JSON/YAML import/export
- `validation`: architecture validation rules

The agent API uses `AgentQueryService`, not REST-resource-local graph logic, so the same service can later back MCP tools.

Authentication is split between Basic Auth for browser/API access and scoped API tokens for agent automation. `WorkspaceAccessService` remains the central authorization seam for future workspace ownership, RBAC, or SSO integration.

External agents do not upload or scan source code inside YaC4e. They submit evidence-backed proposals through `AgentProposalService`; accepted proposals are applied transactionally to the canonical model.
