import { toPng } from 'html-to-image';

export interface SvgExportOptions {
  fileName: string;
  background: 'white' | 'transparent';
  margin: number;
  scale: number;
  includeDescription: boolean;
  includeTechnology: boolean;
  includeOwners: boolean;
  includeRelationshipLabels: boolean;
  includeLegend: boolean;
}

export interface PngExportOptions extends SvgExportOptions {
  pixelRatio: number;
}

export interface DiagramExportService {
  exportSvg(options: SvgExportOptions): Promise<Blob>;
  exportPng(options: PngExportOptions): Promise<Blob>;
}

class DomDiagramExportService implements DiagramExportService {
  async exportSvg(options: SvgExportOptions): Promise<Blob> {
    await document.fonts.ready;
    const viewport = diagramViewport();
    const bounds = boundsForViewport(viewport, options.margin);
    const width = Math.max(1, bounds.width * options.scale);
    const height = Math.max(1, bounds.height * options.scale);
    const background = options.background === 'white' ? '<rect width="100%" height="100%" fill="white"/>' : '';
    const body = renderSvgGraph(viewport, bounds, options);
    const legend = options.includeLegend ? `<text x="16" y="${height - 16}" font-family="Inter, Arial" font-size="12" fill="#475569">YaC4e C4 diagram export</text>` : '';
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}"><defs><marker id="arrow" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M 0 0 L 10 5 L 0 10 z" fill="#475569"/></marker></defs>${background}${body}${legend}</svg>`;
    return new Blob([svg], { type: 'image/svg+xml;charset=utf-8' });
  }

  async exportPng(options: PngExportOptions): Promise<Blob> {
    await document.fonts.ready;
    const viewport = diagramViewport();
    const bounds = boundsForViewport(viewport, options.margin);
    const dataUrl = await toPng(viewport, {
      cacheBust: true,
      pixelRatio: Math.max(2, options.pixelRatio),
      backgroundColor: options.background === 'white' ? '#ffffff' : undefined,
      width: bounds.width,
      height: bounds.height,
      style: {
        transform: `translate(${-bounds.x}px, ${-bounds.y}px) scale(${options.scale})`,
        transformOrigin: 'top left'
      }
    });
    const response = await fetch(dataUrl);
    return response.blob();
  }
}

export const diagramExportService: DiagramExportService = new DomDiagramExportService();

export function downloadBlob(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = fileName;
  anchor.click();
  URL.revokeObjectURL(url);
}

export function boundsForViewport(viewport: HTMLElement, margin: number) {
  const nodes = Array.from(viewport.querySelectorAll<HTMLElement>('.react-flow__node:not([data-hidden="true"])'));
  if (nodes.length === 0) {
    return { x: 0, y: 0, width: viewport.clientWidth || 800, height: viewport.clientHeight || 600 };
  }
  const viewportRect = viewport.getBoundingClientRect();
  const rects = nodes.map((node) => node.getBoundingClientRect());
  const minX = Math.min(...rects.map((rect) => rect.left)) - viewportRect.left - margin;
  const minY = Math.min(...rects.map((rect) => rect.top)) - viewportRect.top - margin;
  const maxX = Math.max(...rects.map((rect) => rect.right)) - viewportRect.left + margin;
  const maxY = Math.max(...rects.map((rect) => rect.bottom)) - viewportRect.top + margin;
  return { x: minX, y: minY, width: maxX - minX, height: maxY - minY };
}

export function renderSvgGraph(viewport: HTMLElement, bounds: ReturnType<typeof boundsForViewport>, options: SvgExportOptions) {
  const nodes = Array.from(viewport.querySelectorAll<HTMLElement>('.react-flow__node:not([data-hidden="true"]) [data-c4-node="true"]'));
  const nodeExports = nodes.map((node) => renderSvgNode(node, viewport, bounds, options)).join('');
  const edgeExports = renderSvgEdges(viewport, bounds, options);
  return `<g>${edgeExports}${nodeExports}</g>`;
}

function renderSvgNode(node: HTMLElement, viewport: HTMLElement, bounds: ReturnType<typeof boundsForViewport>, options: SvgExportOptions) {
  const viewportRect = viewport.getBoundingClientRect();
  const rect = node.getBoundingClientRect();
  const x = (rect.left - viewportRect.left - bounds.x) * options.scale;
  const y = (rect.top - viewportRect.top - bounds.y) * options.scale;
  const width = rect.width * options.scale;
  const height = rect.height * options.scale;
  const type = node.dataset.elementType ?? '';
  const name = node.dataset.elementName ?? '';
  const description = node.dataset.elementDescription ?? '';
  const technology = node.dataset.elementTechnology ?? '';
  const owner = node.dataset.elementOwner ?? '';
  const status = node.dataset.elementStatus ?? '';
  const warningCount = node.dataset.warningCount ?? '0';
  const linkCount = node.dataset.linkCount ?? '0';
  const border = borderColorFor(type);
  const titleY = y + 46 * options.scale;
  const descriptionLines = options.includeDescription ? wrapText(description || 'No description', Math.max(12, width - 24 * options.scale), 12 * options.scale).slice(0, 3) : [];
  const badgeText = [
    options.includeOwners ? owner : undefined,
    status,
    Number(warningCount) > 0 ? `${warningCount} warning${warningCount === '1' ? '' : 's'}` : undefined,
    Number(linkCount) > 0 ? `${linkCount} link${linkCount === '1' ? '' : 's'}` : undefined
  ].filter(Boolean).join('   ');
  const metadataBadge = options.includeOwners || options.includeTechnology ? escapeXml(badgeText) : escapeXml(status);
  const descriptionSvg = descriptionLines
    .map((line, index) => `<text x="${x + 12 * options.scale}" y="${titleY + (options.includeTechnology && technology ? 38 : 20) * options.scale + index * 15 * options.scale}" font-family="Inter, Arial" font-size="${12 * options.scale}" fill="#475467">${escapeXml(line)}</text>`)
    .join('');
  const techSvg = options.includeTechnology && technology ? `<text x="${x + 12 * options.scale}" y="${titleY + 18 * options.scale}" font-family="Inter, Arial" font-size="${12 * options.scale}" font-weight="700" fill="#0f5e8c">${escapeXml(technology)}</text>` : '';
  const badgeSvg = metadataBadge ? `<text x="${x + 12 * options.scale}" y="${y + height - 14 * options.scale}" font-family="Inter, Arial" font-size="${11 * options.scale}" fill="#334155">${metadataBadge}</text>` : '';
  return `<g data-export-element-id="${escapeXml(node.dataset.elementId ?? '')}"><rect x="${x}" y="${y}" width="${width}" height="${height}" rx="${7 * options.scale}" fill="#ffffff" stroke="${border}" stroke-width="${2 * options.scale}"/><text x="${x + 12 * options.scale}" y="${y + 22 * options.scale}" font-family="Inter, Arial" font-size="${11 * options.scale}" font-weight="700" fill="#667085">${escapeXml(stereotypeFor(type))}</text><text x="${x + 12 * options.scale}" y="${titleY}" font-family="Inter, Arial" font-size="${16 * options.scale}" font-weight="800" fill="#172033">${escapeXml(name)}</text>${techSvg}${descriptionSvg}${badgeSvg}</g>`;
}

function renderSvgEdges(viewport: HTMLElement, bounds: ReturnType<typeof boundsForViewport>, options: SvgExportOptions) {
  const nodeCenters = new Map<string, { x: number; y: number }>();
  const viewportRect = viewport.getBoundingClientRect();
  for (const node of Array.from(viewport.querySelectorAll<HTMLElement>('.react-flow__node:not([data-hidden="true"]) [data-c4-node="true"]'))) {
    const rect = node.getBoundingClientRect();
    nodeCenters.set(node.dataset.elementId ?? '', {
      x: (rect.left - viewportRect.left - bounds.x + rect.width / 2) * options.scale,
      y: (rect.top - viewportRect.top - bounds.y + rect.height / 2) * options.scale
    });
  }
  return Array.from(viewport.querySelectorAll<SVGPathElement>('path.c4-edge'))
    .map((path) => {
      const source = nodeCenters.get(path.dataset.sourceId ?? '');
      const target = nodeCenters.get(path.dataset.targetId ?? '');
      if (!source || !target) return '';
      const label = path.dataset.relationshipLabel || path.dataset.relationshipType || '';
      const midX = (source.x + target.x) / 2;
      const midY = (source.y + target.y) / 2;
      const labelSvg = options.includeRelationshipLabels && label ? `<rect x="${midX - 70 * options.scale}" y="${midY - 14 * options.scale}" width="${140 * options.scale}" height="${24 * options.scale}" rx="${5 * options.scale}" fill="#ffffff" stroke="#d7e0ec"/><text x="${midX}" y="${midY + 4 * options.scale}" text-anchor="middle" font-family="Inter, Arial" font-size="${11 * options.scale}" fill="#334155">${escapeXml(label)}</text>` : '';
      return `<g data-export-relationship-id="${escapeXml(path.dataset.edgeId ?? '')}"><line x1="${source.x}" y1="${source.y}" x2="${target.x}" y2="${target.y}" stroke="#475569" stroke-width="${2 * options.scale}" marker-end="url(#arrow)"/>${labelSvg}</g>`;
    })
    .join('');
}

function wrapText(value: string, maxWidth: number, fontSize: number) {
  const words = value.split(/\s+/).filter(Boolean);
  const lines: string[] = [];
  let current = '';
  const approximateCharWidth = fontSize * 0.55;
  for (const word of words) {
    const next = current ? `${current} ${word}` : word;
    if (next.length * approximateCharWidth > maxWidth && current) {
      lines.push(current);
      current = word;
    } else {
      current = next;
    }
  }
  if (current) lines.push(current);
  return lines;
}

function stereotypeFor(type: string) {
  return type.split('_').map((part) => part.charAt(0) + part.slice(1).toLowerCase()).join(' ');
}

function borderColorFor(type: string) {
  if (type === 'PERSON') return '#087f5b';
  if (type === 'SOFTWARE_SYSTEM' || type === 'CONTAINER') return '#0f5e8c';
  if (type === 'COMPONENT') return '#6f4dbf';
  if (type === 'DATA_STORE') return '#a05a16';
  if (type === 'EXTERNAL_SYSTEM') return '#8a4b63';
  return '#7283a0';
}

function escapeXml(value: string) {
  return value.replace(/[<>&'"]/g, (character) => {
    switch (character) {
      case '<':
        return '&lt;';
      case '>':
        return '&gt;';
      case '&':
        return '&amp;';
      case "'":
        return '&apos;';
      case '"':
        return '&quot;';
      default:
        return character;
    }
  });
}

function diagramViewport() {
  const viewport = document.querySelector<HTMLElement>('.react-flow__viewport');
  if (!viewport) {
    throw new Error('Diagram viewport is not available');
  }
  return viewport;
}
