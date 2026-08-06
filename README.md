# YaC4e

YaC4e, "yet another C4 editor", is a web application for maintaining a metadata-rich C4 architecture repository. It is built as one deployable Quarkus application that serves both the REST API and the compiled React editor.

The product goal is to treat architecture as a living model, not as a drawing. Architecture elements, relationships, metadata, links, and diagram views are stored as canonical data in PostgreSQL. Each diagram view projects part of that shared model and keeps its own view-specific positions and dimensions.

![YaC4e editor overview](docs/screenshots/editor-overview.png)

## Features

- Create and rename workspaces for architecture repositories.
- Model C4 people, software systems, containers, components, data stores, and external systems.
- Define typed relationships such as `USES`, `CALLS`, `READS_FROM`, `WRITES_TO`, `DEPENDS_ON`, and `AUTHENTICATES_WITH`.
- Maintain hierarchy through canonical `parentElementId` links instead of canvas nesting.
- Create multiple diagram views over the same architecture model.
- Position the same element differently in different views.
- Drag, resize, connect, delete from view, zoom, pan, and fit diagrams with React Flow.
- Run automatic layout with ELK.js.
- Edit rich standard metadata for ownership, classification, lifecycle, operations, security, delivery, and custom fields.
- Define custom metadata fields per workspace.
- Attach external links for Jira, Confluence, GitHub, GitLab, dashboards, runbooks, API specs, and other resources.
- Search across names, descriptions, technology, metadata, tags, and external links.
- Validate architecture completeness and governance issues.
- Export canonical models as JSON and YAML.
- Import canonical JSON/YAML models transactionally.
- Import Structurizr DSL workspaces from `.dsl` files or supported ZIP uploads.
- Export full diagrams as SVG or PNG from the frontend.
- Expose an AI-agent read API for workspace summary, context, dependency traversal, impact analysis, validation queries, external reference resolution, and deterministic LLM-ready context.
- Accept external agent architecture proposals with evidence, then review and apply or reject them from the UI.
- Let agents propose diagram views complete with their elements, with automatic layout and relationship inclusion.
- Review proposals in bulk: select several and apply or reject them in one pass.
- Protect the UI/API with Basic Auth and support scoped API tokens for agent access.

## Screenshots

### Editor

The main UI is a three-panel editor with a model explorer, React Flow canvas, properties panel, top toolbar, and compact validation footer.

![Editor overview](docs/screenshots/editor-overview.png)

### Import Workflow

The import modal supports canonical JSON, canonical YAML, and Structurizr DSL. Structurizr imports are validated first and can create or replace a workspace.

![Import workflow](docs/screenshots/import-workflow.png)

### Agent Proposals

External agents can submit evidence-backed proposals. The UI provides a human review step before model changes are applied.

![Agent proposal review](docs/screenshots/proposal-review.png)

## Technology Stack

- Backend: Java 21, Quarkus, Hibernate ORM with Panache, Flyway, PostgreSQL, SmallRye OpenAPI
- Frontend: React 19, TypeScript, React Flow, ELK.js, Zustand, TanStack Query, React Hook Form, Zod
- Import/export: Jackson JSON/YAML, Structurizr DSL Java library, html-to-image
- Tests: JUnit 5, REST Assured, Vitest, React Testing Library setup, Playwright
- Build: Maven reactor build with frontend build integration

## Repository Structure

```text
.
├── pom.xml
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/example/c4editor/
│       ├── main/resources/
│       └── test/java/com/example/c4editor/
├── frontend/
│   ├── package.json
│   ├── vite.config.ts
│   ├── src/
│   └── tests/
├── docker/
│   └── docker-compose.yml
├── docs/
│   ├── architecture.md
│   ├── api.md
│   ├── export-design.md
│   ├── model-format.md
│   ├── canonical-model.schema.json
│   └── screenshots/
├── Dockerfile
├── LICENSE
└── README.md
```

## Architecture

YaC4e separates three core concepts:

- Architecture elements represent real architecture entities.
- Architecture relationships represent semantic dependencies between elements.
- Diagram views are projections of selected elements and relationships.

Canvas coordinates are stored only on `diagram_view_element`, never on the canonical architecture element. This is what allows the same container, component, or system to appear in several views with different positions.

