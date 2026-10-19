import clsx from 'clsx';

const STATUS_CONFIG = {
  TRIGGERED: {
    label: 'Triggered',
    dotColor: 'bg-rose-500',
    textColor: 'text-rose-400',
    bgColor: 'bg-rose-500/10',
    ringColor: 'ring-rose-500/20',
    pulse: true,
  },
  ACKNOWLEDGED: {
    label: 'Acknowledged',
    dotColor: 'bg-amber-500',
    textColor: 'text-amber-400',
    bgColor: 'bg-amber-500/10',
    ringColor: 'ring-amber-500/20',
  },
  INVESTIGATING: {
    label: 'Investigating',
    dotColor: 'bg-blue-500',
    textColor: 'text-blue-400',
    bgColor: 'bg-blue-500/10',
    ringColor: 'ring-blue-500/20',
  },
  RESOLVED: {
    label: 'Resolved',
    dotColor: 'bg-emerald-500',
    textColor: 'text-emerald-400',
    bgColor: 'bg-emerald-500/10',
    ringColor: 'ring-emerald-500/20',
  },
  POSTMORTEM: {
    label: 'Postmortem',
    dotColor: 'bg-zinc-400',
    textColor: 'text-zinc-400',
    bgColor: 'bg-zinc-500/10',
    ringColor: 'ring-zinc-500/20',
  },
};

export default function StatusBadge({ status }) {
  const config = STATUS_CONFIG[status] || STATUS_CONFIG.POSTMORTEM;

  return (
    <span
      className={clsx(
        'inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-semibold ring-1',
        config.bgColor,
        config.textColor,
        config.ringColor
      )}
    >
      <span
        className={clsx(
          'w-1.5 h-1.5 rounded-full flex-shrink-0',
          config.dotColor,
          config.pulse && 'animate-pulse-dot'
        )}
      />
      {config.label}
    </span>
  );
}
