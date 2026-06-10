"use client";
export const dynamic = "force-dynamic";

import { useState, useCallback, useRef, useMemo } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import axios from "axios";
import { DataTable } from "@/components/data-table";
import { StatCard } from "@/components/stat-card";
import { PageErrorBoundary, ErrorBoundary } from "@/components/ui/error-boundary";
import { PanelSkeleton, TableSkeleton } from "@/components/ui/panel-skeleton";

const BACKEND = process.env.NEXT_PUBLIC_API_URL || "/api";

// ── Shared types (mirrors data-table.tsx internals) ──

interface Column<T = Record<string, any>> {
  key: string;
  label: string;
  className?: string;
  render?: (row: T) => React.ReactNode;
}

// ── Export trigger with blob download, progress bar, and feedback ──

function useExport() {
  const [state, setState] = useState<"idle" | "loading" | "done" | "error">("idle");
  const [progress, setProgress] = useState(0);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const cleanup = useCallback(() => {
    if (timerRef.current) clearTimeout(timerRef.current);
  }, []);

  const run = useCallback(
    async (subredditFilter: string, format: "csv" | "json", selectedIds2?: Set<string | number>) => {
      cleanup();
      setState("loading");
      setProgress(0);
      try {
        const params = new URLSearchParams({ format });
        if (subredditFilter) params.set("subreddit", subredditFilter);
        if (selectedIds2 && selectedIds2.size > 0) params.set("ids", [...selectedIds2].join(","));

        const onDownloadProgress = (evt: any) => {
          if (evt.total) setProgress(Math.round((evt.loaded / evt.total) * 100));
        };

        const response = await axios.get(`${BACKEND}/data/export?${params}`, {
          responseType: "blob" as any,
          onDownloadProgress,
        });

        // Try to parse Content-Disposition header for filename.
        const disposition = (response.headers["content-disposition"] as string) || "";
        let filename = `reddit_crawler_export.${format}`;
        const match = disposition.match(/filename\*?=(?:UTF-\d'')??"?([^"';]+)"?/i);
        if (match && match[1]) filename = decodeURIComponent(match[1]);

        // Trigger download via blob URL.
        const url = window.URL.createObjectURL(new Blob([response.data]));
        const link = document.createElement("a");
        link.href = url;
        link.download = fileSafelyName(filename);
        link.style.display = "none";
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);

        // 1-sec delay before showing success, so progress reaches 100.
        timerRef.current = setTimeout(() => {
          setProgress(100);
          setState("done");
          window.URL.revokeObjectURL(url);
        }, 500);
      } catch {
        cleanup();
        setState("error");
        setProgress(0);
      }
    },
    [cleanup],
  );

  const reset = useCallback(() => {
    cleanup();
    setState("idle");
    setProgress(0);
  }, [cleanup]);

  return { state, progress, run, reset };
}

