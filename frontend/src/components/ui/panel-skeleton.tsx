"use client";

interface PanelSkeletonProps {
  className?: string;
}

/**
 * Shimmer-style skeleton for a panel/card widget.
 */
export function PanelSkeleton({ className = "" }: PanelSkeletonProps) {
  return (
    <div className={`animate-pulse rounded-[24px] border border-fg-muted/10 bg-surface-mid p-6 ${className}`}>
      <div className="mb-2 h-3 w-20 rounded-full bg-fg-muted/15" />
      <div className="mb-4 h-7 w-28 rounded-lg bg-fg-muted/20" />
      <div className="mb-3 h-2 w-full rounded-full bg-fg-muted/10" />
      <div className="mb-3 h-2 w-[85%] rounded-full bg-fg-muted/10" />
      <div className="h-2 w-[70%] rounded-full bg-fg-muted/10" />
    </div>
  );
}

/**
 * Multi-panel grid skeleton for loading lists of cards.
 */
export function GridSkeleton({ columns = 3, rows = 1 }: { columns?: number; rows?: number }) {
  const count = columns * rows;
  return (
    <div className={`grid gap-4 ${columns >= 2 ? "sm:grid-cols-" + columns : ""}`}>
      {Array.from({ length: count }).map((_, i) => (
        <PanelSkeleton key={i} />
      ))}
    </div>
  );
}

/**
 * Table skeleton showing empty rows with shimmer.
 */
export function TableSkeleton({ rowCount = 5, columns = 6 }: { rowCount?: number; columns?: number }) {
  return (
    <div className="overflow-hidden rounded-lg border border-border">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-border">
            {Array.from({ length: columns }).map((_, i) => (
              <th key={i} className="animate-pulse px-4 py-3 text-left text-xs uppercase tracking-[0.28em] text-fg-muted/40">
                <div className="h-3 w-full rounded bg-fg-muted/15" />
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {Array.from({ length: rowCount }).map((_, row) => (
            <tr key={row} className="border-b border-border/40">
              {Array.from({ length: columns }).map((_, col) => (
                <td key={col} className="animate-pulse px-4 py-3">
                  <div className="h-2 w-full rounded bg-fg-muted/10" />
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export default PanelSkeleton;
