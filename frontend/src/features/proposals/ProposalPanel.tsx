import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Check, GitPullRequestArrow, RefreshCw, X } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { ModalShell } from '../../components/ModalShell';
import { api } from '../../api/client';
import type { AgentProposal, AgentProposalSummary, Metadata } from '../../types/model';

interface Props {
  open: boolean;
  workspaceId: string;
  onClose: () => void;
}

export interface BulkOutcome {
  succeeded: string[];
  failed: Array<{ id: string; message: string }>;
}

/**
 * Runs one proposal action at a time. Applying proposals mutates the shared model, so these are
 * deliberately sequential rather than concurrent, and one failure must not abandon the rest.
 */
export async function runProposalsInSequence(
  ids: string[],
  action: (id: string) => Promise<unknown>,
  onProgress?: (completed: number, total: number) => void
): Promise<BulkOutcome> {
  const succeeded: string[] = [];
  const failed: Array<{ id: string; message: string }> = [];
  for (const id of ids) {
    try {
      await action(id);
      succeeded.push(id);
    } catch (error) {
      failed.push({ id, message: error instanceof Error ? error.message : String(error) });
    }
    onProgress?.(succeeded.length + failed.length, ids.length);
  }
  return { succeeded, failed };
}

export function canApply(proposal: Pick<AgentProposalSummary, 'status'>) {
  return proposal.status === 'PENDING';
}

export function canReject(proposal: Pick<AgentProposalSummary, 'status'>) {
  return proposal.status !== 'APPLIED';
}

