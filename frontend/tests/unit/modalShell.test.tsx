import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ModalShell } from '../../src/components/ModalShell';

function renderShell(props: Partial<Parameters<typeof ModalShell>[0]> = {}) {
  const onClose = vi.fn();
  const view = render(
    <ModalShell className="export-modal" label="Export diagram" onClose={onClose} {...props}>
      <button type="button">Inside</button>
    </ModalShell>
  );
  return { onClose, view };
}

describe('ModalShell', () => {
  it('exposes dialog semantics with an accessible name', () => {
    renderShell();
    const dialog = screen.getByRole('dialog');

    expect(dialog).toHaveAttribute('aria-modal', 'true');
    expect(dialog).toHaveAccessibleName('Export diagram');
  });

  it('moves focus into the dialog when it opens', () => {
    renderShell();
    expect(document.activeElement).toBe(screen.getByRole('dialog'));
  });

  it('closes on Escape', () => {
    const { onClose } = renderShell();
    fireEvent.keyDown(document, { key: 'Escape' });

    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('closes when the backdrop is pressed and released', () => {
    const { onClose } = renderShell();
    const backdrop = document.querySelector('.modal-backdrop') as HTMLElement;

    fireEvent.mouseDown(backdrop);
    fireEvent.mouseUp(backdrop);

    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('does not close when the press starts inside the dialog and ends on the backdrop', () => {
    const { onClose } = renderShell();
    const backdrop = document.querySelector('.modal-backdrop') as HTMLElement;

    fireEvent.mouseDown(screen.getByText('Inside'));
    fireEvent.mouseUp(backdrop);

    expect(onClose).not.toHaveBeenCalled();
  });

  it('does not close on a click that lands inside the dialog', () => {
    const { onClose } = renderShell();

    fireEvent.mouseDown(screen.getByText('Inside'));
    fireEvent.mouseUp(screen.getByText('Inside'));

    expect(onClose).not.toHaveBeenCalled();
  });

  it('ignores Escape and backdrop clicks while work is in flight', () => {
    const { onClose } = renderShell({ dismissible: false });
    const backdrop = document.querySelector('.modal-backdrop') as HTMLElement;

    fireEvent.keyDown(document, { key: 'Escape' });
    fireEvent.mouseDown(backdrop);
    fireEvent.mouseUp(backdrop);

    expect(onClose).not.toHaveBeenCalled();
  });

  it('restores focus to the element that opened it', () => {
    const trigger = document.createElement('button');
    document.body.appendChild(trigger);
    trigger.focus();
    expect(document.activeElement).toBe(trigger);

    const { view } = renderShell();
    expect(document.activeElement).not.toBe(trigger);

    view.unmount();
    expect(document.activeElement).toBe(trigger);
    trigger.remove();
  });
});
