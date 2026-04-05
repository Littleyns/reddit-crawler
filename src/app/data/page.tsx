"use client";

import Link from "next/link";
import { Download, Search } from "lucide-react";
import { useState } from "react";
import { DataTable } from "@/components/data-table";
import { Reveal } from "@/components/ui/reveal";
import { useComments, usePosts } from "@/hooks/use-reddit-crawler";
import { buildExportUrl, formatDate } from "@/lib/utils";
import type { CommentRecord, PostRecord } from "@/lib/types";

const postColumns = [
  {
    key: "title",
    label: "Post",
    render: (row: PostRecord) => (
      <div className="min-w-[18rem]">
        <p className="font-medium">{row.title}</p>
        <p className="mt-1 text-xs uppercase tracking-[0.18em] text-[var(--color-muted)]">
          {row.subreddit} • {row.author}
        </p>
      </div>
    ),
  },
  { key: "score", label: "Score" },
  { key: "commentsCount", label: "Comments" },
  {
    key: "createdAt",
    label: "Created",
    render: (row: PostRecord) => formatDate(row.createdAt),
  },
  {
    key: "url",
    label: "Open",
    render: (row: PostRecord) => (
      <Link href={row.url} target="_blank" className="font-medium text-[var(--color-accent-strong)]">
        Reddit
      </Link>
    ),
  },
] as const;

const commentColumns = [
  {
    key: "body",
    label: "Comment",
    render: (row: CommentRecord) => (
      <div className="min-w-[20rem]">
        <p>{row.body}</p>
        <p className="mt-1 text-xs uppercase tracking-[0.18em] text-[var(--color-muted)]">
          {row.subreddit} • {row.author}
        </p>
      </div>
    ),
  },
  {
    key: "parentPostTitle",
    label: "Parent post",
  },
  { key: "score", label: "Score" },
  {
    key: "createdAt",
    label: "Created",
    render: (row: CommentRecord) => formatDate(row.createdAt),
  },
] as const;

export default function DataPage() {
  const [activeTab, setActiveTab] = useState<"posts" | "comments">("posts");
  const [page, setPage] = useState(1);
  const [search, setSearch] = useState("");

  const query = { page, pageSize: 5, search };
  const postsQuery = usePosts(query);
  const commentsQuery = useComments(query);

  const activeResponse = activeTab === "posts" ? postsQuery.data : commentsQuery.data;

  return (
    <div className="space-y-6">
      <Reveal as="section" className="panel rounded-[32px] border-white/45 p-6">
        <div className="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <p className="text-xs uppercase tracking-[0.32em] text-[var(--color-muted)]">
              Data Explorer
            </p>
            <h1 className="mt-3 text-3xl font-semibold">Search, paginate, and export crawl output</h1>
          </div>

          <div className="flex flex-col gap-3 sm:flex-row">
            <div className="input-shell flex items-center gap-3 rounded-2xl border border-[var(--color-border)] bg-white/75 px-4 py-3">
              <Search className="h-4 w-4 text-[var(--color-muted)]" />
              <input
                value={search}
                onChange={(event) => {
                  setPage(1);
                  setSearch(event.target.value);
                }}
                className="bg-transparent outline-none"
                placeholder="Search titles, bodies, authors"
              />
            </div>

            <div className="flex rounded-2xl border border-[var(--color-border)] bg-white/75 p-1">
              {(["posts", "comments"] as const).map((tab) => (
                <button
                  key={tab}
                  type="button"
                  onClick={() => {
                    setActiveTab(tab);
                    setPage(1);
                  }}
                  className={`interactive-ripple rounded-xl px-4 py-2 text-sm font-medium ${
                    activeTab === tab ? "bg-[var(--color-surface-dark)] text-white" : "text-[var(--color-muted)]"
                  }`}
                >
                  {tab}
                </button>
              ))}
            </div>
          </div>
        </div>

        <div className="mt-6 flex flex-wrap gap-3">
          <Link
            href={buildExportUrl("csv", activeTab, query)}
            className="interactive-ripple interactive-surface inline-flex items-center gap-2 rounded-2xl border border-[var(--color-border)] bg-white/85 px-4 py-3 text-sm font-medium"
          >
            <Download className="h-4 w-4" />
            Export CSV
          </Link>
          <Link
            href={buildExportUrl("json", activeTab, query)}
            className="interactive-ripple interactive-surface inline-flex items-center gap-2 rounded-2xl bg-[var(--color-surface-dark)] px-4 py-3 text-sm font-medium text-white"
          >
            <Download className="h-4 w-4" />
            Export JSON
          </Link>
        </div>
      </Reveal>

      <Reveal as="section" inView delay={90} className="panel rounded-[32px] border-white/45 p-4 sm:p-6">
        {activeTab === "posts" ? (
          <DataTable
            columns={postColumns}
            rows={postsQuery.data?.items ?? []}
            page={activeResponse?.page ?? 1}
            totalPages={activeResponse?.totalPages ?? 1}
            onPageChange={setPage}
          />
        ) : (
          <DataTable
            columns={commentColumns}
            rows={commentsQuery.data?.items ?? []}
            page={activeResponse?.page ?? 1}
            totalPages={activeResponse?.totalPages ?? 1}
            onPageChange={setPage}
          />
        )}
      </Reveal>
    </div>
  );
}
