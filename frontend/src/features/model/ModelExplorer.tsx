import { AlertCircle, AlertTriangle, Box, Database, FileCode2, Layers3, Plus, Server, UserRound } from 'lucide-react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../../api/client';
import { useEditorStore } from '../../stores/editorStore';
import type { ArchitectureElement, ArchitectureElementType, DiagramView, Severity, ValidationIssue } from '../../types/model';

interface Props {
  workspaceId: string;
  elements: ArchitectureElement[];
  views: DiagramView[];
  currentView?: DiagramView;
  issues: ValidationIssue[];
}

const groups: Array<{ type: ArchitectureElementType; label: string }> = [
  { type: 'SOFTWARE_SYSTEM', label: 'Systems' },
  { type: 'CONTAINER', label: 'Containers' },
  { type: 'COMPONENT', label: 'Components' },
  { type: 'DATA_STORE', label: 'Data Stores' },
  { type: 'PERSON', label: 'People' },
  { type: 'EXTERNAL_SYSTEM', label: 'External Systems' }
];

const icons = {
  SOFTWARE_SYSTEM: Layers3,
  CONTAINER: Server,
  COMPONENT: FileCode2,
  DATA_STORE: Database,
  PERSON: UserRound,
  EXTERNAL_SYSTEM: Box
};

export function ModelExplorer({ workspaceId, elements, views, currentView, issues }: Props) {
  const store = useEditorStore();
  const queryClient = useQueryClient();
  const visibleIds = new Set(currentView?.elements.map((item) => item.elementId) ?? []);
  const severityByElementId = severityByElement(issues);
  const createElement = useMutation({
    mutationFn: (type: ArchitectureElementType) =>
      api.createElement(workspaceId, {
        type,
        name: `New ${labelFor(type)}`,
        description: '',
        technology: type === 'CONTAINER' || type === 'COMPONENT' ? 'TBD' : '',
        metadata: { ownership: {}, lifecycle: { status: 'DRAFT' }, custom: {} }
      }),
    onSuccess: async (element) => {
      store.selectElement(element.id);
      await queryClient.invalidateQueries({ queryKey: ['elements', workspaceId] });
      await queryClient.invalidateQueries({ queryKey: ['validation', workspaceId] });
    }
  });

  return (
    <aside className="left-panel">
      <div className="panel-title">Model</div>
      <section className="explorer-section">
        <div className="section-title">Views</div>
        {views.map((view) => (
          <button key={view.id} className={`tree-row ${store.currentViewId === view.id ? 'active' : ''}`} onClick={() => store.setView(view.id)}>
            <Layers3 size={15} />
            <span>{view.name}</span>
          </button>
        ))}
      </section>
      {groups.map((group) => {
        const Icon = icons[group.type];
        const items = elements.filter((element) => element.type === group.type);
        return (
          <section className="explorer-section" key={group.type}>
            <div className="section-title">
              <span>{group.label}</span>
              <button type="button" className="icon-button" onClick={() => createElement.mutate(group.type)} title={`Create ${group.label}`}>
                <Plus size={14} />
              </button>
            </div>
            {items.map((element) => (
              <button
                key={element.id}
                draggable
                onDragStart={(event) => event.dataTransfer.setData('application/yac4e-element', element.id)}
                className={`tree-row ${store.selectedElementId === element.id ? 'active' : ''}`}
                onClick={() => store.selectElement(element.id)}
              >
                <ValidationMarker severity={severityByElementId.get(element.id)} />
                <Icon size={15} />
                <span>{element.name}</span>
                <span
                  className={`visibility-dot ${visibleIds.has(element.id) ? 'visible' : ''}`}
                  title={visibleIds.has(element.id) ? 'Visible in current view' : 'Not in current view'}
                  aria-label={visibleIds.has(element.id) ? 'Visible in current view' : 'Not in current view'}
                />
              </button>
            ))}
          </section>
        );
      })}
      <section className="explorer-section">
        <div className="section-title">Unused Elements</div>
        {elements.filter((element) => !views.some((view) => view.elements.some((member) => member.elementId === element.id))).map((element) => (
          <button key={element.id} className="tree-row" onClick={() => store.selectElement(element.id)}>
            <ValidationMarker severity={severityByElementId.get(element.id)} />
            <span>{element.name}</span>
          </button>
        ))}
      </section>
    </aside>
  );
}

function ValidationMarker({ severity }: { severity?: Severity }) {
  if (!severity) return null;
  if (severity === 'ERROR') return <AlertCircle className="validation-marker error" size={13} aria-label="Has validation errors" />;
  if (severity === 'WARNING') return <AlertTriangle className="validation-marker warning" size={13} aria-label="Has validation warnings" />;
  return null;
}

function severityByElement(issues: ValidationIssue[]) {
  const severities = new Map<string, Severity>();
  for (const issue of issues) {
    if (!issue.elementId) continue;
    const current = severities.get(issue.elementId);
    if (current === 'ERROR') continue;
    if (issue.severity === 'ERROR' || issue.severity === 'WARNING') {
      severities.set(issue.elementId, issue.severity);
    }
  }
  return severities;
}

function labelFor(type: ArchitectureElementType) {
  return type.toLowerCase().replaceAll('_', ' ');
}
