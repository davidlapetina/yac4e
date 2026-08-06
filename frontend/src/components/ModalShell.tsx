import { useEffect, useRef, type ReactNode } from 'react';

interface Props {
  /** Class of the dialog surface, e.g. "export-modal". The backdrop is supplied by the shell. */
  className: string;
  /** Accessible name for the dialog, announced when it opens. */
  label: string;
  onClose: () => void;
  /**
   * Set to false while work is in flight so Escape or a stray backdrop click cannot close the
   * dialog mid-operation.
   */
  dismissible?: boolean;
  children: ReactNode;
}

/**
 * Shared dialog behaviour: Escape to close, click the backdrop to close, dialog semantics for
 * screen readers, and focus moved into the dialog on open and restored to the trigger on close.
 */
export function ModalShell({ className, label, onClose, dismissible = true, children }: Props) {
  const surfaceRef = useRef<HTMLElement>(null);
  const pressedBackdrop = useRef(false);

  useEffect(() => {
    const previouslyFocused = document.activeElement as HTMLElement | null;
    surfaceRef.current?.focus();
    return () => {
      if (previouslyFocused && typeof previouslyFocused.focus === 'function' && previouslyFocused.isConnected) {
        previouslyFocused.focus();
      }
    };
  }, []);

  useEffect(() => {
    if (!dismissible) return undefined;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return;
      // Stop the canvas shortcut handler from also reacting to this key press.
      event.stopPropagation();
      onClose();
    };
    document.addEventListener('keydown', onKeyDown, true);
    return () => document.removeEventListener('keydown', onKeyDown, true);
  }, [dismissible, onClose]);

  return (
    <div
      className="modal-backdrop"
      // Require press and release on the backdrop itself, so selecting text inside the dialog and
      // releasing outside it does not dismiss the dialog.
      onMouseDown={(event) => {
        pressedBackdrop.current = event.target === event.currentTarget;
      }}
      onMouseUp={(event) => {
        const onBackdrop = pressedBackdrop.current && event.target === event.currentTarget;
        pressedBackdrop.current = false;
        if (onBackdrop && dismissible) onClose();
      }}
    >
      <section ref={surfaceRef} className={className} role="dialog" aria-modal="true" aria-label={label} tabIndex={-1}>
        {children}
      </section>
    </div>
  );
}
