"use client";

import { ArrowDownUp, ChevronLeft, ChevronRight } from "lucide-react";
import { useMemo, useState } from "react";
import { cn } from "@/lib/utils";

interface Column<T> {
  key: keyof T;
  label: string;
  className?: string;
  render?: (row: T) => React.ReactNode;
}

export function DataTable<T extends { id: string | number }>({
  columns,
  rows,
  page,
  totalPages,
  onPageChange,
}: {
  columns: readonly Column<T>[];
  rows: T[];
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}) {
  const [sortKey, setSortKey] = useState<keyof T | null>(null);
  const [sortDirection, setSortDirection] = useState<"asc" | "desc">("desc");

  const sortedRows = useMemo(() => {
    if (!sortKey) {
      return rows;
    }

    return [...rows].sort((left, right) => {
      const leftValue = left[sortKey];
      const rightValue = right[sortKey];

      if (leftValue === rightValue) {
        return 0;
      }

      const comparison = String(leftValue).localeCompare(String(rightValue), undefined, {
        numeric: true,
        sensitivity: "base",
      });

      return sortDirection === "asc" ? comparison : comparison * -1;
    });
  }, [rows, sortDirection, sortKey]);

  return (
    <div className="overflow-hidden rounded-[28px] border border-[var(--color-border)] bg-white/80">
      <div className="overflow-x-auto">
        <table className="min-w-full text-left">
          <thead className="border-b border-[var(--color-border)] bg-white/90">
            <tr>
              {columns.map((column) => (
                <th
                  key={String(column.key)}
                  className={cn(
                    "px-4 py-3 text-xs font-medium uppercase tracking-[0.2em] text-[var(--color-muted)]",
                    column.className,
                  )}
                >
                  <button
                    type="button"
                    onClick={() => {
                      if (sortKey === column.key) {
                        setSortDirection((current) => (current === "asc" ? "desc" : "asc"));
                        return;
                      }

                      setSortKey(column.key);
                      setSortDirection("desc");
                    }}
                    className="inline-flex items-center gap-2"
                  >
                    {column.label}
                    <ArrowDownUp className="h-3.5 w-3.5" />
                  </button>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {sortedRows.map((row) => (
              <tr key={row.id} className="border-b border-[var(--color-border)] last:border-b-0">
                {columns.map((column) => (
                  <td key={String(column.key)} className="px-4 py-4 align-top text-sm">
                    {column.render ? column.render(row) : String(row[column.key] ?? "-")}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="flex items-center justify-between border-t border-[var(--color-border)] px-4 py-3">
        <p className="text-sm text-[var(--color-muted)]">
          Page {page} of {totalPages}
        </p>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => onPageChange(page - 1)}
            disabled={page <= 1}
            className="inline-flex items-center gap-2 rounded-2xl border border-[var(--color-border)] px-3 py-2 text-sm disabled:opacity-50"
          >
            <ChevronLeft className="h-4 w-4" />
            Prev
          </button>
          <button
            type="button"
            onClick={() => onPageChange(page + 1)}
            disabled={page >= totalPages}
            className="inline-flex items-center gap-2 rounded-2xl border border-[var(--color-border)] px-3 py-2 text-sm disabled:opacity-50"
          >
            Next
            <ChevronRight className="h-4 w-4" />
          </button>
        </div>
      </div>
    </div>
  );
}
