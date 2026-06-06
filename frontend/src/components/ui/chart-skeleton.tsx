import { cn } from "@/lib/utils";

/**
 * Placeholder skeleton for charts and stat cards during data fetch.
 */
export function ChartSkeleton({ className = "", ...props }: React.ComponentProps<"div">) {
  return (
    <div
      className={cn(
        "animate-pulse rounded-lg bg-surface-high border border-border",
        "h-[96px]",
        className,
      )}
      {...props}
    />
  );
}