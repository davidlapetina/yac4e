import { Download, X } from 'lucide-react';
import { useState } from 'react';
import { ModalShell } from '../../components/ModalShell';
import { diagramExportService, downloadBlob, type PngExportOptions, type SvgExportOptions } from './exportService';

interface Props {
  open: boolean;
  onClose: () => void;
  viewName: string;
}

export function ExportModal({ open, onClose, viewName }: Props) {
  const [format, setFormat] = useState<'SVG' | 'PNG'>('SVG');
  const [background, setBackground] = useState<'white' | 'transparent'>('white');
  const [resolution, setResolution] = useState(2);
  const [content, setContent] = useState({
    includeDescription: true,
    includeTechnology: true,
    includeOwners: true,
    includeRelationshipLabels: true,
    includeLegend: true
  });
  if (!open) return null;
  const baseName = viewName.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '') || 'diagram';

  async function runExport() {
    const common: SvgExportOptions = {
      fileName: `${baseName}.${format.toLowerCase()}`,
      background,
      margin: 48,
      scale: 1,
      ...content
    };
    const blob = format === 'SVG'
      ? await diagramExportService.exportSvg(common)
      : await diagramExportService.exportPng({ ...common, pixelRatio: resolution } satisfies PngExportOptions);
    downloadBlob(blob, common.fileName);
    onClose();
  }

  return (
    <ModalShell className="export-modal" label="Export diagram" onClose={onClose}>
        <header>
          <h2>Export</h2>
          <button className="icon-button" onClick={onClose} title="Close"><X size={16} /></button>
        </header>
        <div className="segmented">
          <button className={format === 'SVG' ? 'active' : ''} onClick={() => setFormat('SVG')}>SVG</button>
          <button className={format === 'PNG' ? 'active' : ''} onClick={() => setFormat('PNG')}>PNG</button>
        </div>
        <label>Scope<select defaultValue="entire"><option value="entire">Entire current view</option><option value="selected">Selected elements</option></select></label>
        <label>Appearance<select value={background} onChange={(event) => setBackground(event.target.value as 'white' | 'transparent')}><option value="white">White background</option><option value="transparent">Transparent background</option></select></label>
        <div className="checkbox-grid">
          {Object.entries(content).map(([key, value]) => (
            <label key={key} className="checkbox">
              <input type="checkbox" checked={value} onChange={(event) => setContent({ ...content, [key]: event.target.checked })} />
              {label(key)}
            </label>
          ))}
        </div>
        <label>Resolution<select value={resolution} onChange={(event) => setResolution(Number(event.target.value))}><option value={1}>1x</option><option value={2}>2x</option><option value={4}>4x</option></select></label>
        <button className="primary-action" onClick={runExport}><Download size={15} /> Export {format}</button>
    </ModalShell>
  );
}

function label(key: string) {
  return key.replace('include', '').replace(/[A-Z]/g, (part) => ` ${part}`).trim();
}