export function ProposalPanel({ open, workspaceId, onClose }: Props) {
  const queryClient = useQueryClient();
  const proposals = useQuery({ queryKey: ['agent-proposals', workspaceId], queryFn: () => api.proposals(workspaceId), enabled: open });
  const reviewableProposals = useMemo(
    () => (proposals.data ?? []).filter((proposal) => proposal.status === 'PENDING' || proposal.status === 'VALIDATION_FAILED'),
    [proposals.data]
  );
  const [selectedId, setSelectedId] = useState<string>();
  const [checkedIds, setCheckedIds] = useState<string[]>([]);
  const [busy, setBusy] = useState<{ mode: 'apply' | 'reject'; completed: number; total: number }>();
  const [outcome, setOutcome] = useState<BulkOutcome>();
  const detail = useQuery({
    queryKey: ['agent-proposal', workspaceId, selectedId],
    queryFn: () => api.proposal(workspaceId, selectedId!),
    enabled: open && Boolean(selectedId) && reviewableProposals.some((proposal) => proposal.id === selectedId)
  });

  useEffect(() => {
    if (selectedId && reviewableProposals.some((proposal) => proposal.id === selectedId)) {
      return;
    }
    if (reviewableProposals[0]) {
      setSelectedId(reviewableProposals[0].id);
      return;
    }
    setSelectedId(undefined);
  }, [reviewableProposals, selectedId]);

  // Drop ids that left the review queue so a stale tick cannot act on an applied proposal.
  useEffect(() => {
    setCheckedIds((current) => {
      const stillReviewable = current.filter((id) => reviewableProposals.some((proposal) => proposal.id === id));
      return stillReviewable.length === current.length ? current : stillReviewable;
    });
  }, [reviewableProposals]);

  // The footer acts on the ticked proposals, falling back to whichever one is open.
  const targetIds = checkedIds.length > 0 ? checkedIds : selectedId ? [selectedId] : [];
  const targets = targetIds
    .map((id) => reviewableProposals.find((proposal) => proposal.id === id))
    .filter((proposal): proposal is AgentProposalSummary => Boolean(proposal));
  const applicable = targets.filter(canApply);
  const rejectable = targets.filter(canReject);
  const blockedCount = reviewableProposals.filter((proposal) => !canApply(proposal)).length;

  const invalidate = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['agent-proposals', workspaceId] }),
      queryClient.invalidateQueries({ queryKey: ['agent-proposal', workspaceId] }),
      queryClient.invalidateQueries({ queryKey: ['elements', workspaceId] }),
      queryClient.invalidateQueries({ queryKey: ['relationships', workspaceId] }),
      queryClient.invalidateQueries({ queryKey: ['views', workspaceId] }),
      queryClient.invalidateQueries({ queryKey: ['metadata-definitions', workspaceId] }),
      queryClient.invalidateQueries({ queryKey: ['validation', workspaceId] })
    ]);
  };

  const runBulk = async (mode: 'apply' | 'reject') => {
    const queue = (mode === 'apply' ? applicable : rejectable).map((proposal) => proposal.id);
    if (queue.length === 0 || busy) return;
    setOutcome(undefined);
    setBusy({ mode, completed: 0, total: queue.length });
    const result = await runProposalsInSequence(
      queue,
      (id) => (mode === 'apply' ? api.applyProposal(workspaceId, id) : api.rejectProposal(workspaceId, id)),
      (completed, total) => setBusy({ mode, completed, total })
    );
    // Refresh before clearing the selection so the detail pane does not blank out and snap back.
    await invalidate();
    setCheckedIds(result.failed.map((failure) => failure.id));
    setBusy(undefined);
    setOutcome(result);
  };

  const toggleChecked = (id: string) =>
    setCheckedIds((current) => (current.includes(id) ? current.filter((value) => value !== id) : [...current, id]));

  const allChecked = reviewableProposals.length > 0 && checkedIds.length === reviewableProposals.length;
  const toggleAll = () => setCheckedIds(allChecked ? [] : reviewableProposals.map((proposal) => proposal.id));

  if (!open) return null;

  return (
    <ModalShell className="proposal-modal" label="Agent proposals" onClose={onClose} dismissible={!busy}>
        <header>
          <h2>
            <GitPullRequestArrow size={18} /> Proposals
            <span className="proposal-count">
              {reviewableProposals.length} awaiting review{blockedCount > 0 ? ` · ${blockedCount} cannot be applied` : ''}
            </span>
          </h2>
          <button className="icon-button" onClick={onClose} title="Close"><X size={16} /></button>
        </header>
        <div className="proposal-grid">
          <aside className="proposal-list">
            <div className="proposal-list-toolbar">
              <label className="proposal-select-all">
                <input
                  type="checkbox"
                  checked={allChecked}
                  disabled={reviewableProposals.length === 0 || Boolean(busy)}
                  onChange={toggleAll}
                />
                {checkedIds.length > 0 ? `${checkedIds.length} selected` : 'Select all'}
              </label>
              <button type="button" className="secondary-action inline-action" disabled={Boolean(busy)} onClick={() => proposals.refetch()}><RefreshCw size={14} /> Refresh</button>
            </div>
            {reviewableProposals.map((proposal) => (
              <ProposalRow
                key={proposal.id}
                proposal={proposal}
                selected={proposal.id === selectedId}
                checked={checkedIds.includes(proposal.id)}
                disabled={Boolean(busy)}
                onSelect={() => setSelectedId(proposal.id)}
                onToggle={() => toggleChecked(proposal.id)}
              />
            ))}
            {reviewableProposals.length === 0 && <div className="empty-state">No pending proposals.</div>}
          </aside>
          <section className="proposal-detail">
            {detail.data ? (
              <ProposalDetail proposal={detail.data} />
            ) : (
              <div className="empty-state">Select a proposal.</div>
            )}
          </section>
        </div>
        {busy && <div className="proposal-progress" role="status">{busy.mode === 'apply' ? 'Applying' : 'Rejecting'} {busy.completed} of {busy.total}…</div>}
        {outcome && outcome.failed.length > 0 && (
          <div className="form-error" role="alert">
            {outcome.succeeded.length > 0 && `${outcome.succeeded.length} succeeded. `}
            {outcome.failed.length} failed: {outcome.failed[0].message}
            {outcome.failed.length > 1 ? ` (and ${outcome.failed.length - 1} more; still selected)` : ''}
          </div>
        )}
        {outcome && outcome.failed.length === 0 && outcome.succeeded.length > 0 && (
          <div className="proposal-progress" role="status">{outcome.succeeded.length} proposal{outcome.succeeded.length === 1 ? '' : 's'} processed.</div>
        )}
        {targets.length > 0 && applicable.length === 0 && (
          <div className="proposal-progress">Selected proposal{targets.length === 1 ? '' : 's'} failed validation and can only be rejected.</div>
        )}
        <footer className="proposal-actions">
          <button type="button" className="secondary-action" disabled={rejectable.length === 0 || Boolean(busy)} onClick={() => runBulk('reject')}>
            <X size={15} /> Reject{rejectable.length > 1 ? ` ${rejectable.length}` : ''}
          </button>
          <button type="button" className="primary-action" disabled={applicable.length === 0 || Boolean(busy)} onClick={() => runBulk('apply')}>
            <Check size={15} /> Apply{applicable.length > 1 ? ` ${applicable.length}` : ''}
          </button>
        </footer>
    </ModalShell>
  );
}

