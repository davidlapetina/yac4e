import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { WorkspacePicker } from '../../src/features/model/WorkspacePicker';
import { ApiClientError, api } from '../../src/api/client';
import type { Workspace } from '../../src/types/model';

const workspace: Workspace = {
  id: 'ws-1',
  name: 'Payments Platform',
  description: 'Core payments architecture',
  version: 7,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z'
};

function renderPicker(workspaces: Workspace[] = [workspace]) {
  const client = new QueryClient({ defaultOptions: { mutations: { retry: false }, queries: { retry: false } } });
  const onSelect = vi.fn();
  render(
    <QueryClientProvider client={client}>
      <WorkspacePicker workspaces={workspaces} workspaceId="ws-1" onSelect={onSelect} />
    </QueryClientProvider>
  );
  return { onSelect };
}

function startRename() {
  fireEvent.click(screen.getByLabelText('Rename workspace'));
  return screen.getByLabelText('Workspace name');
}

function typeName(input: HTMLElement, value: string) {
  fireEvent.change(input, { target: { value } });
}

beforeEach(() => {
  vi.spyOn(api, 'updateWorkspace').mockResolvedValue({ ...workspace, name: 'Renamed', version: 8 });
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('WorkspacePicker rename', () => {
  it('shows the selector until rename starts, then edits the current name', () => {
    renderPicker();
    expect(screen.getByLabelText('Workspace')).toBeInTheDocument();

    const input = startRename();

    expect(input).toHaveValue('Payments Platform');
    expect(screen.queryByLabelText('Workspace')).not.toBeInTheDocument();
  });

  it('sends the new name with the existing description and version', async () => {
    renderPicker();
    typeName(startRename(), 'Renamed');
    fireEvent.click(screen.getByLabelText('Save workspace name'));

    await waitFor(() => expect(api.updateWorkspace).toHaveBeenCalledWith('ws-1', {
      name: 'Renamed',
      description: 'Core payments architecture',
      version: 7
    }));
  });

  it('saves on Enter and returns to the selector', async () => {
    renderPicker();
    const input = startRename();
    typeName(input, 'Via Enter');
    fireEvent.keyDown(input, { key: 'Enter' });

    await waitFor(() => expect(api.updateWorkspace).toHaveBeenCalledWith('ws-1', expect.objectContaining({ name: 'Via Enter' })));
    await waitFor(() => expect(screen.getByLabelText('Workspace')).toBeInTheDocument());
  });

  it('discards the edit on Escape without calling the API', () => {
    renderPicker();
    const input = startRename();
    typeName(input, 'Abandoned');
    fireEvent.keyDown(input, { key: 'Escape' });

    expect(api.updateWorkspace).not.toHaveBeenCalled();
    expect(screen.getByLabelText('Workspace')).toBeInTheDocument();
  });

  it('does not call the API for a blank or unchanged name', () => {
    renderPicker();

    typeName(startRename(), '   ');
    fireEvent.click(screen.getByLabelText('Save workspace name'));
    expect(api.updateWorkspace).not.toHaveBeenCalled();

    startRename();
    fireEvent.click(screen.getByLabelText('Save workspace name'));
    expect(api.updateWorkspace).not.toHaveBeenCalled();
  });

  it('trims surrounding whitespace before sending', async () => {
    renderPicker();
    typeName(startRename(), '  Padded Name  ');
    fireEvent.click(screen.getByLabelText('Save workspace name'));

    await waitFor(() => expect(api.updateWorkspace).toHaveBeenCalledWith('ws-1', expect.objectContaining({ name: 'Padded Name' })));
  });

  it('surfaces a stale-version conflict instead of failing silently', async () => {
    vi.spyOn(api, 'updateWorkspace').mockRejectedValue(new ApiClientError(
      { code: 'STALE_VERSION', message: 'The submitted version is stale', details: {}, timestamp: '2026-01-01T00:00:00Z' },
      409
    ));
    renderPicker();
    typeName(startRename(), 'Conflicting');
    fireEvent.click(screen.getByLabelText('Save workspace name'));

    expect(await screen.findByRole('alert')).toHaveTextContent(/changed elsewhere/i);
  });
});
