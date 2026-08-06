import { Handle, NodeResizer, Position, type NodeProps } from '@xyflow/react';
import { AlertTriangle, Database, ExternalLink, FileCode2, Layers3, Link2, Server, UserRound } from 'lucide-react';
import type { C4NodeData } from './diagramTypes';
import type { Node } from '@xyflow/react';

const iconByType = {
  PERSON: UserRound,
  SOFTWARE_SYSTEM: Layers3,
  CONTAINER: Server,
  COMPONENT: FileCode2,
  DATA_STORE: Database,
  EXTERNAL_SYSTEM: ExternalLink
};

const stereotypeByType = {
  PERSON: 'Person',
  SOFTWARE_SYSTEM: 'Software System',
  CONTAINER: 'Container',
  COMPONENT: 'Component',
  DATA_STORE: 'Data Store',
  EXTERNAL_SYSTEM: 'External System'
};

export function C4Node({ data, selected }: NodeProps<Node<C4NodeData>>) {
  const element = data.element;
  const Icon = iconByType[element.type];
  const ownership = asRecord(element.metadata.ownership);
  const lifecycle = asRecord(element.metadata.lifecycle);
  const owner = String(ownership.team ?? ownership.technicalOwner ?? 'Unowned');
  const status = String(lifecycle.status ?? 'DRAFT');
  const tags = asRecord(element.metadata.classification).tags;
  return (
    <div
      className={`c4-node c4-node-${element.type.toLowerCase()} ${data.highlighted ? 'is-highlighted' : ''}`}
      data-c4-node="true"
      data-element-id={element.id}
      data-element-type={element.type}
      data-element-name={element.name}
      data-element-description={element.description}
      data-element-technology={element.technology ?? ''}
      data-element-owner={owner}
      data-element-status={status}
      data-element-tags={Array.isArray(tags) ? tags.join(',') : ''}
      data-warning-count={data.warningCount}
      data-link-count={data.linkCount}
    >
      <NodeResizer isVisible={selected} minWidth={170} minHeight={108} />
      <Handle type="target" position={Position.Left} />
      <Handle type="source" position={Position.Right} />
      <div className="node-header">
        <Icon size={15} aria-hidden />
        <span>{stereotypeByType[element.type]}</span>
      </div>
      <div className="node-name">{element.name}</div>
      {element.technology && <div className="node-tech">{element.technology}</div>}
      <div className="node-description">{element.description || 'No description'}</div>
      <div className="node-badges">
        <span className="owner-badge" title={owner}>{owner}</span>
        <span className="status-badge" title={status}>{status}</span>
        {data.warningCount > 0 && (
          <span className="warn">
            <AlertTriangle size={11} /> {data.warningCount}
          </span>
        )}
        {data.linkCount > 0 && (
          <span className="count-badge">
            <Link2 size={11} /> {data.linkCount}
          </span>
        )}
      </div>
    </div>
  );
}

export function BoundaryNode(props: NodeProps<Node<C4NodeData>>) {
  const element = props.data.element;
  return (
    <div className="c4-boundary-node" data-boundary-parent-id={element.id}>
      <C4Node {...props} />
    </div>
  );
}

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value) ? (value as Record<string, unknown>) : {};
}