The backend follows a layered structure:

- `api`: JAX-RS resources and DTOs
- `application`: transactional services and orchestration
- `domain`: enums and domain concepts
- `persistence`: Panache entities
- `integration`: Structurizr DSL import adapter
- `export`: canonical model import/export
- `validation`: architecture validation rules

More detail is in [docs/architecture.md](docs/architecture.md).

## Prerequisites

- Java 21
- Maven 3.9+
- Node.js 20+ or 22+
- Docker Desktop or compatible Docker runtime

## Run Locally

Start PostgreSQL:

```bash
docker compose -f docker/docker-compose.yml up -d
```

Run Quarkus dev mode:

```bash
mvn -pl backend quarkus:dev
```

Open:

```text
http://localhost:8080
```

API documentation:

```text
http://localhost:8080/api/docs
```

Default local Basic Auth credentials:

```text
username: admin
password: admin
```

If you run with a custom HTTP port:

```bash
HTTP_PORT=18080 mvn -pl backend quarkus:dev
```

Open `http://localhost:18080`.

## Frontend Development

For fast frontend iteration, run Vite separately while Quarkus serves the API:

```bash
cd frontend
npm install
npm run dev
```

Vite proxies `/api` to the Quarkus backend configured in [frontend/vite.config.ts](frontend/vite.config.ts).

## Production Build

Build and verify the full deployable application:

```bash
mvn clean verify
```

Run the packaged Quarkus app:

```bash
java -jar backend/target/quarkus-app/quarkus-run.jar
```

The Maven build installs frontend dependencies, builds the React app, copies `frontend/dist` into `META-INF/resources`, and packages one Quarkus artifact that serves both:

- REST API under `/api`
- React application under `/`

For a faster package-only rebuild during local iteration:

```bash
mvn -pl backend package -DskipTests
```

Run the packaged app on a custom port and database:

```bash
HTTP_PORT=18080 \
DATABASE_URL=jdbc:postgresql://localhost:15432/yac4e \
DATABASE_USERNAME=yac4e \
DATABASE_PASSWORD=yac4e \
YAC4E_BASIC_USERNAME=admin \
YAC4E_BASIC_PASSWORD=change-me \
YAC4E_SEED_AGENT_TOKEN=change-me-agent-token \
java -jar backend/target/quarkus-app/quarkus-run.jar
```

Flyway migrations run automatically at application startup.

## Docker

Start local PostgreSQL:

```bash
docker compose -f docker/docker-compose.yml up -d
```

Build the production container:

```bash
docker build -t yac4e:local .
```

Run it against the local Docker Compose database:

```bash
docker run --rm -p 8080:8080 \
  -e DATABASE_URL=jdbc:postgresql://host.docker.internal:15432/yac4e \
  -e DATABASE_USERNAME=yac4e \
  -e DATABASE_PASSWORD=yac4e \
  -e YAC4E_BASIC_PASSWORD=change-me \
  -e YAC4E_SEED_AGENT_TOKEN=change-me-agent-token \
  yac4e:local
```

## Deployment

YaC4e deploys as a single Quarkus application artifact. The deployment target only needs Java 21 and network access to PostgreSQL.

Minimal server deployment:

```bash
mvn clean verify
scp -r backend/target/quarkus-app user@server:/opt/yac4e/quarkus-app
ssh user@server
cd /opt/yac4e
DATABASE_URL=jdbc:postgresql://db-host:5432/yac4e \
DATABASE_USERNAME=yac4e \
DATABASE_PASSWORD='replace-me' \
HTTP_PORT=8080 \
YAC4E_BASIC_USERNAME=admin \
YAC4E_BASIC_PASSWORD='replace-me' \
YAC4E_SEED_AGENT_TOKEN_ENABLED=false \
java -jar quarkus-app/quarkus-run.jar
```

Minimal Docker deployment:

```bash
docker build -t yac4e:1.0.0 .
docker run -d --name yac4e \
  -p 8080:8080 \
  -e DATABASE_URL=jdbc:postgresql://db-host:5432/yac4e \
  -e DATABASE_USERNAME=yac4e \
  -e DATABASE_PASSWORD='replace-me' \
  -e HTTP_PORT=8080 \
  -e YAC4E_BASIC_USERNAME=admin \
  -e YAC4E_BASIC_PASSWORD='replace-me' \
  -e YAC4E_SEED_AGENT_TOKEN_ENABLED=false \
  yac4e:1.0.0
```

