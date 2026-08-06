import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Check, GitPullRequestArrow, RefreshCw, X } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { api } from '../../api/client';
import type { AgentProposal, AgentProposalSummary, Metadata } from '../../types/model';

interface Props {
  open: boolean;
  workspaceId: string;
  onClose: () => void;
}

export function ProposalPanel({ open, workspaceId, onClose }: Props) {
  const queryClient = useQueryClient();
  const proposals = useQuery({ queryKey: ['agent-proposals', workspaceId], queryFn: () => api.proposals(workspaceId), enabled: open });
  const reviewableProposals = useMemo(
    () => (proposals.data ?? []).filter((proposal) => proposal.status === 'PENDING' || proposal.status === 'VALIDATION_FAILED'),
    [proposals.data]
  );
  const [selectedId, setSelectedId] = useState<string>();
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

  const completeAndRefresh = async () => {
    setSelectedId(undefined);
    await invalidate();
  };
  const apply = useMutation({ mutationFn: () => api.applyProposal(workspaceId, selectedId!), onSuccess: completeAndRefresh });
  const reject = useMutation({ mutationFn: () => api.rejectProposal(workspaceId, selectedId!), onSuccess: completeAndRefresh });

  if (!open) return null;

  return (
    <div className="modal-backdrop">
      <section className="proposal-modal">
        <header>
          <h2><GitPullRequestArrow size={18} /> Proposals</h2>
          <button className="icon-button" onClick={onClose} title="Close"><X size={16} /></button>
        </header>
        <div className="proposal-grid">
          <aside className="proposal-list">
            <button type="button" className="secondary-action inline-action" onClick={() => proposals.refetch()}><RefreshCw size={14} /> Refresh</button>
            {reviewableProposals.map((proposal) => (
              <ProposalRow key={proposal.id} proposal={proposal} selected={proposal.id === selectedId} onSelect={() => setSelectedId(proposal.id)} />
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
        {(apply.error || reject.error) && <div className="form-error">{String((apply.error ?? reject.error)?.message)}</div>}
        <footer className="proposal-actions">
          <button type="button" className="secondary-action" disabled={!selectedId || detail.data?.status === 'APPLIED'} onClick={() => reject.mutate()}><X size={15} /> Reject</button>
          <button type="button" className="primary-action" disabled={!selectedId || detail.data?.status !== 'PENDING'} onClick={() => apply.mutate()}><Check size={15} /> Apply</button>
        </footer>
      </section>
    </div>
  );
}

function ProposalRow({ proposal, selected, onSelect }: { proposal: AgentProposalSummary; selected: boolean; onSelect: () => void }) {
  return (
    <button type="button" className={`proposal-row ${selected ? 'active' : ''}`} onClick={onSelect}>
      <strong>{proposal.summary || 'Untitled proposal'}</strong>
      <span>{proposal.status} · {proposal.changeCount} changes</span>
      <small>{sourceLabel(proposal.source)}</small>
    </button>
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
