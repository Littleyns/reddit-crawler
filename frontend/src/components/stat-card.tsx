import { LucideIcon, TrendingUp } from "lucide-react";
import { formatNumber } from "@/lib/utils";

export function StatCard({
  label,
  value,
  change,
  icon: Icon,
}: {
  label: string;
  value: number;
  change: string;
  icon: LucideIcon;
}) {
  return (
    <article className="metric-glow panel rounded-[26px] border-white/45 p-5">
      <div className="flex items-start justify-between gap-4">
        <div>
          <p className="text-xs font-medium uppercase tracking-[0.28em] text-[var(--color-muted)]">
            {label}
          </p>
          <p className="mt-4 text-4xl font-semibold">{formatNumber(value)}</p>
        </div>
        <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-[var(--color-accent-soft)] text-[var(--color-accent-strong)]">
          <Icon className="h-5 w-5" />
        </div>
      </div>
      <div className="mt-6 flex items-center gap-2 text-sm text-[var(--color-muted)]">
        <TrendingUp className="h-4 w-4 text-[var(--color-accent)]" />
        <span>{change}</span>
      </div>
    </article>
  );
}
