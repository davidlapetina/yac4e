import {
  Background,
  Controls,
  MiniMap,
  ReactFlow,
  type Connection,
  type Edge,
  type Node,
  useEdgesState,
  useNodesState,
  useReactFlow
} from '@xyflow/react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useRef } from 'react';
import { api } from '../../api/client';
import { useEditorStore } from '../../stores/editorStore';
import type { ArchitectureElement, ArchitectureRelationship, DiagramView, ValidationIssue } from '../../types/model';
import type { C4EdgeData, C4NodeData } from './diagramTypes';
import { toLayoutPayload, toReactFlow } from './diagramConversion';
import { BoundaryNode, C4Node } from './C4Node';
import { C4RelationshipEdge } from './C4RelationshipEdge';
import { diagramLayoutService } from './layoutService';

interface Props {
  workspaceId: string;
  view: DiagramView;
  elements: ArchitectureElement[];
  relationships: ArchitectureRelationship[];
  issues: ValidationIssue[];
  onOpenExport: () => void;
}

const nodeTypes = {
  'person-node': C4Node,
  'software-system-node': C4Node,
  'container-node': C4Node,
  'component-node': C4Node,
  'data-store-node': C4Node,
  'external-system-node': C4Node,
  'boundary-node': BoundaryNode
};

const edgeTypes = {
  c4Relationship: C4RelationshipEdge
};

