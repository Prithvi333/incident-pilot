import clsx from 'clsx';

export default function StatsCard({ title, value, subtitle, icon: Icon, iconColor = 'text-zinc-500', trend }) {
  return (
    <div className="group bg-zinc-900 rounded-xl border border-white/[0.06] p-5 card-glow animate-fade-in">
      <div className="flex items-start justify-between mb-3">
        <p className="text-[12px] font-medium uppercase tracking-wider text-zinc-500">
          {title}
        </p>
        {Icon && (
          <div className={clsx('p-2 rounded-lg bg-white/[0.03]', iconColor)}>
            <Icon size={16} strokeWidth={1.8} />
          </div>
        )}
      </div>
      <p className="text-3xl font-bold tracking-tight text-zinc-50">{value}</p>
      <div className="flex items-center gap-2 mt-1.5">
        {subtitle && (
          <p className="text-[12px] text-zinc-500">{subtitle}</p>
        )}
        {trend && (
          <span className={clsx(
            'text-[11px] font-medium px-1.5 py-0.5 rounded',
            trend > 0 ? 'text-rose-400 bg-rose-500/10' : 'text-emerald-400 bg-emerald-500/10'
          )}>
            {trend > 0 ? '+' : ''}{trend}%
          </span>
        )}
      </div>
    </div>
  );
}
