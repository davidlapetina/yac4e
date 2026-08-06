import { ReactFlowProvider } from '@xyflow/react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Download, FileInput, GitPullRequestArrow, Layers3, Plus, Save } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { api } from '../api/client';
import { DiagramCanvas } from '../features/diagrams/DiagramCanvas';
import { ExportModal } from '../features/exports/ExportModal';
import { ModelExplorer } from '../features/model/ModelExplorer';
import { ImportModal } from '../features/model/ImportModal';
import { PropertiesPanel } from '../features/model/PropertiesPanel';
import { ProposalPanel } from '../features/proposals/ProposalPanel';
import { SearchBox } from '../features/search/SearchBox';
import { ValidationPanel } from '../features/validation/ValidationPanel';
import { useEditorStore } from '../stores/editorStore';

export function App() {
  const store = useEditorStore();
  const queryClient = useQueryClient();
  const [exportOpen, setExportOpen] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [proposalsOpen, setProposalsOpen] = useState(false);
  const workspaces = useQuery({ queryKey: ['workspaces'], queryFn: api.workspaces });
  const routeWorkspaceId = new URLSearchParams(window.location.search).get('workspaceId') ?? undefined;
  const routeViewId = new URLSearchParams(window.location.search).get('viewId') ?? undefined;
  const workspaceId = routeWorkspaceId ?? store.currentWorkspaceId ?? workspaces.data?.[0]?.id;
  const elements = useQuery({ queryKey: ['elements', workspaceId], queryFn: () => api.elements(workspaceId!), enabled: Boolean(workspaceId) });
  const relationships = useQuery({ queryKey: ['relationships', workspaceId], queryFn: () => api.relationships(workspaceId!), enabled: Boolean(workspaceId) });
  const views = useQuery({ queryKey: ['views', workspaceId], queryFn: () => api.views(workspaceId!), enabled: Boolean(workspaceId) });
  const validation = useQuery({ queryKey: ['validation', workspaceId], queryFn: () => api.validation(workspaceId!), enabled: Boolean(workspaceId) });
  const metadataDefinitions = useQuery({ queryKey: ['metadata-definitions', workspaceId], queryFn: () => api.metadataDefinitions(workspaceId!), enabled: Boolean(workspaceId) });

  useEffect(() => {
    if (routeWorkspaceId && store.currentWorkspaceId !== routeWorkspaceId) {
      store.setWorkspace(routeWorkspaceId);
    }
  }, [routeWorkspaceId, store]);

  useEffect(() => {
    if (routeViewId && store.currentViewId !== routeViewId) {
      store.setView(routeViewId);
    }
  }, [routeViewId, store]);

  useEffect(() => {
    if (routeWorkspaceId) return;
    if (!store.currentWorkspaceId && workspaceId) store.setWorkspace(workspaceId);
  }, [routeWorkspaceId, store, workspaceId]);

  useEffect(() => {
    const firstView = views.data?.[0]?.id;
    if (!store.currentViewId && firstView) store.setView(firstView);
  }, [store, views.data]);

  const currentView = views.data?.find((view) => view.id === store.currentViewId) ?? views.data?.[0];
  const allIssues = useMemo(() => [...(validation.data?.errors ?? []), ...(validation.data?.warnings ?? []), ...(validation.data?.info ?? [])], [validation.data]);

  const createWorkspace = useMutation({
    mutationFn: () => api.createWorkspace({ name: 'New Architecture Workspace', description: '' }),
    onSuccess: async (workspace) => {
      store.setWorkspace(workspace.id);
      await queryClient.invalidateQueries({ queryKey: ['workspaces'] });
    }
  });

  const createView = useMutation({
    mutationFn: () => api.createView(workspaceId!, { name: 'Custom View', description: '', type: 'CUSTOM', layoutDirection: 'LEFT_TO_RIGHT', settings: {} }),
    onSuccess: async (view) => {
      store.setView(view.id);
      await queryClient.invalidateQueries({ queryKey: ['views', workspaceId] });
    }
  });

  if (workspaces.isLoading) {
    return <main className="loading-screen">Loading YaC4e</main>;
  }

  if (!workspaceId) {
    return (
      <main className="loading-screen">
        <h1>YaC4e</h1>
        <button className="primary-action" onClick={() => createWorkspace.mutate()}><Plus size={16} /> Create workspace</button>
      </main>
    );
  }

  return (
    <main className="app-shell">
      <header className="topbar">
        <div className="brand"><Layers3 size={20} /> <strong>YaC4e</strong></div>
        <select value={workspaceId} onChange={(event) => store.setWorkspace(event.target.value)}>
          {(workspaces.data ?? []).map((workspace) => <option key={workspace.id} value={workspace.id}>{workspace.name}</option>)}
        </select>
        <SearchBox workspaceId={workspaceId} />
        <button type="button" onClick={() => createView.mutate()} title="Create view"><Plus size={15} /> View</button>
        <button type="button" onClick={() => setProposalsOpen(true)} title="Review proposals"><GitPullRequestArrow size={15} /> Proposals</button>
        <button type="button" onClick={() => setExportOpen(true)} title="Export diagram"><Download size={15} /> Export</button>
        <a className="toolbar-link" href={api.exportModelJsonUrl(workspaceId)}><Save size={15} /> JSON</a>
        <a className="toolbar-link" href={api.exportModelYamlUrl(workspaceId)}><Save size={15} /> YAML</a>
        <button type="button" onClick={() => setImportOpen(true)} title="Import model"><FileInput size={15} /> Import</button>
        <button type="button" onClick={() => createWorkspace.mutate()} title="Create workspace"><Plus size={15} /> Workspace</button>
        <span className={`save-status ${store.saveStatus}`}>{store.saveStatus}</span>
      </header>
      <div className="editor-grid">
        <ModelExplorer workspaceId={workspaceId} elements={elements.data ?? []} views={views.data ?? []} currentView={currentView} issues={allIssues} />
        {currentView ? (
          <ReactFlowProvider>
            <DiagramCanvas
              workspaceId={workspaceId}
              view={currentView}
              elements={elements.data ?? []}
              relationships={relationships.data ?? []}
              issues={allIssues}
              onOpenExport={() => setExportOpen(true)}
            />
          </ReactFlowProvider>
        ) : (
          <section className="canvas-shell empty-state">Create a diagram view to start modeling.</section>
        )}
        <PropertiesPanel
          workspaceId={workspaceId}
          elements={elements.data ?? []}
          relationships={relationships.data ?? []}
          metadataDefinitions={metadataDefinitions.data ?? []}
          issues={allIssues}
        />
      </div>
      <ValidationPanel issues={allIssues} />
      <ExportModal open={exportOpen} onClose={() => setExportOpen(false)} viewName={currentView?.name ?? 'diagram'} />
      <ImportModal open={importOpen} onClose={() => setImportOpen(false)} workspaces={workspaces.data ?? []} currentWorkspaceId={workspaceId} />
      <ProposalPanel open={proposalsOpen} onClose={() => setProposalsOpen(false)} workspaceId={workspaceId} />
    </main>
  );
}
