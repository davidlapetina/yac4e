import { describe, expect, it } from 'vitest';
import { matchesFilter } from '../../src/features/model/ModelExplorer';
import { bySeverity } from '../../src/features/validation/ValidationPanel';
import type { ValidationIssue } from '../../src/types/model';

const element = { name: 'Governance Platform', description: 'Evaluates policy rules', technology: 'Quarkus' };

describe('model explorer filter', () => {
  it('matches everything when the filter is blank', () => {
    expect(matchesFilter(element, '')).toBe(true);
    expect(matchesFilter(element, '   ')).toBe(true);
  });

  it('matches on name, description and technology, case-insensitively', () => {
    expect(matchesFilter(element, 'governance')).toBe(true);
    expect(matchesFilter(element, 'POLICY')).toBe(true);
    expect(matchesFilter(element, 'quarkus')).toBe(true);
  });

  it('does not match unrelated text', () => {
    expect(matchesFilter(element, 'kafka')).toBe(false);
  });

  it('tolerates missing optional fields', () => {
    expect(matchesFilter({ name: 'A', description: '', technology: null }, 'a')).toBe(true);
    expect(matchesFilter({ name: 'A', description: '', technology: null }, 'z')).toBe(false);
  });

  it('ignores surrounding whitespace in the filter', () => {
    expect(matchesFilter(element, '  quarkus  ')).toBe(true);
  });
});

function issue(severity: ValidationIssue['severity'], code: string): ValidationIssue {
  return { severity, code, message: code, elementId: null, relationshipId: null, recommendedAction: '' };
}

describe('validation ordering', () => {
  it('puts errors first, then warnings, then info', () => {
    const ordered = bySeverity([issue('INFO', 'i1'), issue('WARNING', 'w1'), issue('ERROR', 'e1'), issue('WARNING', 'w2')]);

    expect(ordered.map((entry) => entry.code)).toEqual(['e1', 'w1', 'w2', 'i1']);
  });

  it('does not mutate the input array', () => {
    const input = [issue('INFO', 'i1'), issue('ERROR', 'e1')];
    bySeverity(input);

    expect(input.map((entry) => entry.code)).toEqual(['i1', 'e1']);
  });

  it('handles an empty issue list', () => {
    expect(bySeverity([])).toEqual([]);
  });
});
