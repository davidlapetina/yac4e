import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CopyableId } from '../../src/components/CopyableId';
import { copyText } from '../../src/utils/clipboard';

const UUID = '274c4e57-e82b-4f6e-95c0-77a55872cf19';

function setClipboard(value: unknown) {
  Object.defineProperty(navigator, 'clipboard', { value, configurable: true, writable: true });
}

afterEach(() => {
  vi.restoreAllMocks();
  setClipboard(undefined);
});

describe('copyText', () => {
  it('uses the async clipboard when it is available', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    setClipboard({ writeText });

    await expect(copyText(UUID)).resolves.toBe(true);
    expect(writeText).toHaveBeenCalledWith(UUID);
  });

  it('falls back to a selection copy when the clipboard API is absent, as over plain HTTP', async () => {
    setClipboard(undefined);
    const exec = vi.fn().mockReturnValue(true);
    Object.defineProperty(document, 'execCommand', { value: exec, configurable: true });

    await expect(copyText(UUID)).resolves.toBe(true);
    expect(exec).toHaveBeenCalledWith('copy');
    // The temporary textarea must not be left behind.
    expect(document.querySelectorAll('textarea')).toHaveLength(0);
  });

  it('falls back when the clipboard API rejects', async () => {
    setClipboard({ writeText: vi.fn().mockRejectedValue(new Error('denied')) });
    const exec = vi.fn().mockReturnValue(true);
    Object.defineProperty(document, 'execCommand', { value: exec, configurable: true });

    await expect(copyText(UUID)).resolves.toBe(true);
    expect(exec).toHaveBeenCalled();
  });

  it('reports failure when neither path works', async () => {
    setClipboard(undefined);
    Object.defineProperty(document, 'execCommand', { value: vi.fn().mockReturnValue(false), configurable: true });

    await expect(copyText(UUID)).resolves.toBe(false);
  });
});

describe('CopyableId', () => {
  beforeEach(() => {
    Object.defineProperty(document, 'execCommand', { value: vi.fn().mockReturnValue(true), configurable: true });
  });

  it('puts the identifier in the button tooltip so it is readable without copying', () => {
    setClipboard({ writeText: vi.fn().mockResolvedValue(undefined) });
    render(<CopyableId value={UUID} label="workspace ID" />);

    expect(screen.getByLabelText('Copy workspace ID')).toHaveAttribute('title', `Copy workspace ID: ${UUID}`);
  });

  it('optionally renders the identifier as selectable text', () => {
    render(<CopyableId value={UUID} label="element ID" showValue />);
    expect(screen.getByText(UUID)).toBeInTheDocument();
  });

  it('confirms after a successful copy', async () => {
    setClipboard({ writeText: vi.fn().mockResolvedValue(undefined) });
    render(<CopyableId value={UUID} label="workspace ID" />);

    fireEvent.click(screen.getByLabelText('Copy workspace ID'));

    await waitFor(() => expect(screen.getByLabelText('Copy workspace ID')).toHaveAttribute('title', 'Copied workspace ID'));
    expect(screen.queryByLabelText(/select and copy manually/)).not.toBeInTheDocument();
  });

  it('offers the value for manual selection when copying is not possible', async () => {
    setClipboard(undefined);
    Object.defineProperty(document, 'execCommand', { value: vi.fn().mockReturnValue(false), configurable: true });
    render(<CopyableId value={UUID} label="workspace ID" />);

    fireEvent.click(screen.getByLabelText('Copy workspace ID'));

    const manual = await screen.findByLabelText('workspace ID, select and copy manually');
    expect(manual).toHaveValue(UUID);
    expect(manual).toHaveAttribute('readonly');
  });

  it('dismisses the manual field on Escape', async () => {
    setClipboard(undefined);
    Object.defineProperty(document, 'execCommand', { value: vi.fn().mockReturnValue(false), configurable: true });
    render(<CopyableId value={UUID} label="workspace ID" />);

    fireEvent.click(screen.getByLabelText('Copy workspace ID'));
    const manual = await screen.findByLabelText('workspace ID, select and copy manually');
    fireEvent.keyDown(manual, { key: 'Escape' });

    await waitFor(() => expect(screen.queryByLabelText('workspace ID, select and copy manually')).not.toBeInTheDocument());
  });
});
