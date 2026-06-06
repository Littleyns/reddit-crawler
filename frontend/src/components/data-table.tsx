"use client";

import { ArrowDownUp, ChevronLeft, ChevronRight } from "lucide-react";
import { useMemo, useState } from "react";
import { cn } from "@/lib/utils";

interface Column<T = Record<string, any>> {
  key: string;
  label: string;
  className?: string;
  render?: (row: T) => React.ReactNode;
}

export function DataTable<T extends { id: string | number }>({
  columns, rows, page, totalPages, onPageChange,
}: {
  columns: readonly Column<T>[]; rows: T[];
  page: number; totalPages: number;
  onPageChange: (p: number) => void;
}) {
  const [sortKey, setSortKey] = useState<keyof T | null>(null);
  const [sortDir, setSortDir] = useState<"asc"|"desc">("desc");

  const sortedRows = useMemo(() => {
    if (!sortKey) return rows;
    const sorted = [...rows].sort((a,b) => {
      const vA = String(a[sortKey] ?? "");
      const vB = String(b[sortKey] ?? "");
      return sortDir === "asc" ? vA.localeCompare(vB, undefined, {numeric:true}) : vB.localeCompare(vA, undefined, {numeric:true});
    });
    return sorted;
  }, [rows, sortKey, sortDir]);

  const toggleSort = (col: Column<T>) => {
    if (sortKey === col.key) setSortDir(d => d === "asc" ? "desc" : "asc");
    else { setSortKey(col.key as keyof T); setSortDir("desc"); }
  };

  return (
    <div className="w-full min-w-0 flex-1 rounded-none overflow-hidden border border-[var(--color-border)] bg-[var(--color-surface-mid)]">
      <table className="w-full h-full table-auto text-left border-collapse">
        <thead>
          <tr>
            {columns.map((col) => (
              <th key={String(col.key)} className={cn("px-3 py-2", col.className)}>
                <button type="button" onClick={() => toggleSort(col)} className="inline-flex items-center gap-1.5 text-[10px] font-semibold uppercase tracking-[0.06em] text-[var(--color-fg-muted)] hover:text-[var(--color-fg-secondary)] transition-colors">
                  {col.label}
                  <ArrowDownUp className="h-3 w-3 opacity-40" />
                </button>
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {sortedRows.length === 0 && (
            <tr>
              <td colSpan={columns.length} className="text-center py-8 text-[var(--color-fg-muted)] text-xs">No data found</td>
            </tr>
          )}
          {sortedRows.map((row) => (
            <tr key={row.id} className="[&>td]:border-b [&>td]:border-[var(--color-border)]">
              {columns.map((col) => (
                <td key={String(col.key)} className="px-3 py-1.5 text-xs text-[var(--color-fg-primary)] transition-colors hover:bg-[var(--color-accent-soft)]">
                  {col.render ? col.render(row) : String((row as any)[col.key] ?? "-")}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>

      {/* Pagination bar */}
      <div className="flex items-center justify-between border-t border-[var(--color-border)] px-3 py-1.5 bg-[var(--color-surface-low)]">
        <span className="text-[10px] text-[var(--color-fg-muted)] font-mono tabular-nums">Page {page} of {totalPages}</span>
        <div className="flex gap-1">
          <button
            type="button"
            disabled={page <= 1}
            onClick={() => onPageChange(page - 1)}
            className="btn-sq btn-sq-muted px-2.5 py-1 disabled:opacity-30 text-[10px]"
          >
            <ChevronLeft className="h-[10px] w-[10px]" /> Prev
          </button>
          <button
            type="button"
            disabled={page >= totalPages}
            onClick={() => onPageChange(page + 1)}
            className="btn-sq btn-sq-muted px-2.5 py-1 disabled:opacity-30 text-[10px]"
          >
            Next <ChevronRight className="h-[10px] w-[10px]" />
          </button>
        </div>
      </div>
    </div>
  );
}
