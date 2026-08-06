import { FileInput, Upload, X } from 'lucide-react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { ModalShell } from '../../components/ModalShell';
import { api } from '../../api/client';
import { useEditorStore } from '../../stores/editorStore';
import type { ImportPreview, Workspace } from '../../types/model';

interface Props {
  open: boolean;
  onClose: () => void;
  workspaces: Workspace[];
  currentWorkspaceId: string;
}

type Format = 'CANONICAL_JSON' | 'CANONICAL_YAML' | 'STRUCTURIZR_DSL';
type Mode = 'CREATE_NEW' | 'REPLACE';

export function ImportModal({ open, onClose, workspaces, currentWorkspaceId }: Props) {
  const [format, setFormat] = useState<Format>('STRUCTURIZR_DSL');
  const [file, setFile] = useState<File>();
  const [preview, setPreview] = useState<ImportPreview>();
  const [mode, setMode] = useState<Mode>('CREATE_NEW');
  const [workspaceName, setWorkspaceName] = useState('');
  const [targetWorkspaceId, setTargetWorkspaceId] = useState(currentWorkspaceId);
  const queryClient = useQueryClient();
  const store = useEditorStore();

  const validateStructurizr = useMutation({
    mutationFn: async () => {
      if (!file) throw new Error('Select a file first');
      return api.validateStructurizr(file);
    },
    onSuccess: (result) => {
      setPreview(result);
      setWorkspaceName(result.workspace.name || file?.name.replace(/\.(dsl|zip)$/i, '') || 'Imported architecture');
    }
  });

  const importMutation = useMutation({
    mutationFn: async () => {
      if (!file) throw new Error('Select a file first');
      if (format === 'STRUCTURIZR_DSL') {
        return api.importStructurizr(file, { mode, workspaceName, targetWorkspaceId: mode === 'REPLACE' ? targetWorkspaceId : undefined });
      }
      const payload = await file.text();
      return api.importModel(payload, format === 'CANONICAL_YAML' ? 'application/yaml' : 'application/json');
    },
    onSuccess: async (result) => {
      store.setWorkspace(result.workspaceId);
      await queryClient.invalidateQueries();
      onClose();
    }
  });

  if (!open) return null;
  const canConfirm = format !== 'STRUCTURIZR_DSL' || (preview?.valid && file);

  return (
    <ModalShell className="import-modal" label="Import model" onClose={onClose} dismissible={!importMutation.isPending}>
        <header>
          <h2>Import</h2>
          <button className="icon-button" onClick={onClose} title="Close"><X size={16} /></button>
        </header>
        <label>Format<select value={format} onChange={(event) => { setFormat(event.target.value as Format); setPreview(undefined); }}>
          <option value="STRUCTURIZR_DSL">Structurizr DSL</option>
          <option value="CANONICAL_JSON">Canonical JSON</option>
          <option value="CANONICAL_YAML">Canonical YAML</option>
        </select></label>
        <label className="file-picker"><FileInput size={16} /> <span>{file?.name ?? 'Select file'}</span><input type="file" accept={accept(format)} onChange={(event) => { setFile(event.target.files?.[0]); setPreview(undefined); }} /></label>
        {format === 'STRUCTURIZR_DSL' && (
          <>
            <button type="button" className="secondary-action" onClick={() => validateStructurizr.mutate()}><Upload size={15} /> Validate and preview</button>
            {preview && <Preview preview={preview} />}
            <label>Import mode<select value={mode} onChange={(event) => setMode(event.target.value as Mode)}><option value="CREATE_NEW">Create new workspace</option><option value="REPLACE">Replace workspace</option></select></label>
            {mode === 'CREATE_NEW' && <label>Workspace name<input value={workspaceName} onChange={(event) => setWorkspaceName(event.target.value)} /></label>}
            {mode === 'REPLACE' && <label>Target workspace<select value={targetWorkspaceId} onChange={(event) => setTargetWorkspaceId(event.target.value)}>{workspaces.map((workspace) => <option key={workspace.id} value={workspace.id}>{workspace.name}</option>)}</select></label>}
          </>
        )}
        {format !== 'STRUCTURIZR_DSL' && <div className="empty-state">Canonical imports are validated transactionally on submit.</div>}
        {validateStructurizr.error && <div className="form-error">{String(validateStructurizr.error.message)}</div>}
        {importMutation.error && <div className="form-error">{String(importMutation.error.message)}</div>}
        <button type="button" className="primary-action" disabled={!file || !canConfirm} onClick={() => importMutation.mutate()}><Upload size={15} /> Confirm import</button>
    </ModalShell>
  );
}

function Preview({ preview }: { preview: ImportPreview }) {
  return (
    <div className="import-preview">
      <strong>{preview.valid ? 'Valid import' : 'Invalid import'}</strong>
      <div className="preview-counts">
        <span>People {preview.summary.people}</span>
        <span>Systems {preview.summary.softwareSystems}</span>
        <span>Containers {preview.summary.containers}</span>
        <span>Components {preview.summary.components}</span>
        <span>Relationships {preview.summary.relationships}</span>
        <span>Views {preview.summary.views}</span>
      </div>
      {preview.warnings.map((warning) => <div className="preview-warning" key={`${warning.code}-${warning.message}`}>{warning.code}: {warning.message}</div>)}
      {preview.errors.map((error) => <div className="preview-error" key={`${error.code}-${error.message}`}>{error.fileName}:{error.line ?? '-'} {error.code}: {error.message}</div>)}
    </div>
  );
}

function accept(format: Format) {
  if (format === 'STRUCTURIZR_DSL') return '.dsl,.zip';
  if (format === 'CANONICAL_YAML') return '.yaml,.yml,application/yaml';
  return '.json,application/json';
}
