import { describe, expect, it } from 'vitest';
import { boundsForViewport, ellipsize, renderSvgGraph, type SvgExportOptions } from '../../src/features/exports/exportService';

const options: SvgExportOptions = {
  fileName: 'diagram.svg',
  background: 'white',
  margin: 0,
  scale: 1,
  includeDescription: true,
  includeTechnology: true,
  includeOwners: true,
  includeRelationshipLabels: true,
  includeLegend: false
};

describe('diagram SVG export', () => {
  it('renders native SVG nodes and relationship labels from the complete diagram bounds', () => {
    const viewport = document.createElement('div');
    stubRect(viewport, { left: 0, top: 0, right: 800, bottom: 600, width: 800, height: 600 });
    viewport.innerHTML = `
      <div class="react-flow__node">
        <div data-c4-node="true" data-element-id="e1" data-element-type="CONTAINER" data-element-name="Governance Service" data-element-description="Evaluates policy" data-element-technology="Quarkus" data-element-owner="Gov Team" data-element-status="PRODUCTION" data-warning-count="1" data-link-count="2"></div>
      </div>
      <div class="react-flow__node">
        <div data-c4-node="true" data-element-id="e2" data-element-type="DATA_STORE" data-element-name="Governance Database" data-element-description="Stores decisions" data-element-technology="PostgreSQL" data-element-owner="Data Team" data-element-status="PRODUCTION" data-warning-count="0" data-link-count="0"></div>
      </div>
      <svg><path class="c4-edge" data-edge-id="r1" data-source-id="e1" data-target-id="e2" data-relationship-label="Reads from"></path></svg>
    `;
    const first = viewport.querySelectorAll<HTMLElement>('[data-c4-node="true"]')[0];
    const second = viewport.querySelectorAll<HTMLElement>('[data-c4-node="true"]')[1];
    stubRect(first, { left: 100, top: 100, right: 360, bottom: 250, width: 260, height: 150 });
    stubRect(second, { left: 520, top: 120, right: 780, bottom: 270, width: 260, height: 150 });

    const svg = renderSvgGraph(viewport, boundsForViewport(viewport, 20), options);

    expect(svg).toContain('data-export-element-id="e1"');
    expect(svg).toContain('Governance Service');
    expect(svg).toContain('Quarkus');
    expect(svg).toContain('Gov Team');
    expect(svg).toContain('Reads from');
    expect(svg).not.toContain('foreignObject');
  });

  it('honors content flags', () => {
    const viewport = document.createElement('div');
    stubRect(viewport, { left: 0, top: 0, right: 800, bottom: 600, width: 800, height: 600 });
    viewport.innerHTML = `
      <div class="react-flow__node">
        <div data-c4-node="true" data-element-id="e1" data-element-type="CONTAINER" data-element-name="API" data-element-description="Hidden description" data-element-technology="Hidden technology" data-element-owner="Hidden owner" data-element-status="DRAFT" data-warning-count="0" data-link-count="0"></div>
      </div>
      <svg><path class="c4-edge" data-edge-id="r1" data-source-id="e1" data-target-id="e1" data-relationship-label="Hidden label"></path></svg>
    `;
    stubRect(viewport.querySelector<HTMLElement>('[data-c4-node="true"]')!, { left: 100, top: 100, right: 360, bottom: 250, width: 260, height: 150 });

    const svg = renderSvgGraph(viewport, boundsForViewport(viewport, 20), {
      ...options,
      includeDescription: false,
      includeTechnology: false,
      includeOwners: false,
      includeRelationshipLabels: false
    });

    expect(svg).not.toContain('Hidden description');
    expect(svg).not.toContain('Hidden technology');
    expect(svg).not.toContain('Hidden owner');
    expect(svg).not.toContain('Hidden label');
  });
});

function stubRect(element: Element, rect: Omit<DOMRect, 'x' | 'y' | 'toJSON'>) {
  element.getBoundingClientRect = () => ({
    ...rect,
    x: rect.left,
    y: rect.top,
    toJSON: () => ({})
  });
}

describe('relationship label clamping', () => {
  it('leaves labels that already fit untouched', () => {
    expect(ellipsize('Calls API', 132, 11)).toBe('Calls API');
  });

  it('ellipsizes a long description instead of letting it overflow the label box', () => {
    const long = 'Writes reconciled settlement records to the ledger store after validating every batch';
    const clamped = ellipsize(long, 132, 11);

    expect(clamped.endsWith('…')).toBe(true);
    expect(clamped.length).toBeLessThan(long.length);
    expect(long.startsWith(clamped.slice(0, -1).trimEnd())).toBe(true);
  });

  it('scales the budget with the font size', () => {
    const long = 'x'.repeat(200);

    expect(ellipsize(long, 132, 11).length).toBeLessThan(ellipsize(long, 264, 11).length);
    expect(ellipsize(long, 132, 22).length).toBeLessThan(ellipsize(long, 132, 11).length);
  });

  it('degrades safely at tiny widths', () => {
    expect(ellipsize('Something long', 1, 11)).toBe('…');
  });
});
