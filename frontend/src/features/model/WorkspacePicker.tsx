import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Check, Pencil, X } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { ApiClientError, api } from '../../api/client';
import { CopyableId } from '../../components/CopyableId';
import type { Workspace } from '../../types/model';

interface Props {
  workspaces: Workspace[];
  workspaceId: string;
  onSelect: (workspaceId: string) => void;
}

export function WorkspacePicker({ workspaces, workspaceId, onSelect }: Props) {
  const queryClient = useQueryClient();
  const current = workspaces.find((workspace) => workspace.id === workspaceId);
  const [renaming, setRenaming] = useState(false);
  const [draft, setDraft] = useState('');
  const [error, setError] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (renaming) inputRef.current?.select();
  }, [renaming]);

  const rename = useMutation({
    mutationFn: (name: string) =>
      api.updateWorkspace(workspaceId, {
        name,
        // Preserve the description: the API replaces it on every update, so omitting it would clear it.
        description: current?.description ?? '',
        version: current?.version
      }),
    onSuccess: async () => {
      setRenaming(false);
      setError(null);
      await queryClient.invalidateQueries({ queryKey: ['workspaces'] });
    },
    onError: async (cause: unknown) => {
      if (cause instanceof ApiClientError && cause.status === 409) {
        setError('This workspace changed elsewhere. Reloading the latest name.');
        await queryClient.invalidateQueries({ queryKey: ['workspaces'] });
        return;
      }
      setError(cause instanceof Error ? cause.message : 'Rename failed');
    }
  });

  function startRename() {
    setDraft(current?.name ?? '');
    setError(null);
    setRenaming(true);
  }

  function cancelRename() {
    setRenaming(false);
    setError(null);
  }

  function submitRename() {
    const name = draft.trim();
    if (!name || name === current?.name) {
      cancelRename();
      return;
    }
    rename.mutate(name);
  }

  if (!renaming) {
    return (
      <div className="workspace-picker">
        <select aria-label="Workspace" value={workspaceId} onChange={(event) => onSelect(event.target.value)}>
          {workspaces.map((workspace) => <option key={workspace.id} value={workspace.id}>{workspace.name}</option>)}
        </select>
        <button type="button" className="icon-action" onClick={startRename} title="Rename workspace" aria-label="Rename workspace">
          <Pencil size={14} />
        </button>
        <CopyableId value={workspaceId} label="workspace ID" />
      </div>
    );
  }

  return (
    <div className="workspace-picker">
      <input
        ref={inputRef}
        aria-label="Workspace name"
        value={draft}
        autoFocus
        disabled={rename.isPending}
        onChange={(event) => setDraft(event.target.value)}
        onKeyDown={(event) => {
          if (event.key === 'Enter') {
            event.preventDefault();
            submitRename();
          }
          if (event.key === 'Escape') {
            event.preventDefault();
            cancelRename();
          }
        }}
      />
      <button type="button" className="icon-action" onClick={submitRename} disabled={rename.isPending} title="Save name" aria-label="Save workspace name">
        <Check size={14} />
      </button>
      <button type="button" className="icon-action" onClick={cancelRename} disabled={rename.isPending} title="Cancel rename" aria-label="Cancel rename">
        <X size={14} />
      </button>
      {error && <span className="workspace-rename-error" role="alert">{error}</span>}
    </div>
  );
}
