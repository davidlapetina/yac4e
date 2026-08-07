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
  /** Builds the complete diagram once, so SVG and PNG can never disagree. */
  private render(options: SvgExportOptions) {
    const viewport = diagramViewport();
    const bounds = boundsForViewport(viewport, options.margin);
    const width = Math.max(1, bounds.width * options.scale);
    const height = Math.max(1, bounds.height * options.scale);
    const background = options.background === 'white' ? '<rect width="100%" height="100%" fill="white"/>' : '';
    const body = renderSvgGraph(viewport, bounds, options);
    const legend = options.includeLegend ? `<text x="16" y="${height - 16}" font-family="Inter, Arial" font-size="12" fill="#475569">YaC4e C4 diagram export</text>` : '';
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}"><defs><marker id="arrow" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M 0 0 L 10 5 L 0 10 z" fill="#475569"/></marker></defs>${background}${body}${legend}</svg>`;
    return { svg, width, height };
  }

  async exportSvg(options: SvgExportOptions): Promise<Blob> {
    await fontsReady();
    const { svg } = this.render(options);
    return new Blob([svg], { type: 'image/svg+xml;charset=utf-8' });
  }

  async exportPng(options: PngExportOptions): Promise<Blob> {
    await fontsReady();
    // Rasterise the exported SVG rather than screenshotting the live DOM. Screenshotting had to
    // override the viewport's own pan/zoom transform, which cropped everything outside the
    // visible area, so the PNG never matched the SVG or the full diagram.
    const { svg, width, height } = this.render(options);
    return rasterize(svg, width, height, Math.max(1, options.pixelRatio), options.background === 'white' ? '#ffffff' : undefined);
  }
}

async function fontsReady() {
  try {
    await document.fonts?.ready;
  } catch {
    // Font loading status is unavailable in some environments; exporting can still proceed.
  }
}

function rasterize(svg: string, width: number, height: number, pixelRatio: number, background?: string): Promise<Blob> {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(new Blob([svg], { type: 'image/svg+xml;charset=utf-8' }));
    const image = new Image();
    image.onload = () => {
      try {
        const canvas = document.createElement('canvas');
        canvas.width = Math.max(1, Math.round(width * pixelRatio));
        canvas.height = Math.max(1, Math.round(height * pixelRatio));
        const context = canvas.getContext('2d');
        if (!context) {
          reject(new Error('Could not create a canvas to render the PNG'));
          return;
        }
        if (background) {
          context.fillStyle = background;
          context.fillRect(0, 0, canvas.width, canvas.height);
        }
        context.drawImage(image, 0, 0, canvas.width, canvas.height);
        canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error('Could not encode the PNG'))), 'image/png');
      } finally {
        URL.revokeObjectURL(url);
      }
    };
    image.onerror = () => {
      URL.revokeObjectURL(url);
      reject(new Error('Could not render the diagram to an image'));
    };
    image.src = url;
  });
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

/**
 * Current zoom of the React Flow viewport.
 *
 * Every measurement below comes from getBoundingClientRect, which reports screen pixels after the
 * viewport's scale transform. Dividing by the zoom converts back to diagram coordinates so an
 * export is identical no matter how far the user happened to be zoomed in or out.
 */
export function viewportZoom(viewport: HTMLElement) {
  const transform = typeof getComputedStyle === 'function' ? getComputedStyle(viewport).transform : '';
  const match = /matrix3d\(([^)]+)\)|matrix\(([^)]+)\)/.exec(transform ?? '');
  if (!match) return 1;
  const scale = Number((match[1] ?? match[2]).split(',')[0]);
  return Number.isFinite(scale) && scale > 0 ? scale : 1;
}

/** Rect of an element in diagram coordinates, relative to the viewport origin. */
function flowRect(element: HTMLElement, origin: DOMRect, zoom: number) {
  const rect = element.getBoundingClientRect();
  return {
    x: (rect.left - origin.left) / zoom,
    y: (rect.top - origin.top) / zoom,
    width: rect.width / zoom,
    height: rect.height / zoom
  };
}

export function boundsForViewport(viewport: HTMLElement, margin: number) {
  const nodes = Array.from(viewport.querySelectorAll<HTMLElement>('.react-flow__node:not([data-hidden="true"])'));
  if (nodes.length === 0) {
    return { x: 0, y: 0, width: viewport.clientWidth || 800, height: viewport.clientHeight || 600 };
  }
  const origin = viewport.getBoundingClientRect();
  const zoom = viewportZoom(viewport);
  const rects = nodes.map((node) => flowRect(node, origin, zoom));
  const minX = Math.min(...rects.map((rect) => rect.x)) - margin;
  const minY = Math.min(...rects.map((rect) => rect.y)) - margin;
  const maxX = Math.max(...rects.map((rect) => rect.x + rect.width)) + margin;
  const maxY = Math.max(...rects.map((rect) => rect.y + rect.height)) + margin;
  return { x: minX, y: minY, width: maxX - minX, height: maxY - minY };
}

export function renderSvgGraph(viewport: HTMLElement, bounds: ReturnType<typeof boundsForViewport>, options: SvgExportOptions) {
  const nodes = Array.from(viewport.querySelectorAll<HTMLElement>('.react-flow__node:not([data-hidden="true"]) [data-c4-node="true"]'));
  const nodeExports = nodes.map((node) => renderSvgNode(node, viewport, bounds, options)).join('');
  const edgeExports = renderSvgEdges(viewport, bounds, options);
  return `<g>${edgeExports}${nodeExports}</g>`;
}

function renderSvgNode(node: HTMLElement, viewport: HTMLElement, bounds: ReturnType<typeof boundsForViewport>, options: SvgExportOptions) {
  const origin = viewport.getBoundingClientRect();
  const rect = flowRect(node, origin, viewportZoom(viewport));
  const x = (rect.x - bounds.x) * options.scale;
  const y = (rect.y - bounds.y) * options.scale;
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
  const origin = viewport.getBoundingClientRect();
  const zoom = viewportZoom(viewport);
  for (const node of Array.from(viewport.querySelectorAll<HTMLElement>('.react-flow__node:not([data-hidden="true"]) [data-c4-node="true"]'))) {
    const rect = flowRect(node, origin, zoom);
    nodeCenters.set(node.dataset.elementId ?? '', {
      x: (rect.x - bounds.x + rect.width / 2) * options.scale,
      y: (rect.y - bounds.y + rect.height / 2) * options.scale
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
      const clampedLabel = ellipsize(label, 132 * options.scale, 11 * options.scale);
      const labelSvg = options.includeRelationshipLabels && label ? `<rect x="${midX - 70 * options.scale}" y="${midY - 14 * options.scale}" width="${140 * options.scale}" height="${24 * options.scale}" rx="${5 * options.scale}" fill="#ffffff" stroke="#d7e0ec"/><text x="${midX}" y="${midY + 4 * options.scale}" text-anchor="middle" font-family="Inter, Arial" font-size="${11 * options.scale}" fill="#334155"><title>${escapeXml(label)}</title>${escapeXml(clampedLabel)}</text>` : '';
      return `<g data-export-relationship-id="${escapeXml(path.dataset.edgeId ?? '')}"><line x1="${source.x}" y1="${source.y}" x2="${target.x}" y2="${target.y}" stroke="#475569" stroke-width="${2 * options.scale}" marker-end="url(#arrow)"/>${labelSvg}</g>`;
    })
    .join('');
}

/**
 * Clamps a single-line label to the width it is drawn into. The relationship label box is a fixed
 * size, so an unclamped description spilled out of it and across neighbouring shapes.
 */
export function ellipsize(value: string, maxWidth: number, fontSize: number) {
  const approximateCharWidth = fontSize * 0.55;
  const maxChars = Math.max(1, Math.floor(maxWidth / approximateCharWidth));
  if (value.length <= maxChars) return value;
  if (maxChars === 1) return '…';
  return `${value.slice(0, maxChars - 1).trimEnd()}…`;
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
