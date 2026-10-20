import clsx from 'clsx';

const SEVERITY_CONFIG = {
  P1_CRITICAL: {
    label: 'P1',
    sublabel: 'Critical',
    color: 'text-rose-400',
    bg: 'bg-rose-500/10',
    ring: 'ring-rose-500/20',
    barColor: 'bg-rose-500',
  },
  P2_HIGH: {
    label: 'P2',
    sublabel: 'High',
    color: 'text-orange-400',
    bg: 'bg-orange-500/10',
    ring: 'ring-orange-500/20',
    barColor: 'bg-orange-500',
  },
  P3_MEDIUM: {
    label: 'P3',
    sublabel: 'Medium',
    color: 'text-amber-400',
    bg: 'bg-amber-500/10',
    ring: 'ring-amber-500/20',
    barColor: 'bg-amber-500',
  },
  P4_LOW: {
    label: 'P4',
    sublabel: 'Low',
    color: 'text-sky-400',
    bg: 'bg-sky-500/10',
    ring: 'ring-sky-500/20',
    barColor: 'bg-sky-500',
  },
};

export default function SeverityBadge({ severity, showLabel = false }) {
  const config = SEVERITY_CONFIG[severity] || SEVERITY_CONFIG.P4_LOW;

  return (
    <span
      className={clsx(
        'inline-flex items-center gap-1.5 px-2 py-0.5 rounded-md text-[11px] font-bold ring-1',
        config.bg,
        config.color,
        config.ring
      )}
    >
      <span className={clsx('w-1 h-3 rounded-full', config.barColor)} />
      {config.label}
      {showLabel && (
        <span className="font-medium opacity-70">{config.sublabel}</span>
      )}
    </span>
  );
}
