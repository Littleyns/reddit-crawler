"use client";

import { useMemo, useState } from "react";
import { Search, X } from "lucide-react";
import { cn } from "@/lib/utils";
import { formatDate } from "@/lib/utils";

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export interface DataGridRow {
  id: string;
  title: string;
  subreddit: string;
  type: "post" | "comment" | "thread";
  sentiment: "positive" | "neutral" | "negative";
  createdAt: string;
  keywords: string[];
}

export interface DataGridFilter {
  field: keyof Omit<DataGridRow, "id">;
  value: string;
}

interface SentimentBarProps {
  score: number; // -1 to 1
}

function SentimentBar({ score }: SentimentBarProps) {
  const pct = ((score + 1) / 2) * 100;
  const color =
    score > 0.3 ? "#22c55e" : score < -0.3 ? "#ef4444" : "#64748b";
  return (
    <div className="flex items-center gap-1.5">
      <span
        className="inline-block h-[6px] w-full min-w-[40px] rounded-sm transition-all"
        style={{ width: `${pct}%`, backgroundColor: color, opacity: 0.8 }}
      />
      <span
        className={cn(
          "text-[9px] font-mono tabular-nums",
          score > 0.3 ? "text-emerald-400" : score < -0.3 ? "text-red-400" : "text-slate-400"
        )}
      >
        {score.toFixed(2)}
      </span>
    </div>
  );
}

function MiniSparkline({ data }: { data: number[] }) {
  if (data.length === 0) return null;
  const min = Math.min(...data);
  const max = Math.max(...data);
  const range = max - min || 1;
  const w = 80;
  const h = 20;
  const points = data.map((v, i) => {
    const x = (i / (data.length - 1)) * w;
    const y = h - ((v - min) / range) * h;
    return `${x},${y}`;
  }).join(" ");

  return (
    <svg width={w} height={h} className="shrink-0">
      <polyline
        fill="none"
        stroke="#38bdf8"
        strokeWidth={1.5}
        strokeLinecap="round"
        strokeLinejoin="round"
        points={points}
      />
    </svg>
  );
}