function ProposalRow({ proposal, selected, checked, disabled, onSelect, onToggle }: {
  proposal: AgentProposalSummary;
  selected: boolean;
  checked: boolean;
  disabled: boolean;
  onSelect: () => void;
  onToggle: () => void;
}) {
  return (
    <div className={`proposal-row-shell ${selected ? 'active' : ''}`}>
      <input
        type="checkbox"
        checked={checked}
        disabled={disabled}
        aria-label={`Select proposal ${proposal.summary || 'Untitled proposal'}`}
        onChange={onToggle}
      />
      <button type="button" className="proposal-row" onClick={onSelect}>
        <strong>{proposal.summary || 'Untitled proposal'}</strong>
        <span>{proposal.status} · {proposal.changeCount} changes</span>
        <small>{sourceLabel(proposal.source)}</small>
      </button>
    </div>
  );
}

function ProposalDetail({ proposal }: { proposal: AgentProposal }) {
  return (
    <>
      <div className="proposal-heading">
        <div>
          <strong>{proposal.summary || 'Untitled proposal'}</strong>
          <span>{proposal.status}</span>
        </div>
        <small>{new Date(proposal.createdAt).toLocaleString()}</small>
      </div>
      <ValidationSummary proposal={proposal} />
      <div className="proposal-source">{sourceLabel(proposal.source)}</div>
      <div className="proposal-changes">
        {proposal.changes.map((change) => (
          <article key={change.id} className="proposal-change">
            <header>
              <div className="proposal-change-title">
                <strong>{change.sequenceNumber}. {change.action}</strong>
                <span>{payloadName(change.payload) || change.clientReference || change.targetEntityType || 'Change'}</span>
              </div>
            </header>
            <PayloadSummary payload={change.payload} />
            {change.evidence.length > 0 && (
              <div className="proposal-evidence">
                {change.evidence.map((item, index) => <code key={index}>{evidenceLabel(item)}</code>)}
              </div>
            )}
          </article>
        ))}
      </div>
    </>
  );
}

function ValidationSummary({ proposal }: { proposal: AgentProposal }) {
  const errors = proposal.validation.errors.length;
  const warnings = proposal.validation.warnings.length;
  return <div className={`proposal-validation ${errors ? 'error' : warnings ? 'warning' : ''}`}>{errors} errors · {warnings} warnings</div>;
}

function PayloadSummary({ payload }: { payload: Metadata }) {
  const entity = (payload.element ?? payload.relationship ?? payload.link ?? payload.metadataDefinition ?? payload.view) as Metadata | undefined;
  const description = entity && typeof entity === 'object' ? String(entity.description ?? entity.url ?? '') : '';
  if (!description) return null;
  return <p>{description}</p>;
}

function payloadName(payload: Metadata) {
  const entity = (payload.element ?? payload.relationship ?? payload.link ?? payload.metadataDefinition ?? payload.view) as Metadata | undefined;
  if (!entity || typeof entity !== 'object') return '';
  return String(entity.name ?? entity.label ?? entity.description ?? '');
}

function sourceLabel(source: Metadata) {
  const agent = String(source.agent ?? source.source ?? 'agent');
  const repository = String(source.repository ?? source.repo ?? '');
  const commit = String(source.commit ?? source.branch ?? '');
  return [agent, repository, commit].filter(Boolean).join(' · ');
}

function evidenceLabel(item: Metadata) {
  const path = String(item.path ?? item.file ?? item.url ?? item.symbol ?? 'evidence');
  const line = item.lineStart ?? item.line;
  return line ? `${path}:${line}` : path;
}