Deployment checklist:

- Use PostgreSQL 15+ or a compatible managed PostgreSQL service.
- Set `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD`.
- Replace the default Basic Auth password.
- Disable the seeded development token with `YAC4E_SEED_AGENT_TOKEN_ENABLED=false`, or provide a high-entropy token.
- Put TLS termination in front of the app when exposing it outside localhost.
- Back up PostgreSQL; architecture state is persisted in the database, not in local files.
- Verify `/api/docs` is reachable only to intended users if exposed.

## Authentication

Authentication is intentionally simple for the MVP.

Basic Auth is configured through environment variables:

```bash
YAC4E_AUTH_ENABLED=true
YAC4E_BASIC_USERNAME=admin
YAC4E_BASIC_PASSWORD=admin
YAC4E_BASIC_PRINCIPAL_ID=local-user
```

API tokens are seeded from configuration and stored as token hashes in PostgreSQL:

```bash
YAC4E_SEED_AGENT_TOKEN_ENABLED=true
YAC4E_SEED_AGENT_TOKEN=yac4e-dev-agent-token
YAC4E_SEED_AGENT_TOKEN_NAME="Development agent token"
YAC4E_SEED_AGENT_TOKEN_PRINCIPAL_ID=local-agent
```

Use bearer tokens for agent APIs:

```bash
curl -H "Authorization: Bearer yac4e-dev-agent-token" \
  http://localhost:8080/api/agent/workspaces/{workspaceId}/summary
```

For real deployments, override `YAC4E_BASIC_PASSWORD`, disable the default seeded token, or provide a high-entropy token through `YAC4E_SEED_AGENT_TOKEN`.

## Import And Export

Export canonical JSON:

```bash
curl -u admin:admin \
  -o model.json \
  http://localhost:8080/api/workspaces/{workspaceId}/exports/model.json
```

Export canonical YAML:

```bash
curl -u admin:admin \
  -o model.yaml \
  http://localhost:8080/api/workspaces/{workspaceId}/exports/model.yaml
```

Import canonical JSON:

```bash
curl -u admin:admin \
  -X POST \
  -H "Content-Type: application/json" \
  --data @model.json \
  http://localhost:8080/api/imports/model
```

Validate Structurizr DSL:

```bash
curl -u admin:admin \
  -F file=@workspace.dsl \
  http://localhost:8080/api/imports/structurizr/validate
```

Import Structurizr DSL into a new workspace:

```bash
curl -u admin:admin \
  -F file=@workspace.dsl \
  -F 'options={"mode":"CREATE_NEW","workspaceName":"Imported architecture"}' \
  http://localhost:8080/api/imports/structurizr
```

The canonical model format is documented in [docs/model-format.md](docs/model-format.md), with JSON Schema in [docs/canonical-model.schema.json](docs/canonical-model.schema.json).

## Agent API

Agent endpoints are under `/api/agent` and are designed for automated architecture analysis without requiring the client to reconstruct context from many CRUD calls.

Useful examples:

```bash
curl -H "Authorization: Bearer yac4e-dev-agent-token" \
  http://localhost:8080/api/agent/workspaces/{workspaceId}/summary
```

```bash
curl -H "Authorization: Bearer yac4e-dev-agent-token" \
  "http://localhost:8080/api/agent/workspaces/{workspaceId}/elements/{elementId}/dependencies?direction=OUTGOING&depth=2"
```

```bash
curl -H "Authorization: Bearer yac4e-dev-agent-token" \
  -H "Content-Type: application/json" \
  --data '{"reference":"GOV-100"}' \
  http://localhost:8080/api/agent/workspaces/{workspaceId}/resolve-reference
```

Agents can propose architecture changes without modifying the canonical model immediately:

```bash
curl -H "Authorization: Bearer yac4e-dev-agent-token" \
  -H "Content-Type: application/json" \
  --data @proposal.json \
  http://localhost:8080/api/agent/workspaces/{workspaceId}/proposals
```

