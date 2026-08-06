import { expect, test, type APIRequestContext, type Page } from '@playwright/test';
import { mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

test('scenario 1: create a diagram and persist layout', async ({ page, request }) => {
  const fixture = await seedWorkspace(request, 'Scenario 1');
  const worker = await post(request, `/api/workspaces/${fixture.workspace.id}/elements`, {
    type: 'CONTAINER',
    name: 'Worker API',
    description: 'Processes background governance work',
    parentElementId: fixture.system.id,
    technology: 'Java',
    metadata: { ownership: { team: 'Governance Team' }, lifecycle: { status: 'PRODUCTION', reviewedAt: '2026-08-01' }, operations: { runbookUrl: 'https://example.com/runbook' }, custom: {} }
  });
  await post(request, `/api/workspaces/${fixture.workspace.id}/elements/${worker.id}/links`, {
    provider: 'JIRA',
    type: 'ISSUE',
    label: 'Worker backlog',
    url: 'https://company.atlassian.net/browse/GOV-100',
    externalId: 'GOV-100',
    metadata: {}
  });
  const relationship = await post(request, `/api/workspaces/${fixture.workspace.id}/relationships`, {
    sourceElementId: fixture.container.id,
    targetElementId: worker.id,
    type: 'CALLS',
    description: 'Dispatches work',
    technology: 'HTTPS',
    protocol: 'HTTPS',
    metadata: {}
  });
  await post(request, `/api/workspaces/${fixture.workspace.id}/views/${fixture.containerView.id}/elements`, viewElement(worker.id, 640, 140));
  await post(request, `/api/workspaces/${fixture.workspace.id}/views/${fixture.containerView.id}/relationships`, { relationshipId: relationship.id, visible: true, displaySettings: {} });

  await openWorkspace(page, fixture.workspace.id);
  await expect(page.locator('.node-name', { hasText: 'Worker API' })).toBeVisible();
  await page.getByTitle('Save layout').click();
  await page.reload();
  await openWorkspace(page, fixture.workspace.id);
  await expect(page.locator('.node-name', { hasText: 'Worker API' })).toBeVisible();
});

test('scenario 2: the same element keeps different coordinates in multiple views', async ({ request }) => {
  const fixture = await seedWorkspace(request, 'Scenario 2');
  const secondView = await post(request, `/api/workspaces/${fixture.workspace.id}/views`, {
    name: 'Alternate container view',
    description: '',
    type: 'CONTAINER',
    scopeElementId: fixture.system.id,
    layoutDirection: 'LEFT_TO_RIGHT',
    settings: {}
  });
  await post(request, `/api/workspaces/${fixture.workspace.id}/views/${secondView.id}/elements`, viewElement(fixture.container.id, 920, 360));

  await put(request, `/api/workspaces/${fixture.workspace.id}/views/${fixture.containerView.id}/layout`, {
    viewVersion: fixture.containerView.version,
    elements: [viewElement(fixture.system.id, 80, 80), viewElement(fixture.container.id, 220, 120), viewElement(fixture.database.id, 580, 120)],
    relationships: [{ relationshipId: fixture.relationship.id, visible: true, displaySettings: {} }]
  });
  const views = await get(request, `/api/workspaces/${fixture.workspace.id}/views`);
  const first = views.find((view: { id: string }) => view.id === fixture.containerView.id);
  const second = views.find((view: { id: string }) => view.id === secondView.id);

  expect(first.elements.find((member: { elementId: string }) => member.elementId === fixture.container.id)).toMatchObject({ x: 220, y: 120 });
  expect(second.elements.find((member: { elementId: string }) => member.elementId === fixture.container.id)).toMatchObject({ x: 920, y: 360 });
});

test('scenario 3: export SVG and PNG for a view with off-screen nodes', async ({ page, request }) => {
  const fixture = await seedWorkspace(request, 'Scenario 3');
  await put(request, `/api/workspaces/${fixture.workspace.id}/views/${fixture.containerView.id}/layout`, {
    viewVersion: fixture.containerView.version,
    elements: [viewElement(fixture.system.id, -600, -200), viewElement(fixture.container.id, 120, 100), viewElement(fixture.database.id, 1200, 650)],
    relationships: [{ relationshipId: fixture.relationship.id, visible: true, displaySettings: {} }]
  });

  await openWorkspace(page, fixture.workspace.id);
  await exportDownload(page, 'SVG', '.svg');
  await exportDownload(page, 'PNG', '.png');
});

test('scenario 4: canonical JSON export imports as a new workspace', async ({ request }) => {
  const fixture = await seedWorkspace(request, 'Scenario 4');
  const exported = await text(request, `/api/workspaces/${fixture.workspace.id}/exports/model.json`);
  const imported = await post(request, '/api/imports/model', exported, { 'Content-Type': 'application/json' });
  const elements = await get(request, `/api/workspaces/${imported.workspaceId}/elements`);
  const relationships = await get(request, `/api/workspaces/${imported.workspaceId}/relationships`);
  const views = await get(request, `/api/workspaces/${imported.workspaceId}/views`);

  expect(elements.length).toBeGreaterThanOrEqual(3);
  expect(relationships.length).toBeGreaterThanOrEqual(1);
  expect(views.length).toBeGreaterThanOrEqual(1);
});

test('scenario 5: Structurizr DSL import preview creates a workspace from the UI', async ({ page }) => {
  const dir = mkdtempSync(join(tmpdir(), 'yac4e-'));
  const dslPath = join(dir, 'workspace.dsl');
  writeFileSync(dslPath, `
    workspace "Imported DSL Scenario" {
      model {
        user = person "Platform Administrator"
        system = softwareSystem "Reference Operations Platform" {
          api = container "Governance Service" "Evaluates policy" "Java / Quarkus"
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
  `);

  await page.goto('/');
  await expect(page.locator('.app-shell')).toBeVisible();
  await expect(page.locator('button[title="Import model"]')).toBeVisible();
  await page.evaluate(() => {
    document.querySelector<HTMLButtonElement>('button[title="Import model"]')?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
  });
  await page.locator('input[type="file"]').setInputFiles(dslPath);
  await page.getByRole('button', { name: /Validate and preview/ }).click();
  await expect(page.getByText('Valid import')).toBeVisible();
  await page.getByRole('button', { name: /Confirm import/ }).click();
  await expect(page.locator('.tree-row').filter({ hasText: 'Governance Service' }).first()).toBeVisible();
  await page.getByTitle('Automatic layout').click();
  await exportDownload(page, 'SVG', '.svg');
});

test('agent API scenario: context, dependencies, impact, reference resolution and markdown context', async ({ request }) => {
  const fixture = await seedWorkspace(request, 'Agent API');

  const context = await get(request, `/api/agent/workspaces/${fixture.workspace.id}/elements/${fixture.container.id}/context`);
  expect(context.trace.workspaceId).toBe(fixture.workspace.id);
  expect(context.element.id).toBe(fixture.container.id);

  const dependencies = await get(request, `/api/agent/workspaces/${fixture.workspace.id}/elements/${fixture.container.id}/dependencies?direction=OUTGOING&depth=2`);
  expect(dependencies.trace.includedElementIds).toContain(fixture.container.id);

  const impact = await post(request, `/api/agent/workspaces/${fixture.workspace.id}/impact-analysis`, {
    changedElementIds: [fixture.container.id],
    directions: ['OUTGOING'],
    relationshipTypes: ['READS_FROM', 'CALLS', 'DEPENDS_ON'],
    maximumDepth: 2,
    includeOwners: true,
    includeViews: true,
    includeLinks: true
  });
  expect(impact.trace.workspaceId).toBe(fixture.workspace.id);

  const resolved = await post(request, `/api/agent/workspaces/${fixture.workspace.id}/resolve-reference`, { reference: 'GOV-100' });
  expect(resolved.matches[0].element.id).toBe(fixture.container.id);

  const llmContext = await post(request, `/api/agent/workspaces/${fixture.workspace.id}/llm-context`, {
    elementIds: [fixture.container.id],
    query: 'governance',
    relationshipDepth: 2,
    includeMetadata: true,
    includeLinks: true,
    includeValidation: true,
    maximumCharacters: 30_000,
    format: 'MARKDOWN'
  });
  expect(llmContext.content).toContain('Governance Service');
  expect(llmContext.includedElementIds).toContain(fixture.container.id);
});

async function seedWorkspace(request: APIRequestContext, name: string) {
  const workspace = await post(request, '/api/workspaces', { name: `YaC4e ${name} ${Date.now()}`, description: 'E2E workspace' });
  const system = await post(request, `/api/workspaces/${workspace.id}/elements`, {
    type: 'SOFTWARE_SYSTEM',
    name: 'Reference Operations Platform',
    description: 'Architecture platform',
    metadata: { ownership: { team: 'Governance Team' }, lifecycle: { status: 'PRODUCTION', reviewedAt: '2026-08-01' }, operations: { runbookUrl: 'https://example.com/runbook' }, custom: {} }
  });
  const container = await post(request, `/api/workspaces/${workspace.id}/elements`, {
    type: 'CONTAINER',
    name: 'Governance Service',
    description: 'Evaluates policy decisions',
    parentElementId: system.id,
    technology: 'Java / Quarkus',
    metadata: { ownership: { team: 'Governance Team' }, classification: { domain: 'Governance', tags: ['security'] }, lifecycle: { status: 'PRODUCTION', reviewedAt: '2026-08-01' }, operations: { runbookUrl: 'https://example.com/runbook' }, custom: {} }
  });
  const database = await post(request, `/api/workspaces/${workspace.id}/elements`, {
    type: 'DATA_STORE',
    name: 'Governance Database',
    description: 'Stores decisions',
    parentElementId: system.id,
    technology: 'PostgreSQL',
    metadata: { ownership: { team: 'Data Team' }, lifecycle: { status: 'PRODUCTION', reviewedAt: '2026-08-01' }, operations: { runbookUrl: 'https://example.com/runbook' }, custom: {} }
  });
  const relationship = await post(request, `/api/workspaces/${workspace.id}/relationships`, {
    sourceElementId: container.id,
    targetElementId: database.id,
    type: 'READS_FROM',
    description: 'Reads governance decisions',
    technology: 'JDBC',
    protocol: 'TCP',
    metadata: {}
  });
  await post(request, `/api/workspaces/${workspace.id}/elements/${container.id}/links`, {
    provider: 'JIRA',
    type: 'ISSUE',
    label: 'Governance ticket',
    url: 'https://company.atlassian.net/browse/GOV-100',
    externalId: 'GOV-100',
    metadata: {}
  });
  const containerView = await post(request, `/api/workspaces/${workspace.id}/views`, {
    name: 'Container View',
    description: '',
    type: 'CONTAINER',
    scopeElementId: system.id,
    layoutDirection: 'LEFT_TO_RIGHT',
    settings: {}
  });
  await post(request, `/api/workspaces/${workspace.id}/views/${containerView.id}/elements`, viewElement(system.id, 40, 40, 680, 360));
  await post(request, `/api/workspaces/${workspace.id}/views/${containerView.id}/elements`, viewElement(container.id, 140, 120));
  await post(request, `/api/workspaces/${workspace.id}/views/${containerView.id}/elements`, viewElement(database.id, 480, 120));
  await post(request, `/api/workspaces/${workspace.id}/views/${containerView.id}/relationships`, { relationshipId: relationship.id, visible: true, displaySettings: {} });
  return { workspace, system, container, database, relationship, containerView };
}

function viewElement(elementId: string, x: number, y: number, width = 260, height = 150) {
  return { elementId, x, y, width, height, locked: false, visible: true, zIndex: 1, displaySettings: {} };
}

async function openWorkspace(page: Page, workspaceId: string) {
  await page.goto(`/?workspaceId=${workspaceId}`);
  await expect(page.locator('.react-flow')).toBeVisible();
}

async function exportDownload(page: Page, format: 'SVG' | 'PNG', extension: string) {
  await page.getByRole('button', { name: /^Export$/ }).click();
  await page.getByRole('button', { name: format, exact: true }).click();
  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: new RegExp(`Export ${format}`) }).click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toContain(extension);
  const failure = await download.failure();
  expect(failure).toBeNull();
}

async function get(request: APIRequestContext, path: string) {
  const response = await request.get(path);
  expect(response.ok()).toBeTruthy();
  return response.json();
}

async function text(request: APIRequestContext, path: string) {
  const response = await request.get(path);
  expect(response.ok()).toBeTruthy();
  return response.text();
}

async function post(request: APIRequestContext, path: string, body: unknown, headers?: Record<string, string>) {
  const response = await request.post(path, typeof body === 'string' ? { data: body, headers } : { data: body, headers });
  expect(response.ok()).toBeTruthy();
  return response.json();
}

async function put(request: APIRequestContext, path: string, body: unknown) {
  const response = await request.put(path, { data: body });
  expect(response.ok()).toBeTruthy();
  return response.json();
}
