import { LucideIcon } from "lucide-react";
import { formatNumber } from "@/lib/utils";

export function StatCard({ label, value, change, icon: Icon }: {
  label: string;
  value: number;
  change: string;
  icon: LucideIcon;
}) {
  return (
    <div className="panel-sq-dense p-3 flex flex-col justify-between rounded-none">
      <div className="flex items-center justify-between mb-1.5">
        <span className="text-[10px] font-semibold tracking-[0.1em] uppercase text-[var(--color-fg-muted)]">
          {label}
        </span>
        <Icon className="h-3 w-3 text-[var(--color-fg-muted)] shrink-0" />
      </div>
      <p className="text-xl font-bold tabular-nums leading-none tracking-tight text-[var(--color-fg-primary)]">{formatNumber(value)}</p>
      <p className="text-[10px] text-[var(--color-fg-muted)] mt-auto pt-0.5 truncate" title={change}>{change}</p>
    </div>
  );
}
