import { afterEach, describe, expect, it, vi } from 'vitest';
import { boundsForViewport, ellipsize, renderSvgGraph, viewportZoom, type SvgExportOptions } from '../../src/features/exports/exportService';

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

describe('zoom independence', () => {
  function viewportWithNodes(zoom: number) {
    const viewport = document.createElement('div');
    viewport.className = 'react-flow__viewport';
    stubRect(viewport, { left: 0, top: 0, right: 800, bottom: 600, width: 800, height: 600 });
    vi.stubGlobal('getComputedStyle', () => ({ transform: `matrix(${zoom}, 0, 0, ${zoom}, 0, 0)` }));

    // Two 260x150 nodes at diagram coords (0,0) and (400,300), as the browser would report them
    // after the viewport scale transform is applied.
    for (const [x, y] of [[0, 0], [400, 300]]) {
      const node = document.createElement('div');
      node.className = 'react-flow__node';
      stubRect(node, {
        left: x * zoom, top: y * zoom,
        right: (x + 260) * zoom, bottom: (y + 150) * zoom,
        width: 260 * zoom, height: 150 * zoom
      });
      viewport.appendChild(node);
    }
    return viewport;
  }

  afterEach(() => vi.unstubAllGlobals());

  it('reads the scale out of the viewport transform', () => {
    vi.stubGlobal('getComputedStyle', () => ({ transform: 'matrix(0.5, 0, 0, 0.5, 120, 40)' }));
    expect(viewportZoom(document.createElement('div'))).toBe(0.5);

    vi.stubGlobal('getComputedStyle', () => ({ transform: 'none' }));
    expect(viewportZoom(document.createElement('div'))).toBe(1);
  });

  it('produces the same diagram bounds whatever the zoom', () => {
    const atFullZoom = boundsForViewport(viewportWithNodes(1), 0);
    const zoomedOut = boundsForViewport(viewportWithNodes(0.4), 0);
    const zoomedIn = boundsForViewport(viewportWithNodes(2.5), 0);

    // 400 + 260 wide, 300 + 150 tall, in diagram units.
    expect(atFullZoom).toMatchObject({ x: 0, y: 0, width: 660, height: 450 });
    expect(zoomedOut).toMatchObject(atFullZoom);
    expect(zoomedIn).toMatchObject(atFullZoom);
  });

  it('covers nodes lying outside the visible viewport', () => {
    const viewport = viewportWithNodes(1);
    const offscreen = document.createElement('div');
    offscreen.className = 'react-flow__node';
    // Far to the right and below anything on screen.
    stubRect(offscreen, { left: 3000, top: 2000, right: 3260, bottom: 2150, width: 260, height: 150 });
    viewport.appendChild(offscreen);

    const bounds = boundsForViewport(viewport, 0);

    expect(bounds.width).toBe(3260);
    expect(bounds.height).toBe(2150);
  });

  it('applies the margin around the full extent', () => {
    const bounds = boundsForViewport(viewportWithNodes(1), 48);

    expect(bounds).toMatchObject({ x: -48, y: -48, width: 660 + 96, height: 450 + 96 });
  });
});

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
