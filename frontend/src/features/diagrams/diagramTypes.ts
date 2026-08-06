import type { Edge, Node } from '@xyflow/react';
import type { ArchitectureElement, ArchitectureRelationship, DiagramView } from '../../types/model';

export interface C4NodeData extends Record<string, unknown> {
  element: ArchitectureElement;
  warningCount: number;
  linkCount: number;
  highlighted: boolean;
  locked: boolean;
  zIndex: number;
}

export interface C4EdgeData extends Record<string, unknown> {
  relationship: ArchitectureRelationship;
  label: string;
}

export type C4Node = Node<C4NodeData>;
export type C4Edge = Edge<C4EdgeData>;

export interface DiagramModel {
  view: DiagramView;
  elements: ArchitectureElement[];
  relationships: ArchitectureRelationship[];
}
