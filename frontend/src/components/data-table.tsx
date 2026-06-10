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
  onSelectAll, onDeselectAll, selectedIds, hasSelection = false,
}: {
  columns: readonly Column<T>[]; rows: T[];
  page: number; totalPages: number;
  onSelectChange?: (p: boolean) => void;
  onPageChange: (p: number) => void;
  onSelectAll?: () => void;
  onDeselectAll?: () => void;
  selectedIds?: Set<string | number>;
  hasSelection?: boolean;
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

  /* ---- helpers for row selection ---- */
  const _sids = selectedIds ?? new Set<string | number>();

  /* is a single id selected? */
  const isSelectedFn = (id: string | number): boolean =>
    hasSelection && _sids.has(id);

  /* is everything on this page selected? */
  const allSelected = rows.length > 0 && _sids.size === rows.map(r => String(r.id)).length;

  return (
      <div className="w-full min-w-0 flex-1 rounded-none overflow-hidden border border-[var(--color-border)] bg-[var(--color-surface-mid)]">

        {hasSelection ? (
          /* ── selection header ── */
          <div className="flex items-center justify-between px-3 py-1.5 border-b border-[var(--color-border)] bg-[var(--color-surface-low)]">
            <button type="button"
                    onClick={allSelected ? onDeselectAll : onSelectAll}
                    disabled={!onSelectAll && !onDeselectAll}
                    className={cn(
                      "flex items-center gap-2 text-xs text-fg-muted hover:text-fg-secondary transition-colors",
                      (_sids.size === 0 && allSelected === false) && "cursor-not-allowed opacity-40"
                    )}>
              <span className={cn(
                "w-3.5 h-3.5 border border-[var(--color-border)] rounded flex items-center justify-center bg-surface-mid",
                allSelected && "border-accent-primary bg-accent-primary/20"
              )}>
                {allSelected ? (
                  <span className="text-accent-primary text-[10px]">✓</span>
                ) : _sids.size > 0 ? (
                  <span className="text-accent-primary text-[10px]">–</span>
                ) : null}
              </span>
              <span>{_sids.size}/{rows.length} selected</span>
            </button>
          </div>
        ) : null}

        <table className="w-full h-full table-auto text-left border-collapse">
          <thead>
            <tr>
              {hasSelection ? (
                <th className="px-2 py-1.5 w-[32px]">
                  <span className="w-3.5 h-3.5 inline-block border-[var(--color-border)] text-transparent pointer-events-none"/>
                </th>
              ) : null}
              {columns.map((col) => (
                <th key={String(col.key)} className={cn("px-3 py-2", col.className)}>
                  <button type="button" onClick={() => toggleSort(col)} className="inline-flex items-center gap-1.5 text-[10px] font-semibold uppercase tracking-[.06em] text-[var(--color-fg-muted)] hover:text-[var(--color-fg-secondary)] transition-colors">
                    {col.label}
                    <ArrowDownUp className="h-3 w-3 opacity-40"/>
                  </button>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {sortedRows.length === 0 && (
              <tr>
                <td colSpan={hasSelection ? columns.length + 1 : columns.length} className="text-center py-8 text-[var(--color-fg-muted)] text-xs">No data found</td>
              </tr>
            )}
            {sortedRows.map((row) => (
              <tr key={row.id} className={cn(
                "[&>td]:border-b [&>td]:border-[var(--color-border)]",
                isSelectedFn(row.id) && "bg-accent-primary/[.04]"
              )}>
                {hasSelection ? (
                  <td className="px-2 py-1.5 text-xs">
                    <label className={cn(
                      "w-3.5 h-3.5 cursor-pointer rounded-sm flex items-center justify-center border",
                      isSelectedFn(row.id)
                        ? "border-accent-primary bg-accent-primary/20"
                        : "border-[var(--color-border)] hover:border-fg-muted"
                    )}>
                      {isSelectedFn(row.id) && (
                        <span onClick={(e) => e.stopPropagation()} className="text-accent-primary text-[10px] cursor-pointer">✓</span>
                      )}
                    </label>
                  </td>
                ) : null}
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
            <button type="button" disabled={page <= 1} onClick={() => onPageChange(page - 1)}
                    className="btn-sq btn-sq-muted px-2.5 py-1 disabled:opacity-30 text-[10px]">
              <ChevronLeft className="h-[10px] w-[10px]"/> Prev
            </button>
            <button type="button" disabled={page >= totalPages} onClick={() => onPageChange(page + 1)}
                    className="btn-sq btn-sq-muted px-2.5 py-1 disabled:opacity-30 text-[10px]">
              Next <ChevronRight className="h-[10px] w-[10px]"/>
            </button>
          </div>
        </div>
      </div>
  );
}
