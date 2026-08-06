import { PanelLeftOpen, PanelRightOpen } from 'lucide-react';

interface Props {
  side: 'left' | 'right';
  label: string;
  /** Shown next to the label, e.g. the number of elements hidden behind the rail. */
  badge?: number;
  onExpand: () => void;
}

/**
 * The narrow strip left in place of a collapsed side panel. Without it a collapsed panel would
 * have no affordance to bring it back.
 */
export function PanelRail({ side, label, badge, onExpand }: Props) {
  const Icon = side === 'left' ? PanelLeftOpen : PanelRightOpen;
  return (
    <div className={`panel-rail ${side}`}>
      <button type="button" className="icon-button" onClick={onExpand} title={`Show ${label}`} aria-label={`Show ${label}`} aria-expanded={false}>
        <Icon size={16} />
      </button>
      <button type="button" className="panel-rail-label" onClick={onExpand} tabIndex={-1} aria-hidden="true">
        {label}{typeof badge === 'number' ? ` · ${badge}` : ''}
      </button>
    </div>
  );
}