The proposal can then be reviewed and applied from the YaC4e UI.

## Example Prompt For An External AI Agent

Use this prompt with an external coding agent that has access to a repository and can make HTTP requests to YaC4e. Replace the placeholders before running it.

```text
You are an architecture analysis agent. Analyze the codebase I give you and submit evidence-backed C4 architecture proposals to YaC4e.

YaC4e connection:
- Base URL: http://localhost:8080
- Workspace ID: {workspaceId}
- API token: {agentApiToken}
- Authentication header: Authorization: Bearer {agentApiToken}

Rules:
- Do not modify YaC4e directly through CRUD endpoints.
- Use only the agent API under /api/agent.
- First retrieve the workspace summary:
  GET /api/agent/workspaces/{workspaceId}/summary
- If you know relevant existing elements, retrieve context:
  GET /api/agent/workspaces/{workspaceId}/elements/{elementId}/context?includeParents=true&includeChildren=true&includeIncoming=true&includeOutgoing=true&includeLinks=true&includeMetadata=true&includeViews=true&includeValidation=true&depth=1
- Search before proposing duplicates:
  POST /api/agent/workspaces/{workspaceId}/query
- Submit proposed changes only through:
  POST /api/agent/workspaces/{workspaceId}/proposals
- Include code evidence for every proposed change.
- Evidence should point to files, folders, classes, symbols, line numbers, commit hashes, or repository URLs.
- Do not invent architecture facts. Only propose facts supported by source evidence.
- Prefer C4 element types PERSON, SOFTWARE_SYSTEM, CONTAINER, COMPONENT, DATA_STORE, and EXTERNAL_SYSTEM.
- Prefer relationship types USES, CALLS, READS_FROM, WRITES_TO, PUBLISHES_TO, SUBSCRIBES_TO, DEPENDS_ON, OWNS, and AUTHENTICATES_WITH.
- Preserve detected source identity in metadata.custom.source when useful.
- For source-code references, attach an external link with provider GITHUB, GITLAB, AZURE_DEVOPS, or OTHER when a stable URL is available.
- Keep each proposal focused and reviewable. Use at most 50 changes per proposal.
- Always finish a proposal with at least one CREATE_VIEW change, otherwise the elements you created appear on no diagram and the editor looks empty.
- Do not claim the proposal was applied. YaC4e stores it for human review.

Creating diagram views:
- Use the CREATE_VIEW action and list the members in view.elements. A view without elements is created as an empty diagram and validation returns an EMPTY_VIEW warning.
- Identify each member by elementId (an element that already exists) or elementReference (the clientReference of an element created earlier in the same proposal).
- Do not compute layout. x, y, width, height, locked, visible, and zIndex are all optional. Omitted coordinates are placed on an automatic four-column grid, and sizes default to 260 x 150.
- Relationships are attached automatically: every relationship whose source and target are both on the view is added to it. Set "includeRelationships": false only if you want elements without connections.
- View types are SYSTEM_CONTEXT, CONTAINER, COMPONENT, and CUSTOM. Set scopeReference or scopeElementId to the element the view is scoped to: the software system for a CONTAINER view, the container for a COMPONENT view.
- Propose one CONTAINER view per software system, and a COMPONENT view for any container with components worth showing.
- An element listed twice is placed once and reported as a DUPLICATE_VIEW_ELEMENT warning. An unresolvable elementReference is an UNKNOWN_CLIENT_REFERENCE error, caught by /proposals/validate before anything is applied.

Output process:
1. Inspect the repository structure and identify deployable systems, services, applications, data stores, external integrations, and major components.
2. Query YaC4e to understand the current workspace and avoid duplicates.
3. Build one proposal JSON payload using the schema below.
4. Add the CREATE_VIEW changes that place the proposed elements on diagrams.
5. Validate the proposal:
   POST /api/agent/workspaces/{workspaceId}/proposals/validate
6. If valid, submit it:
   POST /api/agent/workspaces/{workspaceId}/proposals
7. Report the proposal ID, validation warnings, the element, relationship and view counts, and the main source files used as evidence.

Proposal JSON shape:
{
  "source": {
    "agent": "repo-analysis-agent",
    "repository": "{repositoryUrl}",
    "commit": "{commitSha}",
    "branch": "{branchName}"
  },
  "summary": "Detected architecture facts from {repositoryName}",
  "changes": [
    {
      "action": "CREATE_ELEMENT",
      "clientReference": "service_api",
      "element": {
        "type": "CONTAINER",
        "name": "Service API",
        "description": "Handles API requests for the service.",
        "parentElementId": "{existingSoftwareSystemId}",
        "technology": "Java / Quarkus",
        "metadata": {
          "classification": {
            "domain": "Operations",
            "tags": ["code-detected"]
          },
          "lifecycle": {
            "status": "PRODUCTION"
          },
          "custom": {
            "source": {
              "paths": ["services/api"],
              "primaryClasses": ["com.example.ApiResource"]
            }
          }
        }
      },
      "evidence": [
        {
          "kind": "CLASS",
          "path": "services/api/src/main/java/com/example/ApiResource.java",
          "symbol": "ApiResource",
          "lineStart": 18,
          "lineEnd": 94
        },
        {
          "kind": "BUILD_FILE",
          "path": "services/api/pom.xml"
        }
      ]
    },
    {
      "action": "CREATE_LINK",
      "link": {
        "elementReference": "service_api",
        "provider": "GITHUB",
        "type": "REPOSITORY",
        "label": "Service API source",
        "url": "{repositoryUrl}/tree/{branchName}/services/api",
        "externalId": "services/api",
        "metadata": {
          "path": "services/api"
        }
      },
      "evidence": [
        {
          "kind": "FOLDER",
          "path": "services/api"
        }
      ]
    },
    {
      "action": "CREATE_VIEW",
      "clientReference": "container_view",
      "view": {
        "name": "Service API containers",
        "description": "Containers detected in the service repository.",
        "type": "CONTAINER",
        "scopeElementId": "{existingSoftwareSystemId}",
        "layoutDirection": "LEFT_TO_RIGHT",
        "elements": [
          { "elementReference": "service_api" }
        ]
      },
      "evidence": [
        {
          "kind": "FOLDER",
          "path": "services/api"
        }
      ]
    }
  ]
}
```

