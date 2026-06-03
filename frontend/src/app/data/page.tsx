"use client";

import { useMemo, useState } from "react";
import { cn } from "@/lib/utils";
import Link from "next/link";
import { Download, Search } from "lucide-react";
import { DataTable } from "@/components/data-table";
import { useComments, usePosts } from "@/hooks/use-reddit-crawler";
import { buildExportUrl, formatDate } from "@/lib/utils";
import type { CommentRecord, PostRecord } from "@/lib/types";

const postColumns = [
  {
    key: "title" as const,
    label: "Post",
    render: (row: PostRecord) => (
      <div className="min-w-[12rem]">
        <p className="text-[11px] font-medium leading-tight truncate text-[var(--color-fg-primary)]">{row.title}</p>
        <p className="mt-0.5 text-[9px] text-[var(--color-fg-muted)] tabular-nums whitespace-nowrap">{row.subreddit} • {row.author}</p>
      </div>
    ),
  },
  { key: "score" as const, label: "Score", render: (r: PostRecord) => <span className="tabular-nums text-[11px] text-[var(--color-fg-primary)]">{r.score.toLocaleString()}</span> },
  { key: "commentsCount" as const, label: "Comments", render: (r: PostRecord) => <span className="tabular-nums text-[11px] text-[var(--color-fg-primary)]">{r.commentsCount}</span> },
  { key: "createdAt" as const, label: "Created", render: (row: PostRecord) => <span className="text-[var(--color-fg-muted)] tabular-nums text-[9px] whitespace-nowrap">{formatDate(row.createdAt)}</span> },
  { key: "url" as const, label: "Link", render: (row: PostRecord) => (
    <Link href={row.url} target="_blank" rel="noopener noreferrer" className="text-[10px] text-[var(--color-accent-text)] hover:text-[var(--color-fg-secondary)] transition-colors">Reddit →</Link>
  )},
] as const;

const commentColumns = [
  {
    key: "body" as const,
    label: "Comment",
    render: (row: CommentRecord) => (
      <div className="min-w-[16rem] max-w-xl truncate">
        <p className="text-[10px] leading-tight text-[var(--color-fg-primary)] line-clamp-1">{row.body}</p>
        <p className="mt-0.5 text-[9px] text-[var(--color-fg-muted)] tabular-nums whitespace-nowrap">{row.subreddit} • {row.author}</p>
      </div>
    ),
  },
  {key: "score" as const, label: "Score", render: (r: CommentRecord) => <span className="tabular-nums text-[11px] text-[var(--color-fg-primary)]">{r.score.toLocaleString()}</span>},
  { key: "createdAt" as const, label: "Created", render: (row: CommentRecord) => <span className="text-[var(--color-fg-muted)] tabular-nums text-[9px] whitespace-nowrap">{formatDate(row.createdAt)}</span> },
] as const;

export default function DataPage() {
  const [activeTab, setActiveTab] = useState<"posts" | "comments">("posts");
  const [page, setPage] = useState(1);
  const [search, setSearch] = useState("");

  const query = useMemo(() => ({ page, pageSize: 50, search }), [page, search]);
  const postsQuery = usePosts(query);
  const commentsQuery = useComments(query);

  const activeResponse = activeTab === "posts" ? postsQuery.data : commentsQuery.data;
  const totalRecords = activeResponse?.total ?? 0;
  const totalPages = activeResponse?.totalPages ?? 1;

  return (
    <div className="flex w-full flex-col gap-3 min-w-0">
      {/* Top bar — full-width */}
      <section className="panel-sq-dense flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div className="min-w-0">
          <span className="section-label block mb-0.5">Data Explorer</span>
          <h2 className="text-sm font-semibold tracking-tight mt-0.5 truncate text-[var(--color-fg-primary)]">
            Search {activeTab} and export crawl data.
          </h2>
        </div>

        <div className="flex gap-1.5 flex-wrap items-stretch shrink-0">
          {/* Search */}
          <div className="flex items-center border border-[var(--color-border)] bg-[var(--color-surface-high)] px-3 min-w-[240px] lg:w-[280px]">
            <Search className="h-3.5 w-3.5 text-[var(--color-fg-muted)] mr-2 shrink-0" />
            <input
              value={search}
              onChange={(e) => { setPage(1); setSearch(e.target.value); }}
              className="bg-transparent text-[11px] outline-none flex-1 min-w-0 placeholder:text-[var(--color-fg-muted)] text-[var(--color-fg-primary)]"
              placeholder="Search titles, authors..."
            />
          </div>

          {/* Tabs */}
          <div className="flex border border-[var(--color-border)] bg-[var(--color-surface-high)]">
            {(["posts", "comments"] as const).map((tab) => (
              <button key={tab} type="button" onClick={() => { setActiveTab(tab); setPage(1); }}
                className={cn(
                  "px-2.5 py-[4px] text-[9px] font-semibold uppercase tracking-wider transition-colors border-l border-[var(--color-border)] first:border-l-0",
                  activeTab === tab ? "bg-[var(--color-accent)]/10 text-[var(--color-accent-text)]" : "text-[var(--color-fg-muted)] hover:text-[var(--color-fg-primary)]"
                )}
              >
                {tab}
              </button>
            ))}
          </div>

          {/* Export */}
          <Link href={buildExportUrl("csv", activeTab, query)} target="_blank">
            <span className="btn-sq btn-sq-primary px-2.5 py-[4px] flex items-center gap-1.5 text-[9px] shrink-0 cursor-pointer rounded-none">
              <Download className="h-3 w-3" /> CSV
            </span>
          </Link>
          <Link href={buildExportUrl("json", activeTab, query)} target="_blank">
            <span className="btn-sq btn-sq-muted px-2.5 py-[4px] flex items-center gap-1.5 text-[9px] shrink-0 hover:border-[var(--color-border-muted)] cursor-pointer rounded-none">
              <Download className="h-3 w-3" /> JSON
            </span>
          </Link>
        </div>
      </section>

      {/* Summary bar */}
      <div className="flex items-center gap-2 px-1 text-[9px] font-mono text-[var(--color-fg-muted)] tabular-nums">
        <span>{totalRecords.toLocaleString()} records</span>
        <span className="text-[var(--color-border)]">/</span>
        <span>page {activeResponse?.page ?? 1} of {totalPages}</span>
      </div>

      {/* Table — full width */}
      <div className="flex w-full min-w-0 flex-1">
        {activeTab === "posts" ? (
          <DataTable<PostRecord> columns={postColumns as any} rows={postsQuery.data?.items ?? []} page={activeResponse?.page ?? 1} totalPages={totalPages} onPageChange={setPage} />
        ) : (
          <DataTable<CommentRecord> columns={commentColumns as any} rows={commentsQuery.data?.items ?? []} page={activeResponse?.page ?? 1} totalPages={totalPages} onPageChange={setPage} />
        )}
      </div>

      {/* Bottom bar */}
      <div className="flex items-center justify-between px-1 text-[9px] text-[var(--color-fg-muted)]">
        <span>
          Showing {((activeResponse?.page ?? 1) - 1) * (activeResponse?.pageSize ?? 50) + 1}–
          {Math.min(activeResponse?.page! * (activeResponse?.pageSize ?? 50), totalRecords)} of {totalRecords}
        </span>
        <Link href={buildExportUrl("csv", activeTab, query)} target="_blank" rel="noopener noreferrer" className="text-[var(--color-accent-text)] hover:text-[var(--color-fg-primary)] transition-colors">export all →</Link>
      </div>
    </div>
  );
}
