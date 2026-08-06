import ELK, { type ElkExtendedEdge, type ElkNode } from 'elkjs/lib/elk.bundled.js';
import type { Edge, Node } from '@xyflow/react';
import type { LayoutDirection } from '../../types/model';

export interface LayoutOptions {
  direction: LayoutDirection;
  nodeSpacing: number;
  layerSpacing: number;
}

export interface DiagramLayoutService {
  layout<TNode extends Node, TEdge extends Edge>(nodes: TNode[], edges: TEdge[], options: LayoutOptions): Promise<{ nodes: TNode[]; edges: TEdge[] }>;
}

const directionMap: Record<LayoutDirection, string> = {
  LEFT_TO_RIGHT: 'RIGHT',
  TOP_TO_BOTTOM: 'DOWN',
  RIGHT_TO_LEFT: 'LEFT',
  BOTTOM_TO_TOP: 'UP'
};

class ElkDiagramLayoutService implements DiagramLayoutService {
  private elk = new ELK();

  async layout<TNode extends Node, TEdge extends Edge>(nodes: TNode[], edges: TEdge[], options: LayoutOptions): Promise<{ nodes: TNode[]; edges: TEdge[] }> {
    const graph: ElkNode = {
      id: 'root',
      layoutOptions: {
        'elk.algorithm': 'layered',
        'elk.direction': directionMap[options.direction],
        'elk.spacing.nodeNode': String(options.nodeSpacing),
        'elk.layered.spacing.nodeNodeBetweenLayers': String(options.layerSpacing),
        'elk.edgeRouting': 'ORTHOGONAL'
      },
      children: nodes.map((node) => ({
        id: node.id,
        width: node.measured?.width ?? node.width ?? 260,
        height: node.measured?.height ?? node.height ?? 150
      })),
      edges: edges.map(
        (edge): ElkExtendedEdge => ({
          id: edge.id,
          sources: [edge.source],
          targets: [edge.target]
        })
      )
    };
    const result = await this.elk.layout(graph);
    const positions = new Map((result.children ?? []).map((child) => [child.id, { x: child.x ?? 0, y: child.y ?? 0 }]));
    return {
      nodes: nodes.map((node) => ({ ...node, position: positions.get(node.id) ?? node.position })),
      edges
    };
  }
}

export const diagramLayoutService: DiagramLayoutService = new ElkDiagramLayoutService();