### Diagram views with membership

`CREATE_VIEW` places elements on the view through `view.elements`. A view proposed without
`elements` is created as an empty diagram and the validation response returns an `EMPTY_VIEW`
warning.

Each entry in `view.elements` identifies one element by either `elementId` (an element that already
exists in the workspace) or `elementReference` (the `clientReference` of an element created earlier
in the same proposal). All geometry is optional:

| Field | Default |
|---|---|
| `x`, `y` | laid out automatically in a four-column grid |
| `width`, `height` | `260` x `150` |
| `locked` | `false` |
| `visible` | `true` |
| `zIndex` | placement order, starting at `1` |

Because positions are optional, an agent can propose a usable diagram without computing layout;
the editor's automatic layout can still be applied afterwards.

Relationships are added automatically: after the members are placed, every workspace relationship
whose source and target are both on the view is attached to it. Set `"includeRelationships": false`
in the view draft to place elements only.

An element listed twice is placed once and reported as a `DUPLICATE_VIEW_ELEMENT` warning. An
element reference that cannot be resolved is an `UNKNOWN_CLIENT_REFERENCE` error, so it is caught by
`/proposals/validate` before anything is applied.

Minimal validation command:

```bash
curl -H "Authorization: Bearer {agentApiToken}" \
  -H "Content-Type: application/json" \
  --data @proposal.json \
  http://localhost:8080/api/agent/workspaces/{workspaceId}/proposals/validate
```

Minimal submit command:

```bash
curl -H "Authorization: Bearer {agentApiToken}" \
  -H "Content-Type: application/json" \
  --data @proposal.json \
  http://localhost:8080/api/agent/workspaces/{workspaceId}/proposals
```

## Implemented REST Areas