export function DiagramCanvas({ workspaceId, view, elements, relationships, issues, onOpenExport }: Props) {
  const queryClient = useQueryClient();
  const store = useEditorStore();
  const reactFlow = useReactFlow();
  const converted = useMemo(() => toReactFlow(view, elements, relationships, issues, store.highlightedId), [view, elements, relationships, issues, store.highlightedId]);
  const [nodes, setNodes, onNodesChange] = useNodesState(converted.nodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState(converted.edges);
  const lastLoadedSignature = useRef('');

  useEffect(() => {
    const signature = diagramSignature(view, elements, relationships, issues, store.highlightedId);
    if (lastLoadedSignature.current === signature) {
      return;
    }
    lastLoadedSignature.current = signature;
    setNodes(converted.nodes);
    setEdges(converted.edges);
    store.clearHistory();
  }, [converted.nodes, converted.edges, elements, issues, relationships, setEdges, setNodes, store, view]);

  const invalidate = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['views', workspaceId] }),
      queryClient.invalidateQueries({ queryKey: ['relationships', workspaceId] }),
      queryClient.invalidateQueries({ queryKey: ['validation', workspaceId] })
    ]);
  };

  const createRelationship = useMutation({
    mutationFn: async (connection: Connection) => {
      if (!connection.source || !connection.target) throw new Error('Relationship endpoints are required');
      const relationship = await api.createRelationship(workspaceId, {
        sourceElementId: connection.source,
        targetElementId: connection.target,
        type: 'CALLS',
        description: 'Calls',
        technology: 'HTTPS',
        protocol: 'HTTPS',
        metadata: {}
      });
      await api.addRelationshipToView(workspaceId, view.id, { relationshipId: relationship.id, visible: true, displaySettings: {} });
    },
    onSuccess: invalidate
  });

  const saveLayout = useMutation({
    mutationFn: () => api.saveLayout(workspaceId, view.id, toLayoutPayload(view.version, nodes, edges)),
    onMutate: () => store.setSaveStatus('saving'),
    onSuccess: async () => {
      store.setSaveStatus('saved');
      await invalidate();
    },
    onError: () => store.setSaveStatus('failed')
  });

  const addElementToView = useMutation({
    mutationFn: async ({ elementId, x, y, parentElementId }: { elementId: string; x: number; y: number; parentElementId?: string }) => {
      if (parentElementId) {
        const element = elements.find((item) => item.id === elementId);
        if (element && element.parentElementId !== parentElementId) {
          await api.updateElement(workspaceId, element.id, {
            type: element.type,
            name: element.name,
            description: element.description,
            parentElementId,
            technology: element.technology,
            metadata: element.metadata,
            version: element.version
          });
        }
      }
      return api.addElementToView(workspaceId, view.id, { elementId, x, y, width: 260, height: 150, locked: false, visible: true, zIndex: nodes.length + 1, displaySettings: {} });
    },
    onSuccess: invalidate
  });

  const reparentElement = useMutation({
    mutationFn: async ({ elementId, parentElementId }: { elementId: string; parentElementId: string }) => {
      const element = elements.find((item) => item.id === elementId);
      if (!element || element.parentElementId === parentElementId) return;
      await api.updateElement(workspaceId, element.id, {
        type: element.type,
        name: element.name,
        description: element.description,
        parentElementId,
        technology: element.technology,
        metadata: element.metadata,
        version: element.version
      });
    },
    onSuccess: invalidate
  });

  const removeFromView = useMutation({
    mutationFn: (ids: string[]) => Promise.all(ids.map((id) => api.removeElementFromView(workspaceId, view.id, id))),
    onSuccess: invalidate
  });

  const currentSnapshot = useCallback(() => ({ nodes, edges }), [nodes, edges]);

  const autoLayout = async () => {
    store.pushHistory(currentSnapshot());
    const result = await diagramLayoutService.layout(nodes, edges, { direction: view.layoutDirection, nodeSpacing: 70, layerSpacing: 120 });
    setNodes(result.nodes);
    setEdges(result.edges);
    store.setSaveStatus('unsaved');
  };

  useEffect(() => {
    const listener = (event: KeyboardEvent) => {
      const meta = event.metaKey || event.ctrlKey;
      if (meta && event.key.toLowerCase() === 's') {
        event.preventDefault();
        saveLayout.mutate();
      }
      if (meta && event.key.toLowerCase() === 'z' && !event.shiftKey) {
        event.preventDefault();
        const previous = store.undo(currentSnapshot());
        if (previous) {
          setNodes(previous.nodes as Node<C4NodeData>[]);
          setEdges(previous.edges as Edge<C4EdgeData>[]);
        }
      }
      if (meta && event.key.toLowerCase() === 'z' && event.shiftKey) {
        event.preventDefault();
        const next = store.redo(currentSnapshot());
        if (next) {
          setNodes(next.nodes as Node<C4NodeData>[]);
          setEdges(next.edges as Edge<C4EdgeData>[]);
        }
      }
      if (meta && event.key.toLowerCase() === 'e') {
        event.preventDefault();
        onOpenExport();
      }
      if (event.key === 'Delete' || event.key === 'Backspace') {
        const selected = nodes.filter((node) => node.selected).map((node) => node.id);
        const selectedRelationships = edges.filter((edge) => edge.selected).map((edge) => edge.id);
        if (selected.length > 0) {
          store.pushHistory(currentSnapshot());
          removeFromView.mutate(selected);
        }
        if (selectedRelationships.length > 0) {
          store.pushHistory(currentSnapshot());
          Promise.all(selectedRelationships.map((id) => api.removeRelationshipFromView(workspaceId, view.id, id))).then(invalidate).catch(() => store.setSaveStatus('failed'));
        }
      }
    };
    window.addEventListener('keydown', listener);
    return () => window.removeEventListener('keydown', listener);
  }, [currentSnapshot, edges, nodes, onOpenExport, removeFromView, saveLayout, setEdges, setNodes, store]);

  return (
    <section className="canvas-shell" onDragOver={(event) => event.preventDefault()}>
      <ReactFlow
        nodes={nodes}
        edges={edges}
        nodeTypes={nodeTypes}
        edgeTypes={edgeTypes}
        fitView
        onNodesChange={(changes) => {
          onNodesChange(changes);
          if (changes.some((change) => change.type === 'position' || change.type === 'dimensions')) store.setSaveStatus('unsaved');
        }}
        onEdgesChange={onEdgesChange}
        onNodeDragStart={() => store.pushHistory(currentSnapshot())}
        onNodeDragStop={(_, node) => {
          const targetParentId = parentBoundaryForNode(node, nodes, elements);
          if (targetParentId && globalThis.confirm('Update this element hierarchy to match the boundary it was dropped into?')) {
            reparentElement.mutate({ elementId: node.id, parentElementId: targetParentId });
          }
        }}
        onNodeClick={(_, node) => store.selectElement(node.id)}
        onEdgeClick={(_, edge) => store.selectRelationship(edge.id)}
        onConnect={(connection) => {
          store.pushHistory(currentSnapshot());
          createRelationship.mutate(connection);
        }}
        onDrop={(event) => {
          const elementId = event.dataTransfer.getData('application/yac4e-element');
          if (!elementId) return;
          const position = reactFlow.screenToFlowPosition({ x: event.clientX, y: event.clientY });
          const parentElementId = parentBoundaryForPoint(position.x, position.y, nodes, elements, elementId);
          const shouldReparent = parentElementId && globalThis.confirm('Add this element to the boundary hierarchy?');
          store.pushHistory(currentSnapshot());
          addElementToView.mutate({ elementId, x: position.x, y: position.y, parentElementId: shouldReparent ? parentElementId : undefined });
        }}
      >
        <MiniMap pannable zoomable />
        <Controls />
        <Background gap={22} size={1} />
      </ReactFlow>
      <div className="canvas-actions">
        <button type="button" onClick={() => reactFlow.fitView()} title="Fit view">Fit</button>
        <button type="button" onClick={autoLayout} title="Automatic layout">Layout</button>
        <button type="button" onClick={() => saveLayout.mutate()} title="Save layout">{store.saveStatus === 'saving' ? 'Saving' : 'Save'}</button>
      </div>
    </section>
  );
}

