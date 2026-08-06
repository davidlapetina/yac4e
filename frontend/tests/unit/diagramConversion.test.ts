import { describe, expect, it } from 'vitest';
import { toLayoutPayload, toReactFlow } from '../../src/features/diagrams/diagramConversion';
import type { ArchitectureElement, ArchitectureRelationship, DiagramView } from '../../src/types/model';

const element: ArchitectureElement = {
  id: 'e1',
  workspaceId: 'w1',
  type: 'CONTAINER',
  name: 'Governance Service',
  description: 'Evaluates policy',
  technology: 'Quarkus',
  parentElementId: null,
  metadata: { ownership: { team: 'Gov' }, lifecycle: { status: 'PRODUCTION' } },
  version: 0,
  createdAt: '',
  updatedAt: '',
  linkCount: 2
};

const relationship: ArchitectureRelationship = {
  id: 'r1',
  workspaceId: 'w1',
  sourceElementId: 'e1',
  targetElementId: 'e2',
  type: 'CALLS',
  description: 'Calls API',
  metadata: {},
  version: 0,
  createdAt: '',
  updatedAt: ''
};

describe('diagram conversion', () => {
  it('keeps view-specific coordinates separate from elements', () => {
    const view: DiagramView = {
      id: 'v1',
      workspaceId: 'w1',
      name: 'View',
      description: '',
      type: 'CONTAINER',
      layoutDirection: 'LEFT_TO_RIGHT',
      settings: {},
      version: 4,
      createdAt: '',
      updatedAt: '',
      elements: [{ id: 'm1', viewId: 'v1', elementId: 'e1', x: 12, y: 34, width: 260, height: 150, locked: false, visible: true, zIndex: 1, displaySettings: {} }],
      relationships: []
    };
    const { nodes } = toReactFlow(view, [element], [relationship], [], undefined);
    expect(nodes[0].position).toEqual({ x: 12, y: 34 });
    expect(element).not.toHaveProperty('x');
  });

  it('creates batch layout payloads from React Flow state', () => {
    const payload = toLayoutPayload(4, [{ id: 'e1', position: { x: 10, y: 20 }, data: {}, width: 200, height: 100 }], []);
    expect(payload.viewVersion).toBe(4);
    expect(payload.elements[0]).toMatchObject({ elementId: 'e1', x: 10, y: 20, width: 200, height: 100 });
  });
});
