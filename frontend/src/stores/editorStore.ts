import type { Edge, Node } from '@xyflow/react';
import { create } from 'zustand';

export type SaveStatus = 'saved' | 'unsaved' | 'saving' | 'failed';
export type EditorMode = 'select' | 'connect' | 'create';

interface DiagramSnapshot {
  nodes: Node<Record<string, unknown>>[];
  edges: Edge<Record<string, unknown>>[];
}

interface EditorState {
  currentWorkspaceId?: string;
  currentViewId?: string;
  selectedElementId?: string;
  selectedRelationshipId?: string;
  highlightedId?: string;
  saveStatus: SaveStatus;
  mode: EditorMode;
  leftPanelOpen: boolean;
  rightPanelOpen: boolean;
  bottomPanelOpen: boolean;
  undoStack: DiagramSnapshot[];
  redoStack: DiagramSnapshot[];
  setWorkspace: (workspaceId: string) => void;
  setView: (viewId: string) => void;
  selectElement: (elementId?: string) => void;
  selectRelationship: (relationshipId?: string) => void;
  highlight: (id?: string) => void;
  setSaveStatus: (status: SaveStatus) => void;
  setMode: (mode: EditorMode) => void;
  toggleLeftPanel: () => void;
  toggleRightPanel: () => void;
  toggleBottomPanel: () => void;
  pushHistory: (snapshot: DiagramSnapshot) => void;
  undo: (current: DiagramSnapshot) => DiagramSnapshot | undefined;
  redo: (current: DiagramSnapshot) => DiagramSnapshot | undefined;
  clearHistory: () => void;
}

export const useEditorStore = create<EditorState>((set, get) => ({
  saveStatus: 'saved',
  mode: 'select',
  leftPanelOpen: true,
  rightPanelOpen: true,
  // The validation issue list starts collapsed; the summary bar is always visible.
  bottomPanelOpen: false,
  undoStack: [],
  redoStack: [],
  setWorkspace: (workspaceId) => set({ currentWorkspaceId: workspaceId, currentViewId: undefined, selectedElementId: undefined, selectedRelationshipId: undefined }),
  setView: (viewId) => set({ currentViewId: viewId, selectedElementId: undefined, selectedRelationshipId: undefined }),
  selectElement: (elementId) => set({ selectedElementId: elementId, selectedRelationshipId: undefined }),
  selectRelationship: (relationshipId) => set({ selectedRelationshipId: relationshipId, selectedElementId: undefined }),
  highlight: (id) => set({ highlightedId: id }),
  setSaveStatus: (saveStatus) => set({ saveStatus }),
  setMode: (mode) => set({ mode }),
  toggleLeftPanel: () => set((state) => ({ leftPanelOpen: !state.leftPanelOpen })),
  toggleRightPanel: () => set((state) => ({ rightPanelOpen: !state.rightPanelOpen })),
  toggleBottomPanel: () => set((state) => ({ bottomPanelOpen: !state.bottomPanelOpen })),
  pushHistory: (snapshot) =>
    set((state) => ({
      undoStack: [...state.undoStack, snapshot].slice(-100),
      redoStack: [],
      saveStatus: 'unsaved'
    })),
  undo: (current) => {
    const { undoStack, redoStack } = get();
    const previous = undoStack.at(-1);
    if (!previous) return undefined;
    set({ undoStack: undoStack.slice(0, -1), redoStack: [...redoStack, current].slice(-100), saveStatus: 'unsaved' });
    return previous;
  },
  redo: (current) => {
    const { undoStack, redoStack } = get();
    const next = redoStack.at(-1);
    if (!next) return undefined;
    set({ redoStack: redoStack.slice(0, -1), undoStack: [...undoStack, current].slice(-100), saveStatus: 'unsaved' });
    return next;
  },
  clearHistory: () => set({ undoStack: [], redoStack: [], saveStatus: 'saved' })
}));
