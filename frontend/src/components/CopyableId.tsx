import { Check, Copy } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { copyText } from '../utils/clipboard';

interface Props {
  value: string;
  /** What is being copied, e.g. "workspace ID". Used for the tooltip and accessible name. */
  label: string;
  /** Render the identifier next to the button rather than only in the tooltip. */
  showValue?: boolean;
}

/**
 * A UUID with a copy button, for handing identifiers to an agent. Falls back to a selected,
 * read-only field when the clipboard is unavailable, which is the normal case over plain HTTP.
 */
export function CopyableId({ value, label, showValue = false }: Props) {
  const [state, setState] = useState<'idle' | 'copied' | 'manual'>('idle');
  const manualRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (state !== 'copied') return undefined;
    const timer = setTimeout(() => setState('idle'), 1600);
    return () => clearTimeout(timer);
  }, [state]);

  useEffect(() => {
    if (state === 'manual') manualRef.current?.select();
  }, [state]);

  // A new selection should not keep showing the previous one's copy confirmation.
  useEffect(() => setState('idle'), [value]);

  return (
    <span className="copyable-id">
      {showValue && <code className="copyable-id-value" title={value}>{value}</code>}
      <button
        type="button"
        className="icon-button"
        title={state === 'copied' ? `Copied ${label}` : `Copy ${label}: ${value}`}
        aria-label={`Copy ${label}`}
        onClick={async () => setState((await copyText(value)) ? 'copied' : 'manual')}
      >
        {state === 'copied' ? <Check size={13} /> : <Copy size={13} />}
      </button>
      {state === 'manual' && (
        <input
          ref={manualRef}
          className="copyable-id-manual"
          readOnly
          value={value}
          aria-label={`${label}, select and copy manually`}
          onBlur={() => setState('idle')}
          onKeyDown={(event) => {
            if (event.key === 'Escape') {
              event.stopPropagation();
              setState('idle');
            }
          }}
        />
      )}
    </span>
  );
}