- `GET|POST /api/workspaces`
- `GET|PUT|DELETE /api/workspaces/{workspaceId}`
- `/api/workspaces/{workspaceId}/elements`
- `/api/workspaces/{workspaceId}/relationships`
- `/api/workspaces/{workspaceId}/views`
- `/api/workspaces/{workspaceId}/views/{viewId}/layout`
- `/api/workspaces/{workspaceId}/views/{viewId}/elements`
- `/api/workspaces/{workspaceId}/views/{viewId}/relationships`
- `/api/workspaces/{workspaceId}/elements/{elementId}/links`
- `/api/workspaces/{workspaceId}/metadata-definitions`
- `/api/workspaces/{workspaceId}/validation`
- `/api/workspaces/{workspaceId}/search`
- `/api/workspaces/{workspaceId}/exports/model.json`
- `/api/workspaces/{workspaceId}/exports/model.yaml`
- `/api/imports/model`
- `/api/imports/structurizr`
- `/api/imports/structurizr/validate`
- `/api/agent/workspaces/{workspaceId}/summary`
- `/api/agent/workspaces/{workspaceId}/elements/{elementId}/context`
- `/api/agent/workspaces/{workspaceId}/context`
- `/api/agent/workspaces/{workspaceId}/query`
- `/api/agent/workspaces/{workspaceId}/elements/{elementId}/dependencies`
- `/api/agent/workspaces/{workspaceId}/impact-analysis`
- `/api/agent/workspaces/{workspaceId}/validation/query`
- `/api/agent/workspaces/{workspaceId}/external-resources`
- `/api/agent/workspaces/{workspaceId}/resolve-reference`
- `/api/agent/workspaces/{workspaceId}/llm-context`
- `GET|POST /api/agent/workspaces/{workspaceId}/proposals`
- `POST /api/agent/workspaces/{workspaceId}/proposals/validate`
- `GET /api/agent/workspaces/{workspaceId}/proposals/{proposalId}`
- `POST /api/agent/workspaces/{workspaceId}/proposals/{proposalId}/apply`
- `POST /api/agent/workspaces/{workspaceId}/proposals/{proposalId}/reject`

Bearer tokens are accepted only under `/api/agent` and `/api/proposals`. Every other path, including
workspace creation, requires Basic Auth.

The full OpenAPI UI is available at `/api/docs` when the app is running.

## Testing

Backend tests:

```bash
mvn test
```

Full build and verification:

```bash
mvn clean verify
```

Frontend unit tests:

```bash
cd frontend
npm test
```

Playwright end-to-end tests:

```bash
cd frontend
npm run test:e2e
```

Playwright expects the Quarkus app to be running. To target a non-default port:

```bash
PLAYWRIGHT_BASE_URL=http://localhost:18080 npm run test:e2e
```

## Sample Workspace

The database seed creates an anonymous sample named `Anonymous Reference Architecture`. It includes:

- Three people
- One software system
- Six containers
- Nine components
- Three data stores
- Three external systems
- Multiple system context, container, component, and data-flow views
- Metadata badges, ownership, lifecycle data, runbooks, dashboards, and external links

The sample is intentionally anonymous and demonstrates a moderately complex C4 model without using a real company or product name.

## Known Limitations

- This is an MVP, not a full enterprise architecture platform.
- Structurizr merge import is deferred; create-new and replace are supported.
- Diagram SVG/PNG export is frontend-only. Server-side diagram rendering is prepared conceptually but not implemented.
- Basic Auth and seeded API tokens are available, but OIDC/SSO, user management, token creation UI, token rotation UI, and RBAC are deferred.
- Agent proposals can be applied or rejected as a whole, individually or in bulk. Per-change accept/reject is not implemented yet.
- `created_by` and `updated_by` are written as `local-user` for every change. The audit columns exist but do not yet record the authenticated principal.
- The agent API is deterministic and template-based; it does not invoke an LLM internally.
- Codebase scanning is intentionally not implemented. External agents should analyze repositories outside YaC4e and submit evidence-backed proposals that reference files, classes, folders, commits, or URLs.
- React Flow, ELK, and export tooling are loaded eagerly, so the production bundle is larger than it would be with route-level code splitting.

## Documentation

- [Architecture](docs/architecture.md)
- [API notes](docs/api.md)
- [Model format](docs/model-format.md)
- [Export design](docs/export-design.md)
- [Canonical JSON Schema](docs/canonical-model.schema.json)

## License

YaC4e is released under the MIT License. See [LICENSE](LICENSE).
