import { AlertCircle, AlertTriangle, ChevronDown, ChevronUp, Info } from 'lucide-react';
import { useEditorStore } from '../../stores/editorStore';
import type { ValidationIssue } from '../../types/model';

const icon = {
  ERROR: AlertCircle,
  WARNING: AlertTriangle,
  INFO: Info
};

/** Errors first, then warnings, then info, so the most severe issue is always the one on top. */
export function bySeverity(issues: ValidationIssue[]) {
  const rank = { ERROR: 0, WARNING: 1, INFO: 2 };
  return [...issues].sort((left, right) => rank[left.severity] - rank[right.severity]);
}

export function ValidationPanel({ issues }: { issues: ValidationIssue[] }) {
  const store = useEditorStore();
  const expanded = store.bottomPanelOpen;
  const errors = issues.filter((issue) => issue.severity === 'ERROR');
  const warnings = issues.filter((issue) => issue.severity === 'WARNING');
  const ordered = bySeverity(issues);
  const firstIssue = ordered[0];

  const focusIssue = (issue: ValidationIssue) => {
    if (issue.elementId) store.selectElement(issue.elementId);
    if (issue.relationshipId) store.selectRelationship(issue.relationshipId);
    store.highlight(issue.elementId ?? issue.relationshipId ?? undefined);
  };

  return (
    <footer className={`validation-panel ${expanded ? 'expanded' : ''}`}>
      <div className="validation-bar">
        <div className={`validation-summary ${errors.length ? 'error' : warnings.length ? 'warning' : 'ok'}`}>
          {errors.length > 0 && <AlertCircle size={14} />}
          {errors.length === 0 && warnings.length > 0 && <AlertTriangle size={14} />}
          {errors.length === 0 && warnings.length === 0 && <Info size={14} />}
          Validation: {warnings.length} warnings · {errors.length} errors
        </div>
        {firstIssue && !expanded && (
          <button type="button" className={`validation-focus ${firstIssue.severity.toLowerCase()}`} onClick={() => focusIssue(firstIssue)}>
            {(() => {
              const Icon = icon[firstIssue.severity];
              return <Icon size={14} />;
            })()}
            <span>{firstIssue.code}</span>
            <strong>{firstIssue.message}</strong>
          </button>
        )}
        {issues.length > 0 && (
          // Previously only the first issue was ever reachable from here.
          <button
            type="button"
            className="validation-toggle"
            aria-expanded={expanded}
            onClick={store.toggleBottomPanel}
          >
            {expanded ? <ChevronDown size={14} /> : <ChevronUp size={14} />}
            {expanded ? 'Hide' : `All ${issues.length}`}
          </button>
        )}
      </div>
      {expanded && (
        <ul className="validation-list">
          {ordered.map((issue, index) => {
            const Icon = icon[issue.severity];
            return (
              <li key={`${issue.code}-${issue.elementId ?? issue.relationshipId ?? index}`}>
                <button type="button" className={`validation-row ${issue.severity.toLowerCase()}`} onClick={() => focusIssue(issue)}>
                  <Icon size={13} />
                  <span className="validation-code">{issue.code}</span>
                  <span className="validation-message">{issue.message}</span>
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </footer>
  );
}
