"use client";

import { ArrowDownUp, ChevronLeft, ChevronRight, Search, SlidersHorizontal } from "lucide-react";
import { useMemo, useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";

export interface DataTableColumn<T> {
  key: keyof T;
  label: string;
  className?: string;
  sortable?: boolean;
  render?: (row: T) => React.ReactNode;
}

export interface DataTableProps<T extends { id: string }> {
  columns: readonly DataTableColumn<T>[];
  rows: T[];
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  searchValue?: string;
  onSearchChange?: (value: string) => void;
  emptyMessage?: string;
  filtersSlot?: React.ReactNode;
}

export function DataTable<T extends { id: string }>({
  columns,
  rows,
  page,
  totalPages,
  onPageChange,
  searchValue,
  onSearchChange,
  emptyMessage = "No rows match the current filters.",
  filtersSlot,
}: DataTableProps<T>) {
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
    <div className="overflow-hidden rounded-[calc(var(--radius-xl)+0.125rem)] border border-[var(--ds-border-soft)] bg-[rgba(8,17,31,0.6)]">
      {onSearchChange || filtersSlot ? (
        <div className="flex flex-col gap-3 border-b border-[var(--ds-border-soft)] px-4 py-4 lg:flex-row lg:items-center lg:justify-between">
          {onSearchChange ? (
            <Input
              value={searchValue ?? ""}
              onChange={(event) => onSearchChange(event.target.value)}
              placeholder="Filter visible rows"
              containerClassName="w-full lg:max-w-sm"
              leadingIcon={<Search className="h-4 w-4" />}
            />
          ) : (
            <div />
          )}
          {filtersSlot ? (
            <div className="flex items-center gap-2 text-sm text-[var(--ds-text-secondary)]">
              <SlidersHorizontal className="h-4 w-4" />
              {filtersSlot}
            </div>
          ) : null}
        </div>
      ) : null}

      <div className="scroll-shell overflow-x-auto">
        <table className="min-w-full text-left">
          <thead className="border-b border-[var(--ds-border-soft)] bg-[rgba(18,33,56,0.86)]">
            <tr>
              {columns.map((column) => (
                <th
                  key={String(column.key)}
                  className={cn(
                    "px-4 py-3 text-xs font-medium uppercase tracking-[0.22em] text-[var(--ds-text-muted)]",
                    column.className,
                  )}
                >
                  {column.sortable === false ? (
                    column.label
                  ) : (
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
                  )}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {sortedRows.length > 0 ? (
              sortedRows.map((row) => (
                <tr
                  key={row.id}
                  className="border-b border-[var(--ds-border-soft)] last:border-b-0 hover:bg-[rgba(103,179,255,0.05)]"
                >
                  {columns.map((column) => (
                    <td key={String(column.key)} className="px-4 py-4 align-top text-sm text-[var(--ds-text-primary)]">
                      {column.render ? column.render(row) : String(row[column.key] ?? "-")}
                    </td>
                  ))}
                </tr>
              ))
            ) : (
              <tr>
                <td
                  colSpan={columns.length}
                  className="px-4 py-12 text-center text-sm text-[var(--ds-text-secondary)]"
                >
                  {emptyMessage}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <div className="flex flex-col gap-3 border-t border-[var(--ds-border-soft)] px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
        <p className="text-sm text-[var(--ds-text-secondary)]">
          Page {page} of {totalPages}
        </p>
        <div className="flex items-center gap-2">
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={() => onPageChange(page - 1)}
            disabled={page <= 1}
            leadingIcon={<ChevronLeft className="h-4 w-4" />}
          >
            Prev
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={() => onPageChange(page + 1)}
            disabled={page >= totalPages}
            trailingIcon={<ChevronRight className="h-4 w-4" />}
          >
            Next
          </Button>
        </div>
      </div>
    </div>
  );
}
