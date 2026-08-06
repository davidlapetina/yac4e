import { AlertCircle, AlertTriangle, Info } from 'lucide-react';
import { useEditorStore } from '../../stores/editorStore';
import type { ValidationIssue } from '../../types/model';

export function ValidationPanel({ issues }: { issues: ValidationIssue[] }) {
  const store = useEditorStore();
  const icon = {
    ERROR: AlertCircle,
    WARNING: AlertTriangle,
    INFO: Info
  };
  const errors = issues.filter((issue) => issue.severity === 'ERROR');
  const warnings = issues.filter((issue) => issue.severity === 'WARNING');
  const info = issues.filter((issue) => issue.severity === 'INFO');
  const firstIssue = errors[0] ?? warnings[0] ?? info[0];
  return (
    <footer className="validation-panel">
      <div className={`validation-summary ${errors.length ? 'error' : warnings.length ? 'warning' : 'ok'}`}>
        {errors.length > 0 && <AlertCircle size={14} />}
        {errors.length === 0 && warnings.length > 0 && <AlertTriangle size={14} />}
        {errors.length === 0 && warnings.length === 0 && <Info size={14} />}
        Validation: {warnings.length} warnings · {errors.length} errors
      </div>
      {firstIssue && (
        <button
          type="button"
          className={`validation-focus ${firstIssue.severity.toLowerCase()}`}
          onClick={() => {
            if (firstIssue.elementId) store.selectElement(firstIssue.elementId);
            if (firstIssue.relationshipId) store.selectRelationship(firstIssue.relationshipId);
            store.highlight(firstIssue.elementId ?? firstIssue.relationshipId ?? undefined);
          }}
        >
          {(() => {
            const Icon = icon[firstIssue.severity];
            return <Icon size={14} />;
          })()}
          <span>{firstIssue.code}</span>
          <strong>{firstIssue.message}</strong>
        </button>
      )}
    </footer>
  );
}
