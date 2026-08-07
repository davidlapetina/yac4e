import { describe, expect, it, vi } from 'vitest';
import { canApply, canReject, proposalIndicator, runProposalsInSequence } from '../../src/features/proposals/ProposalPanel';

describe('bulk proposal review', () => {
  it('processes every id and reports which succeeded', async () => {
    const action = vi.fn().mockResolvedValue(undefined);

    const outcome = await runProposalsInSequence(['a', 'b', 'c'], action);

    expect(action).toHaveBeenCalledTimes(3);
    expect(outcome.succeeded).toEqual(['a', 'b', 'c']);
    expect(outcome.failed).toEqual([]);
  });

  it('continues past a failure instead of abandoning the queue', async () => {
    const action = vi.fn(async (id: string) => {
      if (id === 'b') throw new Error('stale version');
    });

    const outcome = await runProposalsInSequence(['a', 'b', 'c'], action);

    expect(action).toHaveBeenCalledTimes(3);
    expect(outcome.succeeded).toEqual(['a', 'c']);
    expect(outcome.failed).toEqual([{ id: 'b', message: 'stale version' }]);
  });

  it('runs strictly one at a time so concurrent applies cannot interleave', async () => {
    let inFlight = 0;
    let maxInFlight = 0;
    const action = async () => {
      inFlight++;
      maxInFlight = Math.max(maxInFlight, inFlight);
      await Promise.resolve();
      inFlight--;
    };

    await runProposalsInSequence(['a', 'b', 'c', 'd'], action);

    expect(maxInFlight).toBe(1);
  });

  it('reports progress after each item', async () => {
    const progress: Array<[number, number]> = [];

    await runProposalsInSequence(['a', 'b'], async () => {}, (completed, total) => progress.push([completed, total]));

    expect(progress).toEqual([[1, 2], [2, 2]]);
  });

  it('handles an empty queue without calling the action', async () => {
    const action = vi.fn();
    const outcome = await runProposalsInSequence([], action);

    expect(action).not.toHaveBeenCalled();
    expect(outcome).toEqual({ succeeded: [], failed: [] });
  });

  it('shows a clear light when nothing is waiting', () => {
    expect(proposalIndicator([])).toMatchObject({ tone: 'clear', count: 0 });
    expect(proposalIndicator([{ status: 'APPLIED' }, { status: 'REJECTED' }])).toMatchObject({ tone: 'clear', count: 0 });
  });

  it('shows a pending light counting only reviewable proposals', () => {
    const indicator = proposalIndicator([{ status: 'PENDING' }, { status: 'PENDING' }, { status: 'APPLIED' }]);

    expect(indicator.tone).toBe('pending');
    expect(indicator.count).toBe(2);
    expect(indicator.label).toBe('2 proposals awaiting review');
  });

  it('escalates to blocked when any proposal failed validation', () => {
    const indicator = proposalIndicator([{ status: 'PENDING' }, { status: 'VALIDATION_FAILED' }]);

    expect(indicator.tone).toBe('blocked');
    expect(indicator.count).toBe(2);
    expect(indicator.label).toMatch(/failed validation.*awaiting review/);
  });

  it('uses singular wording for a single proposal', () => {
    expect(proposalIndicator([{ status: 'PENDING' }]).label).toBe('1 proposal awaiting review');
    expect(proposalIndicator([{ status: 'VALIDATION_FAILED' }]).label).toBe('1 proposal failed validation');
  });

  it('only allows applying pending proposals, but rejecting anything not applied', () => {
    expect(canApply({ status: 'PENDING' })).toBe(true);
    expect(canApply({ status: 'VALIDATION_FAILED' })).toBe(false);
    expect(canApply({ status: 'APPLIED' })).toBe(false);

    expect(canReject({ status: 'PENDING' })).toBe(true);
    expect(canReject({ status: 'VALIDATION_FAILED' })).toBe(true);
    expect(canReject({ status: 'APPLIED' })).toBe(false);
  });
});