function parentBoundaryForNode(node: Node<C4NodeData>, nodes: Node<C4NodeData>[], elements: ArchitectureElement[]) {
  const width = numberDimension(node.measured?.width ?? node.width ?? node.style?.width, 260);
  const height = numberDimension(node.measured?.height ?? node.height ?? node.style?.height, 150);
  return parentBoundaryForPoint(node.position.x + width / 2, node.position.y + height / 2, nodes, elements, node.id);
}

function parentBoundaryForPoint(x: number, y: number, nodes: Node<C4NodeData>[], elements: ArchitectureElement[], childElementId: string) {
  const child = elements.find((element) => element.id === childElementId);
  if (!child) return undefined;
  const candidates = nodes
    .filter((node) => node.id !== childElementId && node.type === 'boundary-node' && isValidParent(child.type, node.data.element.type))
    .filter((node) => {
      const width = numberDimension(node.measured?.width ?? node.width ?? node.style?.width, 260);
      const height = numberDimension(node.measured?.height ?? node.height ?? node.style?.height, 150);
      return x >= node.position.x && x <= node.position.x + width && y >= node.position.y && y <= node.position.y + height;
    })
    .sort((left, right) => area(left) - area(right));
  return candidates[0]?.id;
}

function isValidParent(childType: ArchitectureElement['type'], parentType: ArchitectureElement['type']) {
  if (childType === 'CONTAINER') return parentType === 'SOFTWARE_SYSTEM';
  if (childType === 'COMPONENT') return parentType === 'CONTAINER';
  if (childType === 'DATA_STORE') return parentType === 'SOFTWARE_SYSTEM' || parentType === 'CONTAINER';
  return false;
}

function area(node: Node<C4NodeData>) {
  return numberDimension(node.measured?.width ?? node.width ?? node.style?.width, 260) * numberDimension(node.measured?.height ?? node.height ?? node.style?.height, 150);
}

function numberDimension(value: unknown, fallback: number) {
  if (typeof value === 'number') return value;
  if (typeof value === 'string') {
    const parsed = Number.parseFloat(value);
    return Number.isFinite(parsed) ? parsed : fallback;
  }
  return fallback;
}

function diagramSignature(view: DiagramView, elements: ArchitectureElement[], relationships: ArchitectureRelationship[], issues: ValidationIssue[], highlightedId?: string) {
  return JSON.stringify({
    viewId: view.id,
    viewVersion: view.version,
    viewElements: view.elements.map((member) => [member.elementId, member.x, member.y, member.width, member.height, member.visible, member.zIndex]),
    viewRelationships: view.relationships.map((member) => [member.relationshipId, member.visible]),
    elements: elements.map((element) => [element.id, element.version, element.parentElementId, element.linkCount]),
    relationships: relationships.map((relationship) => [relationship.id, relationship.version]),
    issues: issues.map((issue) => [issue.code, issue.elementId, issue.relationshipId, issue.severity]),
    highlightedId
  });
}