function KeywordPills({ keywords }: { keywords: string[] }) {
  if (!keywords || keywords.length === 0) {
    return (
      <span className="text-[9px] text-[var(--color-fg-muted)] italic">None</span>
    );
  }
  return (
    <div className="flex flex-wrap gap-1">
      {keywords.slice(0, 3).map((kw) => (
        <span
          key={kw}
          className="inline-flex items-center rounded-sm bg-sky-500/20 px-[5px] py-[1px] text-[8px] font-medium text-sky-300"
        >
          {kw}
        </span>
      ))}
      {keywords.length > 3 && (
        <span className="text-[8px] text-[var(--color-fg-muted)]">+{keywords.length - 3}</span>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Filter helpers
// ---------------------------------------------------------------------------

function matchesFilter(row: DataGridRow, filters: DataGridFilter[]): boolean {
  return filters.every((f) => {
    const val = String(row[f.field]).toLowerCase();
    return val.toLowerCase().includes(f.value.toLowerCase());
  });
}

function matchesSearch(row: DataGridRow, search: string): boolean {
  if (!search) return true;
  const s = search.toLowerCase();
  return (
    row.title.toLowerCase().includes(s) ||
    row.subreddit.toLowerCase().includes(s) ||
    row.type.includes(s) ||
    row.sentiment.includes(s) ||
    row.keywords.some((k) => k.toLowerCase().includes(s))
  );
}

// ---------------------------------------------------------------------------
// Column definitions
// ---------------------------------------------------------------------------

const FILTERABLE_FIELDS: (keyof Omit<DataGridRow, "id">)[] = [
  "subreddit",
  "type",
  "sentiment",
] as const;

interface SortState {
  key: keyof DataGridRow;
  dir: "asc" | "desc";
}

const SPARK_DATA_POINTS = [3, 7, 5, 12, 9, 14, 10]; // deterministic fake sparkline

export function DataGrid({ rows }: { rows: DataGridRow[] }) {
  const [search, setSearch] = useState("");
  const [filters, setFilters] = useState<DataGridFilter[]>([]);
  const [sort, setSort] = useState<SortState>({ key: "createdAt", dir: "desc" });

  // ---- filtered data ----
  const processedRows = useMemo(() => {
    let result = rows;

    if (search) {
      result = result.filter((r) => matchesSearch(r, search));
    }

    result = result.filter((r) => matchesFilter(r, filters));

    // sort
    const sorted = [...result].sort((a, b) => {
      const aVal = a[sort.key];
      const bVal = b[sort.key];
      if (typeof aVal === "string" && typeof bVal === "string") {
        return sort.dir === "asc" ? aVal.localeCompare(bVal) : bVal.localeCompare(aVal);
      }
      // createdAt — compare as dates
      return sort.dir === "asc"
        ? new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
        : new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
    });

    return sorted;
  }, [rows, search, filters, sort]);

  const toggleSort = (key: keyof DataGridRow) => {
    setSort((s) => ({ key, dir: s.key === key && s.dir === "asc" ? "desc" : "asc" }));
  };

  const clearFilters = () => setFilters([]);

  const addFilter = (field: keyof Omit<DataGridRow, "id">) => {
    if (filters.some((f) => f.field === field)) return;
    setFilters((prev) => [...prev, { field, value: "" }]);
  };

  const updateFilter = (field: keyof Omit<DataGridRow, "id">, value: string) => {
    setFilters((prev) => prev.map((f) => (f.field === field ? { ...f, value } : f)));
  };

  const removeFilter = (field: keyof Omit<DataGridRow, "id">) => {
    setFilters((prev) => prev.filter((f) => f.field !== field));
  };

  // ---- Unique values for type-ahead suggestions ----
  const uniqueValues = useMemo(() => {
    const map: Record<string, Set<string>> = {};
    rows.forEach((r) => {
      FILTERABLE_FIELDS.forEach((field) => {
        if (!map[field]) map[field] = new Set();
        map[field].add(String(r[field]));
      });
    });
    return Object.fromEntries(
      Object.entries(map).map(([k, v]) => [k, [...v]])
    ) as Record<keyof Omit<DataGridRow, "id">, string[]>;
  }, [rows]);

  const columns = [
    { key: "title" as const, label: "Title", width: "min-w-[28vw]" },
    { key: "subreddit" as const, label: "Subreddit", width: "w-[110px]" },
    { key: "type" as const, label: "Type", width: "w-[70px]" },
    { key: "sentimentScore" as keyof DataGridRow, label: "Sentiment", width: "w-[130px]", isSentiment: true },
    { key: "createdAt" as const, label: "Date", width: "w-[120px]" },
    { key: "keywords" as const, label: "Keywords", width: "w-[160px]" },
  ];

  return (
    <div className="flex w-full flex-col overflow-hidden rounded-none border border-[var(--color-border)] bg-[var(--color-surface-mid)]">
      {/* ---- Search / Filter bar ---- */}
      <div className="p-3 flex flex-col gap-2.5 border-b border-[var(--color-border)] bg-[var(--color-surface-high)]">
        <div className="flex items-center gap-2">
          {/* Search input */}
          <div className="relative flex-1 min-w-[200px]">
            <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-[var(--color-fg-muted)]" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search across all columns…"
              className="w-full h-[28px] bg-transparent border border-[var(--color-border)] rounded-none pl-7 pr-3 text-[10px] outline-none placeholder:text-[var(--color-fg-muted)] text-[var(--color-fg-primary)] focus:border-[var(--color-accent)] transition-colors"
            />
            {search && (
              <button
                onClick={() => setSearch("")}
                className="absolute right-2.5 top-1/2 -translate-y-1/2 text-[var(--color-fg-muted)] hover:text-[var(--color-fg-primary)]"
              >
                <X className="h-3 w-3" />
              </button>
            )}
          </div>

          {/* Add filter buttons */}
          {FILTERABLE_FIELDS.map((field) => (
            <button
              key={field}
              type="button"
              onClick={() => addFilter(field)}
              disabled={filters.some((f) => f.field === field)}
              className={cn(
                "h-[28px] px-2.5 text-[9px] font-semibold uppercase tracking-wider border transition-colors",
                filters.some((f) => f.field === field)
                  ? "bg-[var(--color-accent)]/10 border-[var(--color-accent)]/40 text-[var(--color-accent-text)] cursor-default"
                  : "border-[var(--color-border)] text-[var(--color-fg-muted)] hover:text-[var(--color-fg-primary)] hover:border-[var(--color-border-muted)] disabled:opacity-40"
              )}
            >
              +{field}
            </button>
          ))}

          {filters.length > 0 && (
            <span
              className="h-[28px] px-2 text-[9px] text-[var(--color-danger-text)] hover:text-[var(--color-fg-primary)] flex items-center cursor-pointer border border-[var(--color-border-muted)]"
              onClick={clearFilters}
              title="Clear all filters"
            >
              Clear ({filters.length})
            </span>
          )}
        </div>

        {/* Active filter pills */}
        {filters.map((f) => (
          <FilterPill
            key={f.field}
            field={f.field}
            value={f.value}
            options={uniqueValues[f.field] || []}
            onUpdate={(val) => updateFilter(f.field, val)}
            onRemove={() => removeFilter(f.field)}
          />
        ))}
      </div>

      {/* ---- Table ---- */}
      <div className="flex-1 overflow-auto">
        {/* Header row (sticky top) */}
        <div className="grid grid-cols-[2.5rem_repeat(6,_auto)] border-b border-[var(--color-border)] bg-[var(--color-surface-high)] sticky top-0 z-10" style={{ fontSize: 9, textTransform: "uppercase", letterSpacing: 0.06 }}>
          <div className="px-3 py-1.5 text-[var(--color-fg-muted)] font-semibold">#</div>
          {columns.map((col) => (
            <div
              key={String(col.key)}
              onClick={() => col.label !== "Sentiment" && toggleSort(col.key)}
              className={cn(
                "px-3 py-1.5 flex items-center gap-1 font-semibold text-[var(--color-fg-muted)] hover:text-[var(--color-fg-primary)] transition-colors",
                col.label === "Sentiment" || !col.label ? "cursor-default" : "cursor-pointer"
              )}
            >
              <span>{col.label}</span>
              {sort.key === String(col.key) && (
                <span className={cn("text-[8px]", sort.dir === "asc" ? "" : "rotate-180")}>▲</span>
              )}
            </div>
          ))}
        </div>

        {/* Body rows */}
        {processedRows.length === 0 && (
          <div className="flex items-center justify-center h-40 text-[var(--color-fg-muted)] text-xs">
            No matching records
          </div>
        )}
        {processedRows.map((row, idx) => {
          const score = row.sentiment === "positive" ? 0.6 : row.sentiment === "negative" ? -0.55 : 0.05;
          return (
            <div
              key={row.id}
              className="grid grid-cols-[2.5rem_repeat(6,_auto)] border-b border-[var(--color-border)] hover:bg-[var(--color-accent-soft)] transition-colors text-[10px]"
              style={{ fontSize: 10 }}
            >
              <div className="px-3 py-1.5 tabular-nums text-[var(--color-fg-muted)] font-mono">{idx + 1}</div>
              {/* title */}
              <div className="px-3 py-1.5 min-w-0">
                <p className="truncate font-medium leading-snug text-[var(--color-fg-primary)]">{row.title}</p>
                <MiniSparkline data={SPARK_DATA_POINTS.map((_, i) => SPARK_DATA_POINTS[i % SPARK_DATA_POINTS.length] + Math.sin(idx + i) * 3)} />
              </div>
              {/* subreddit */}
              <div className="px-3 py-1.5 tabular-nums truncate">
                <span className="font-mono text-[var(--color-accent-text)]">{row.subreddit}</span>
              </div>
              {/* type */}
              <div className="px-3 py-1.5">
                <TypeBadge type={row.type} />
              </div>
              {/* sentiment */}
              <div className="px-3 py-1.5 max-w-[140px]">
                <SentimentBar score={score} />
              </div>
              {/* date */}
              <div className="px-3 py-1.5 tabular-nums whitespace-nowrap">
                <span className="font-mono text-[var(--color-fg-muted)]">{formatDate(row.createdAt)}</span>
              </div>
              {/* keywords */}
              <div className="px-3 py-1.5 min-w-0 max-w-[160px]">
                <KeywordPills keywords={row.keywords} />
              </div>
            </div>
          );
        })}
      </div>

      {/* Footer */}
      <div className="flex items-center justify-between px-3 py-1.5 border-t border-[var(--color-border)] bg-[var(--color-surface-low)] text-[9px] text-[var(--color-fg-muted)] font-mono tabular-nums">
        <span>{processedRows.length} of {rows.length} rows</span>
        {filters.length > 0 && (
          <span className="text-[var(--color-accent-text)]">{filters.reduce((s, f) => s + (f.value ? 1 : 0), 0)} active filter{filters.reduce((s, f) => s + (f.value ? 1 : 0), 0) !== 1 ? "s" : ""}</span>
        )}
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Filter pill component
// ---------------------------------------------------------------------------

function FilterPill({
  field,
  value,
  options,
  onUpdate,
  onRemove,
}: {
  field: keyof Omit<DataGridRow, "id">;
  value: string;
  options: string[];
  onUpdate: (v: string) => void;
  onRemove: () => void;
}) {
  const [showMenu, setShowMenu] = useState(false);

  return (
    <div className="relative flex items-center gap-1.5">
      <span
        className="h-[24px] px-2.5 text-[9px] font-semibold bg-sky-500/15 text-sky-300 border border-sky-500/30 rounded-none flex items-center gap-1"
        onClick={() => setShowMenu(!showMenu)}
      >
        {field}:{value && ` "${value}"`}
      </span>
      {/* Text input for free text */}
      <input
        type="text"
        value={value}
        onChange={(e) => onUpdate(e.target.value)}
        placeholder="…any"
        className="h-[24px] px-2 w-[70px] text-[9px] border border-[var(--color-border)] bg-transparent outline-none focus:border-[var(--color-accent)] rounded-none"
      />
      {/* Dropdown for type-ahead */}
      {showMenu && options.length > 0 && (
        <div className="absolute z-20 mt-1 left-0 w-[140px] max-h-[150px] overflow-auto border border-[var(--color-border)] bg-[var(--color-surface-high)] rounded-none shadow-lg py-1">
          {options.map((opt) => (
            <div
              key={opt}
              className="px-2.5 py-1 hover:bg-[var(--color-accent-soft)] cursor-pointer text-[9px] truncate"
              onClick={() => { onUpdate(opt); setShowMenu(false); }}
            >
              {opt}
            </div>
          ))}
        </div>
      )}
      <button
        type="button"
        onClick={onRemove}
        className="text-[var(--color-fg-muted)] hover:text-[var(--color-danger-text)] transition-colors"
      >
        <X className="h-3 w-3" />
      </button>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Helper badges
// ---------------------------------------------------------------------------

function TypeBadge({ type }: { type: "post" | "comment" | "thread" }) {
  const config = {
    post: ["bg-emerald-500/15 text-emerald-400 border-emerald-500/30", "POST"],
    comment: ["bg-purple-500/15 text-purple-400 border-purple-500/30", "COMM"],
    thread: ["bg-sky-500/15 text-sky-400 border-sky-500/30", "THRD"],
  }[type];
  return (
    <span className={`inline-block h-[18px] px-1.5 border text-[8px] font-bold tabular-nums rounded-none ${config[0]}`}>
      {config[1]}
    </span>
  );
}
