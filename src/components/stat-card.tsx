import { LucideIcon, TrendingUp } from "lucide-react";
import { Card } from "@/components/ui/card";
import { StaggerItem } from "@/components/ui/reveal";
import { formatNumber } from "@/lib/utils";

export function StatCard({
  label,
  value,
  change,
  icon: Icon,
  index = 0,
}: {
  label: string;
  value: number;
  change: string;
  icon: LucideIcon;
  index?: number;
}) {
  return (
    <StaggerItem as="div" index={index}>
      <Card className="metric-glow rounded-[calc(var(--radius-lg)+0.125rem)] p-5" variant="default">
        <div className="flex items-start justify-between gap-4">
          <div>
            <p className="text-xs font-medium uppercase tracking-[0.28em] text-[var(--ds-text-muted)]">
              {label}
            </p>
            <p className="mt-4 text-4xl font-semibold">{formatNumber(value)}</p>
          </div>
          <div className="flex h-12 w-12 items-center justify-center rounded-[var(--radius-md)] bg-[rgba(103,179,255,0.14)] text-[var(--ds-primary-500)]">
            <Icon className="h-5 w-5" />
          </div>
        </div>
        <div className="mt-6 flex items-center gap-2 text-sm text-[var(--ds-text-secondary)]">
          <TrendingUp className="h-4 w-4 text-[var(--ds-success-500)]" />
          <span>{change}</span>
        </div>
      </Card>
    </StaggerItem>
  );
}
