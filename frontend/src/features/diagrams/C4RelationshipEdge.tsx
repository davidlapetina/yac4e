import { EdgeLabelRenderer, getBezierPath, type EdgeProps } from '@xyflow/react';
import type { C4EdgeData } from './diagramTypes';
import type { Edge } from '@xyflow/react';

export function C4RelationshipEdge(props: EdgeProps<Edge<C4EdgeData>>) {
  const [edgePath, labelX, labelY] = getBezierPath(props);
  return (
    <>
      <path
        id={props.id}
        d={edgePath}
        fill="none"
        className="react-flow__edge-path c4-edge"
        markerEnd={typeof props.markerEnd === 'string' ? props.markerEnd : undefined}
        data-edge-id={props.id}
        data-source-id={props.source}
        data-target-id={props.target}
        data-relationship-type={props.data?.relationship.type ?? ''}
        data-relationship-label={props.data?.label ?? ''}
      />
      <EdgeLabelRenderer>
        <div
          className="edge-label"
          data-c4-edge-label={props.id}
          title={props.data?.label}
          style={{ transform: `translate(-50%, -50%) translate(${labelX}px, ${labelY}px)` }}
        >
          {props.data?.label}
        </div>
      </EdgeLabelRenderer>
    </>
  );
}
