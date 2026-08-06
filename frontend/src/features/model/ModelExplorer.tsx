import { AlertCircle, AlertTriangle, Box, Database, FileCode2, Layers3, PanelLeftClose, Plus, Search, Server, UserRound, X } from 'lucide-react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
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

/** Narrows the tree by name, description or technology. Case-insensitive; blank matches all. */
export function matchesFilter(element: Pick<ArchitectureElement, 'name' | 'description' | 'technology'>, filter: string) {
  const needle = filter.trim().toLowerCase();
  if (!needle) return true;
  return [element.name, element.description, element.technology]
    .some((field) => typeof field === 'string' && field.toLowerCase().includes(needle));
}

export function ModelExplorer({ workspaceId, elements, views, currentView, issues }: Props) {
  const store = useEditorStore();
  const queryClient = useQueryClient();
  const [filter, setFilter] = useState('');
  const visibleIds = new Set(currentView?.elements.map((item) => item.elementId) ?? []);
  const severityByElementId = severityByElement(issues);

  const filtered = useMemo(() => elements.filter((element) => matchesFilter(element, filter)), [elements, filter]);
  const placedIds = useMemo(
    () => new Set(views.flatMap((view) => view.elements.map((member) => member.elementId))),
    [views]
  );
  const unplaced = filtered.filter((element) => !placedIds.has(element.id));
  const filtering = filter.trim().length > 0;

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
      <div className="panel-title">
        <span>Model</span>
        <button
          type="button"
          className="icon-button"
          onClick={store.toggleLeftPanel}
          title="Collapse model panel"
          aria-label="Collapse model panel"
          aria-expanded
        >
          <PanelLeftClose size={15} />
        </button>
      </div>
      <div className="explorer-filter">
        <Search size={14} />
        <input
          value={filter}
          placeholder="Filter model"
          aria-label="Filter model"
          onChange={(event) => setFilter(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Escape') {
              event.stopPropagation();
              setFilter('');
            }
          }}
        />
        {filter && (
          <button type="button" className="icon-button" onClick={() => setFilter('')} title="Clear filter" aria-label="Clear filter">
            <X size={13} />
          </button>
        )}
      </div>
      {createElement.isError && <div className="form-error">Could not create the element. {String(createElement.error?.message ?? '')}</div>}

      <section className="explorer-section">
        <div className="section-title"><span>Views</span><span className="section-count">{views.length}</span></div>
        {views.map((view) => (
          <button key={view.id} className={`tree-row ${store.currentViewId === view.id ? 'active' : ''}`} onClick={() => store.setView(view.id)}>
            <Layers3 size={15} />
            <span>{view.name}</span>
          </button>
        ))}
        {views.length === 0 && <div className="empty-state">No views yet.</div>}
      </section>

      {groups.map((group) => {
        const Icon = icons[group.type];
        const items = filtered.filter((element) => element.type === group.type);
        const total = elements.filter((element) => element.type === group.type).length;
        // While filtering, hide groups with nothing to show instead of a wall of empty headers.
        if (filtering && items.length === 0) return null;
        return (
          <section className="explorer-section" key={group.type}>
            <div className="section-title">
              <span>{group.label}</span>
              <span className="section-count">{filtering ? `${items.length}/${total}` : total}</span>
              <button type="button" className="icon-button" onClick={() => createElement.mutate(group.type)} title={`Create ${group.label}`}>
                <Plus size={14} />
              </button>
            </div>
            {items.map((element) => (
              <ElementRow
                key={element.id}
                element={element}
                Icon={Icon}
                severity={severityByElementId.get(element.id)}
                inCurrentView={visibleIds.has(element.id)}
                selected={store.selectedElementId === element.id}
                onSelect={() => store.selectElement(element.id)}
              />
            ))}
          </section>
        );
      })}

      {unplaced.length > 0 && (
        <section className="explorer-section">
          <div className="section-title">
            <span>On no view</span>
            <span className="section-count">{unplaced.length}</span>
          </div>
          <div className="section-hint">Drag onto the canvas to place them.</div>
          {unplaced.map((element) => (
            <ElementRow
              key={element.id}
              element={element}
              Icon={icons[element.type]}
              severity={severityByElementId.get(element.id)}
              inCurrentView={visibleIds.has(element.id)}
              selected={store.selectedElementId === element.id}
              onSelect={() => store.selectElement(element.id)}
            />
          ))}
        </section>
      )}

      {filtering && filtered.length === 0 && <div className="empty-state">No elements match “{filter}”.</div>}
    </aside>
  );
}

function ElementRow({ element, Icon, severity, inCurrentView, selected, onSelect }: {
  element: ArchitectureElement;
  Icon: typeof Layers3;
  severity?: Severity;
  inCurrentView: boolean;
  selected: boolean;
  onSelect: () => void;
}) {
  return (
    <button
      draggable
      onDragStart={(event) => event.dataTransfer.setData('application/yac4e-element', element.id)}
      className={`tree-row ${selected ? 'active' : ''}`}
      title={element.description || element.name}
      onClick={onSelect}
    >
      <ValidationMarker severity={severity} />
      <Icon size={15} />
      <span>{element.name}</span>
      <span
        className={`visibility-dot ${inCurrentView ? 'visible' : ''}`}
        title={inCurrentView ? 'Visible in current view' : 'Not in current view'}
        aria-label={inCurrentView ? 'Visible in current view' : 'Not in current view'}
      />
    </button>
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