/** Sanitise a server-side filename to something the browser won't choke on. */
function fileSafelyName(raw: string): string {
  return raw.replace(/[<>"\\/]/g, "_").trim() || "reddit_crawler_export";
}

// ── Row interfaces ──

interface DataRow {
  id: string | number;
  subreddit: string;
  author: string;
  score: number;
  title?: string;
  body?: string;
  parentPostTitle?: string;
  createdAt: string;
  url?: string;
}

function postToRow(p: any): DataRow {
  return { id: p.id, subreddit: p.subreddit, title: p.title, author: p.author, score: p.score ?? 0, createdAt: p.createdAt, url: p.url };
}

function commentToRow(c: any): DataRow {
  return { id: c.id, subreddit: c.subreddit, author: c.author, body: c.body, score: c.score ?? 0, parentPostTitle: c.parentPostTitle, createdAt: c.createdAt, url: c.url };
}

// ── Column definitions ──

function makePostColumns(): readonly Column<DataRow>[] {
  return [
    { key: "subreddit", label: "Subreddit" },
    { key: "title", label: "Title" },
    { key: "author", label: "Author" },
    { key: "score", label: "Score" },
    { key: "createdAt", label: "Date", className: "whitespace-nowrap tabular-nums" },
    {
      key: "url", label: "Link",
      render: (row: DataRow) => {
        const fullUrl = row.url || "";
        return <a href={fullUrl} target="_blank" rel="noopener noreferrer" className="text-accent-primary hover:underline text-[10px] truncate block max-w-[140px]" title={fullUrl}>🔗 {String(row.id).slice(0, 8)}</a>;
      },
    },
  ];
}

function makeCommentColumns(): readonly Column<DataRow>[] {
  return [
    { key: "subreddit", label: "Subreddit" },
    { key: "author", label: "Author" },
    { key: "parentPostTitle", label: "Parent Post" },
    { key: "body", label: "Body" },
    { key: "score", label: "Score" },
    { key: "createdAt", label: "Date", className: "whitespace-nowrap tabular-nums" },
  ];
}

// ── Export progress bar (inline) ──

function ExportProgressBar({ state, progress }: { state: string; progress: number }) {
  if (state === "idle") return null;
  const pct = state === "loading" ? Math.min(progress, 95) : progress;
  const bgClass = state === "done" ? "bg-green-400" : state === "error" ? "bg-red-400" : "bg-accent-primary";

  return (
    <div className="w-full h-1.5 bg-surface-low rounded-full overflow-hidden">
      <div
        className={`h-full ${bgClass} rounded-full transition-all duration-300 ease-out`}
        style={{ width: `${pct}%` }}
      />
    </div>
  );
}

// ── Page ──────────────────────────────────────────────────────────────────

export default function DataPage() {
  const [view, setView] = useState<"posts" | "comments">("posts");
  const [subredditFilter, setSubredditFilter] = useState("");
  const [searchQuery, setSearchQuery] = useState("");
  const [page, setPage] = useState(0); // 0-based
  const [pageSize, setPageSize] = useState(25);
  const exportHook = useExport();

  // Column visibility (persisted in memory for the session)
  const visibleColsPosts = useRef(new Set(["subreddit", "title", "author", "score", "createdAt", "url"]));
  const visibleColsComments = useRef(new Set(["subreddit", "author", "parentPostTitle", "body", "score", "createdAt"]));

  // Row selection (persisted across re-renders in memory)
  const [selectedIds, setSelectedIds] = useState<Set<string | number>>(new Set());

  const { data: postsData, isLoading: postsLoading } = useQuery({
    queryKey: ["data-posts", subredditFilter, page, pageSize],
    queryFn: async () => {
      const params = new URLSearchParams();
      if (subredditFilter) params.set("subreddit", subredditFilter);
      if (searchQuery) params.set("search", searchQuery);
      params.set("page", String(page + 1));
      params.set("pageSize", String(pageSize));
      const res = await axios.get(`${BACKEND}/data/posts?${params}`);
      return res.data as { items: any[]; total: number; totalPages: number; page: number };
    },
  });

  const { data: commentsData, isLoading: commentsLoading } = useQuery({
    queryKey: ["data-comments", subredditFilter, page, pageSize],
    queryFn: async () => {
      const params = new URLSearchParams();
      if (subredditFilter) params.set("subreddit", subredditFilter);
      if (searchQuery) params.set("search", searchQuery);
      params.set("page", String(page + 1));
      params.set("pageSize", String(pageSize));
      const res = await axios.get(`${BACKEND}/data/comments?${params}`);
      return res.data as { items: any[]; total: number; totalPages: number; page: number };
    },
  });

  const { data: subredditsData } = useQuery({
    queryKey: ["data-subreddits"],
    queryFn: async () => {
      const res = await axios.get(`${BACKEND}/data/subreddits`);
      return res.data as string[];
    },
  });

  const queryClient = useQueryClient();
  const invalidateData = useCallback(
    (newPage: number, newFilter: string, newSearch: string) => {
      setPage(newPage);
      setSubredditFilter(newFilter);
      setSearchQuery(newSearch);
      setSelectedIds(new Set()); // clear selection on filter change
      queryClient.invalidateQueries({ queryKey: ["data-posts"] });
      queryClient.invalidateQueries({ queryKey: ["data-comments"] });
    },
    [queryClient],
  );

  // ── Column helpers ──

  const allPostCols = useMemo(() => makePostColumns(), []);
  const allCommentCols = useMemo(() => makeCommentColumns(), []);

  const toggleColumn = useCallback((colKey: string, visibleMap: React.MutableRefObject<Set<string>>) => {
    // In a real app this would update state; for now it's just demonstrated visually.
    // Using document as a quick signal handler since we can't easily setState inside ref callbacks.
  }, []);

  // ── Row selection helpers ──

  const toggleRow = useCallback((id: string | number) => {
    setSelectedIds(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  }, []);

  const selectAll = useCallback(() => {
    const rows = view === "posts" ? (postsData?.items ?? []) : (commentsData?.items ?? []);
    setSelectedIds(new Set(rows.map((r: any) => r.id)));
  }, [view, postsData?.items, commentsData?.items]);

  const deselectAll = useCallback(() => {
    setSelectedIds(new Set());
  }, []);

  // Convert raw items → DataRow[] for shared column typing
  const postRows = (postsData?.items ?? []).map(postToRow);
  const commentRows = (commentsData?.items ?? []).map(commentToRow);
  const rows: DataRow[] = view === "posts" ? postRows : commentRows;
  const allColumns = view === "posts" ? allPostCols : allCommentCols;
  const visibleColSet = view === "posts" ? visibleColsPosts.current : visibleColsComments.current;

  // Filter columns to only those that are visible
  const activeColumns: readonly Column<DataRow>[] = allColumns.filter(c => visibleColSet.has(c.key));

  const totalPagesCount = view === "posts"
    ? (postsData?.totalPages ?? 1)
    : (commentsData?.totalPages ?? 1);

  // Pre-compute totals to avoid ??/|| mixing in JSX expressions.
  const totalPosts  = (postsData?.total  ?? 0) + 0;
  const totalComments = (commentsData?.total ?? 0) + 0;
  const currentTotal   = view === "posts" ? totalPosts : totalComments;

  const handleExport    = () => { exportHook.run(subredditFilter, "csv", selectedIds.size > 0 ? selectedIds : undefined); setTimeout(() => exportHook.reset(), 5000); };
  const handleExportJson = () => { exportHook.run(subredditFilter, "json", selectedIds.size > 0 ? selectedIds : undefined); setTimeout(() => exportHook.reset(), 5000); };

  // Current view total for stat display
  const viewTotal = view === "posts" ? totalPosts : totalComments;

  return (
    <PageErrorBoundary>
      <div className="flex w-full flex-col gap-3 min-w-0">

        {/* ── Header band ── */}
        <section className="panel-sq-dense flex items-center justify-between flex-wrap gap-2">
          <span className="section-label block mb-0.5">Data Explorer</span>
          <div className="flex flex-wrap items-center gap-2">

            {/* View tabs */}
            <div className="flex gap-1 bg-surface-low rounded-lg p-0.5">
              <button type="button" onClick={() => { setView("posts"); setSelectedIds(new Set()); }}
                      className={`px-3 py-1 text-xs rounded-md transition-colors font-semibold ${view === "posts" ? "bg-accent-primary/20 text-accent-primary" : "text-fg-muted hover:text-fg-secondary"}`}>
                Posts ({totalPosts})
              </button>
              <button type="button" onClick={() => { setView("comments"); setSelectedIds(new Set()); }}
                      className={`px-3 py-1 text-xs rounded-md transition-colors font-semibold ${view === "comments" ? "bg-accent-primary/20 text-accent-primary" : "text-fg-muted hover:text-fg-secondary"}`}>
                Comments ({totalComments})
              </button>
            </div>

            {/* Subreddit filter */}
            {subredditsData && subredditsData.length > 0 ? (
              <select value={subredditFilter} onChange={(e) => invalidateData(0, e.target.value, searchQuery)}
                      className="px-2 py-1 text-xs bg-surface-low rounded-md border border-[var(--color-border)] text-fg-primary">
                <option value="">All subreddits</option>
                {subredditsData.map((s: string) => (
                  <option key={s} value={s}>{s}</option>
                ))}
              </select>
            ) : (
              <span className="text-[10px] text-fg-muted">Filters after data loads</span>
            )}

            {/* Search input */}
            <input
              type="search"
              placeholder="Search…"
              style={{ width: "9rem" }}
              value={searchQuery}
              onChange={(e) => invalidateData(0, subredditFilter, e.target.value)}
              className="px-2 py-1 text-xs bg-surface-low rounded-md border border-[var(--color-border)] text-fg-primary placeholder:text-fg-muted/40"
            />

            {/* Column visibility toggle */}
            <div className="relative group">
              <button type="button"
                      title="Toggle column visibility"
                      className="px-2.5 py-1 text-[11px] bg-surface-low rounded-md border border-[var(--color-border)] text-fg-muted hover:text-fg-primary transition-colors">
                ⚙ Columns
              </button>
              <div className="absolute right-0 top-full mt-1 w-44 bg-surface-mid border border-[var(--color-border)] rounded-lg shadow-xl opacity-0 invisible group-hover:opacity-100 group-hover:visible transition-all duration-150 z-50 p-2">
                {allColumns.map(col => (
                  <label key={col.key} className="flex items-center gap-2 px-2 py-1 text-xs text-fg-primary hover:bg-surface-high rounded cursor-pointer select-none">
                    {/* Checkbox mock — click toggles visibility */}
                    <input type="checkbox" checked={visibleColSet.has(col.key)} disabled onChange={() => {}} className="accent-accent-primary" />
                    <span className="truncate">{col.label}</span>
                  </label>
                ))}
              </div>
            </div>

            {/* Row selection controls */}
            {rows.length > 0 && (
              <div className="flex gap-1">
                <button type="button" onClick={selectAll} disabled={selectedIds.size === rows.length}
                        className={`px-2 py-1 text-[10px] rounded transition-colors ${selectedIds.size === rows.length ? "text-fg-muted/40 cursor-not-allowed" : "text-fg-muted hover:text-fg-secondary"}`}>
                  ☑ Select All
                </button>
                <button type="button" onClick={deselectAll} disabled={selectedIds.size === 0}
                        className={`px-2 py-1 text-[10px] rounded transition-colors ${selectedIds.size === 0 ? "text-fg-muted/40 cursor-not-allowed" : "text-fg-muted hover:text-fg-secondary"}`}>
                  ☐ Deselect
                </button>
              </div>
            )}

            {/* Export buttons */}
            <div className="flex gap-1 items-center">
              {/* Progress bar (inline above export area) */}
              {exportHook.state !== "idle" && (
                <ExportProgressBar state={exportHook.state} progress={exportHook.progress} />
              )}

              <button type="button" onClick={handleExport} disabled={exportHook.state === "loading"}
                      className={`btn-sq btn-sq-accent px-2.5 py-1 text-[11px] flex items-center gap-1 transition-all ${selectedIds.size > 0 ? "ring-1 ring-accent-primary/40" : ""}`}
                      title={selectedIds.size > 0 ? `Export ${selectedIds.size} selected row(s) as CSV` : `Export ${view} as CSV`}
                      style={{ minWidth: "6.5rem" }}>
                {exportHook.state === "loading" ? (
                  <><span className="inline-block w-2 h-2 rounded-full bg-accent-primary/60 animate-pulse" /> {exportHook.progress > 0 ? `${exportHook.progress}%` : "Exp…"}</>
                ) : exportHook.state === "done" ? (
                  <><span className="text-green-400">✓</span> Done</>
                ) : exportHook.state === "error" ? (
                  <><span className="text-red-400">✗</span> Retry</>
                ) : (
                  <span>📥 CSV</span>
                )}
              </button>
              <button type="button" onClick={handleExportJson} disabled={exportHook.state === "loading"}
                      className={`btn-sq btn-sq-accent px-2.5 py-1 text-[11px] flex items-center gap-1 transition-all ${selectedIds.size > 0 ? "ring-1 ring-accent-primary/40" : ""}`}
                      title={selectedIds.size > 0 ? `Export ${selectedIds.size} selected row(s) as JSON` : `Export ${view} as JSON`}
                      style={{ minWidth: "6.5rem" }}>
                {exportHook.state === "loading" ? (
                  <><span className="inline-block w-2 h-2 rounded-full bg-accent-primary/60 animate-pulse" /> {exportHook.progress > 0 ? `${exportHook.progress}%` : "Exp…"}</>
                ) : exportHook.state === "done" ? (
                  <><span className="text-green-400">✓</span> Done</>
                ) : exportHook.state === "error" ? (
                  <><span className="text-red-400">✗</span> Retry</>
                ) : (
                  <span>📥 JSON</span>
                )}
              </button>
            </div>
          </div>
        </section>

        {/* Row selection status bar */}
        {selectedIds.size > 0 && (
          <section className="panel-sq-dense flex items-center justify-between flex-wrap gap-2 border-l-2 border-l-accent-primary">
            <span className="text-xs text-fg-muted">
              <span className="font-semibold text-accent-primary">{selectedIds.size}</span> row(s) selected
            </span>
            <button type="button" onClick={deselectAll}
                    className="text-[10px] text-fg-muted hover:text-red-400 transition-colors">Deselect all</button>
          </section>
        )}

        {/* ── Stat cards ── */}
        <div className="grid grid-cols-5 gap-3">
          <ErrorBoundary><StatCard label="Total Posts" value={String(totalPosts)} icon="database" /></ErrorBoundary>
          <ErrorBoundary><StatCard label="Total Comments" value={String(totalComments)} icon="message" trend={`+${view === "posts" ? totalPosts : totalComments} in view`} /></ErrorBoundary>
          <ErrorBoundary><StatCard label="Subreddits" value={String(subredditsData?.length ?? 0)} icon="hash" /></ErrorBoundary>
          <ErrorBoundary><StatCard label="Viewing" value={view === "posts" ? `${(page * pageSize) + 1}–${Math.min((page + 1) * pageSize, currentTotal)}` : "-"} icon="clock" trend={`${currentTotal} total`} /></ErrorBoundary>
          <ErrorBoundary><StatCard label="Rows Selected" value={String(selectedIds.size)} icon="database" trend={selectedIds.size > 0 ? "Ready to export" : "Tap ☑ to select"} /></ErrorBoundary>
        </div>

        {/* ── Table + pagination ── */}
        <ErrorBoundary>
          {postsLoading || commentsLoading ? (
            <TableSkeleton rowCount={5} columns={activeColumns.length} />
          ) : rows.length > 0 ? (
            <>
              {/* DataTable renders its own pagination bar — no duplicate below */}
              <DataTable
                key={view + "-" + page + "-" + pageSize}
                columns={activeColumns}
                rows={rows}
                page={page + 1}
                totalPages={totalPagesCount}
                onPageChange={(p) => invalidateData(p - 1, subredditFilter, searchQuery)}
                onSelectAll={selectAll}
                onDeselectAll={deselectAll}
                selectedIds={selectedIds}
              />

              {/* Compact bottom info bar (lighter than a full pagination section) */}
              <div className="flex items-center justify-between gap-3 px-1">
                <div className="flex items-center gap-2 text-xs text-fg-muted">
                  <span>{(page * pageSize) + 1}–{Math.min((page + 1) * pageSize, currentTotal)} / {currentTotal}</span>
                </div>

                <div className="flex items-center gap-3">
                  {/* Page-size selector */}
                  <div className="flex items-center gap-1 text-xs text-fg-muted">
                    <span className="hidden sm:inline">Rows:</span>
                    <button type="button" onClick={() => setPageSize(15)}
                            className={`px-2 py-0.5 rounded text-[10px] font-medium transition-colors ${pageSize === 15 ? "bg-accent-primary/20 text-accent-primary" : "text-fg-muted hover:text-fg-secondary"}`}>
                      15
                    </button>
                    <button type="button" onClick={() => setPageSize(25)}
                            className={`px-2 py-0.5 rounded text-[10px] font-medium transition-colors ${pageSize === 25 ? "bg-accent-primary/20 text-accent-primary" : "text-fg-muted hover:text-fg-secondary"}`}>
                      25
                    </button>
                    <button type="button" onClick={() => setPageSize(50)}
                            className={`px-2 py-0.5 rounded text-[10px] font-medium transition-colors ${pageSize === 50 ? "bg-accent-primary/20 text-accent-primary" : "text-fg-muted hover:text-fg-secondary"}`}>
                      50
                    </button>
                    <button type="button" onClick={() => setPageSize(100)}
                            className={`px-2 py-0.5 rounded text-[10px] font-medium transition-colors ${pageSize === 100 ? "bg-accent-primary/20 text-accent-primary" : "text-fg-muted hover:text-fg-secondary"}`}>
                      100
                    </button>
                  </div>

                  {/* Prev / Next */}
                  <div className="flex gap-1">
                    <button type="button" disabled={page === 0}
                            onClick={() => invalidateData(page - 1, subredditFilter, searchQuery)}
                            className={`px-3 py-1 text-xs bg-surface-mid rounded transition-colors ${page === 0 ? "opacity-40 cursor-not-allowed" : "hover:bg-surface-high"}`}>Prev</button>
                    <span className="text-xs tabular-nums px-2 self-center">{page + 1} / {totalPagesCount ?? "?"}</span>
                    <button type="button" disabled={page >= totalPagesCount - 1}
                            onClick={() => invalidateData(page + 1, subredditFilter, searchQuery)}
                            className={`px-3 py-1 text-xs bg-surface-mid rounded transition-colors ${page >= totalPagesCount - 1 ? "opacity-40 cursor-not-allowed" : "hover:bg-surface-high"}`}>Next</button>
                  </div>
                </div>
              </div>
            </>
          ) : (
            <section className="panel-sq-dense p-6 text-center">
              <p className="text-fg-muted">No data available. Start a crawl from the Controls page or apply filters to narrow results.</p>
            </section>
          )}
        </ErrorBoundary>

      </div>
    </PageErrorBoundary>
  );
}
