import { MarkerType, type Edge, type Node } from '@xyflow/react';
import type { ArchitectureElement, ArchitectureRelationship, DiagramView, ValidationIssue } from '../../types/model';
import type { C4EdgeData, C4NodeData } from './diagramTypes';

export function toReactFlow(
  view: DiagramView,
  elements: ArchitectureElement[],
  relationships: ArchitectureRelationship[],
  issues: ValidationIssue[],
  highlightedId?: string
): { nodes: Node<C4NodeData>[]; edges: Edge<C4EdgeData>[] } {
  const elementById = new Map(elements.map((element) => [element.id, element]));
  const visibleElementIds = new Set(view.elements.filter((member) => member.visible).map((member) => member.elementId));
  const nodes = view.elements
    .filter((member) => member.visible)
    .flatMap((member) => {
      const element = elementById.get(member.elementId);
      if (!element) return [];
      const warningCount = issues.filter((issue) => issue.elementId === element.id && issue.severity !== 'INFO').length;
      return [
        {
          id: element.id,
          type: nodeTypeFor(element.type, hasVisibleChild(element.id, elements, visibleElementIds)),
          position: { x: member.x, y: member.y },
          width: member.width,
          height: member.height,
          data: {
            element,
            warningCount,
            linkCount: element.linkCount,
            highlighted: highlightedId === element.id
          },
          style: { width: member.width, height: member.height, zIndex: member.zIndex }
        }
      ];
    });

  const relationshipById = new Map(relationships.map((relationship) => [relationship.id, relationship]));
  const edges = view.relationships
    .filter((member) => member.visible)
    .flatMap((member) => {
      const relationship = relationshipById.get(member.relationshipId);
      if (!relationship || !visibleElementIds.has(relationship.sourceElementId) || !visibleElementIds.has(relationship.targetElementId)) {
        return [];
      }
      return [
        {
          id: relationship.id,
          type: 'c4Relationship',
          source: relationship.sourceElementId,
          target: relationship.targetElementId,
          label: relationship.description || relationship.type,
          data: { relationship, label: relationship.description || relationship.type },
          markerEnd: { type: MarkerType.ArrowClosed }
        }
      ];
    });
  return { nodes, edges };
}

export function toLayoutPayload(viewVersion: number, nodes: Node[], edges: Edge[]) {
  return {
    viewVersion,
    elements: nodes.map((node, index) => ({
      elementId: node.id,
      x: node.position.x,
      y: node.position.y,
      width: node.measured?.width ?? numberStyle(node.style?.width) ?? node.width ?? 260,
      height: node.measured?.height ?? numberStyle(node.style?.height) ?? node.height ?? 150,
      locked: Boolean(node.dragging === false && node.selectable === false),
      visible: true,
      zIndex: index + 1,
      displaySettings: {}
    })),
    relationships: edges.map((edge) => ({
      relationshipId: edge.id,
      visible: !edge.hidden,
      displaySettings: {}
    }))
  };
}

export function nodeTypeFor(type: ArchitectureElement['type'], hasVisibleChild = false) {
  if ((type === 'SOFTWARE_SYSTEM' || type === 'CONTAINER') && hasVisibleChild) {
    return 'boundary-node';
  }
  return `${type.toLowerCase().replaceAll('_', '-')}-node`;
}

function hasVisibleChild(elementId: string, elements: ArchitectureElement[], visibleElementIds: Set<string>) {
  return elements.some((element) => element.parentElementId === elementId && visibleElementIds.has(element.id));
}

function numberStyle(value: unknown) {
  if (typeof value === 'number') return value;
  if (typeof value === 'string') {
    const parsed = Number.parseFloat(value);
    return Number.isFinite(parsed) ? parsed : undefined;
  }
  return undefined;
}
