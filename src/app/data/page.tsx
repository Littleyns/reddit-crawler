"use client";

import Link from "next/link";
import { Download } from "lucide-react";
import { useMemo, useState } from "react";
import { DataTable, type DataTableColumn } from "@/components/data-table";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { useComments, usePosts } from "@/hooks/use-reddit-crawler";
import { buildExportUrl, formatDate, filterBySearch } from "@/lib/utils";
import type { CommentRecord, PostRecord } from "@/lib/types";

const postColumns: readonly DataTableColumn<PostRecord>[] = [
  {
    key: "title",
    label: "Post",
    render: (row) => (
      <div className="min-w-[18rem]">
        <p className="font-medium">{row.title}</p>
        <p className="mt-1 text-xs uppercase tracking-[0.18em] text-[var(--ds-text-muted)]">
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
    render: (row) => formatDate(row.createdAt),
  },
  {
    key: "url",
    label: "Open",
    sortable: false,
    render: (row) => (
      <Link href={row.url} target="_blank" className="font-medium text-[var(--ds-primary-500)]">
        Reddit
      </Link>
    ),
  },
];

const commentColumns: readonly DataTableColumn<CommentRecord>[] = [
  {
    key: "body",
    label: "Comment",
    render: (row) => (
      <div className="min-w-[20rem]">
        <p>{row.body}</p>
        <p className="mt-1 text-xs uppercase tracking-[0.18em] text-[var(--ds-text-muted)]">
          {row.subreddit} • {row.author}
        </p>
      </div>
    ),
  },
  { key: "parentPostTitle", label: "Parent post" },
  { key: "score", label: "Score" },
  {
    key: "createdAt",
    label: "Created",
    render: (row) => formatDate(row.createdAt),
  },
];

export default function DataPage() {
  const [activeTab, setActiveTab] = useState<"posts" | "comments">("posts");
  const [page, setPage] = useState(1);
  const [search, setSearch] = useState("");
  const [localFilter, setLocalFilter] = useState("");

  const query = { page, pageSize: 5, search };
  const postsQuery = usePosts(query);
  const commentsQuery = useComments(query);
  const activeResponse = activeTab === "posts" ? postsQuery.data : commentsQuery.data;

  const visibleRows = useMemo(() => {
    const rows = activeTab === "posts" ? postsQuery.data?.items ?? [] : commentsQuery.data?.items ?? [];
    if (!localFilter) {
      return rows;
    }

    return activeTab === "posts"
      ? filterBySearch(rows as PostRecord[], localFilter, ["title", "author", "subreddit"])
      : filterBySearch(rows as CommentRecord[], localFilter, [
          "body",
          "author",
          "subreddit",
          "parentPostTitle",
        ]);
  }, [activeTab, commentsQuery.data?.items, localFilter, postsQuery.data?.items]);

  return (
    <div className="space-y-6">
      <Card variant="spotlight" className="rounded-[calc(var(--radius-xl)+0.3rem)]">
        <div className="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <p className="text-xs uppercase tracking-[0.32em] text-[var(--ds-text-muted)]">Data Explorer</p>
            <h1 className="mt-3 text-3xl font-semibold">Search, paginate, and export crawl output</h1>
          </div>

          <div className="flex flex-col gap-3 sm:flex-row">
            <div className="flex rounded-[var(--radius-md)] border border-[var(--ds-border-soft)] bg-[rgba(8,17,31,0.5)] p-1">
              {(["posts", "comments"] as const).map((tab) => (
                <button
                  key={tab}
                  type="button"
                  onClick={() => {
                    setActiveTab(tab);
                    setPage(1);
                    setLocalFilter("");
                  }}
                  className={`rounded-[calc(var(--radius-sm)-0.125rem)] px-4 py-2 text-sm font-medium ${
                    activeTab === tab
                      ? "bg-[var(--ds-primary-500)] text-[var(--ds-text-inverse)]"
                      : "text-[var(--ds-text-secondary)]"
                  }`}
                >
                  {tab}
                </button>
              ))}
            </div>
          </div>
        </div>

        <div className="mt-6 flex flex-wrap gap-3">
          <Link href={buildExportUrl("csv", activeTab, query)}>
            <Button variant="secondary" leadingIcon={<Download className="h-4 w-4" />}>
              Export CSV
            </Button>
          </Link>
          <Link href={buildExportUrl("json", activeTab, query)}>
            <Button leadingIcon={<Download className="h-4 w-4" />}>Export JSON</Button>
          </Link>
        </div>
      </Card>

      <Card className="rounded-[calc(var(--radius-xl)+0.3rem)] p-4 sm:p-6">
        {activeTab === "posts" ? (
          <DataTable
            columns={postColumns}
            rows={visibleRows as PostRecord[]}
            page={activeResponse?.page ?? 1}
            totalPages={activeResponse?.totalPages ?? 1}
            onPageChange={setPage}
            searchValue={localFilter}
            onSearchChange={setLocalFilter}
            filtersSlot={
              <div className="flex items-center gap-2">
                <span>Server query:</span>
                <input
                  value={search}
                  onChange={(event) => {
                    setPage(1);
                    setSearch(event.target.value);
                  }}
                  className="rounded-full border border-[var(--ds-border-soft)] bg-[rgba(8,17,31,0.56)] px-3 py-1.5 text-sm text-[var(--ds-text-primary)] outline-none"
                  placeholder="Search API data"
                />
              </div>
            }
          />
        ) : (
          <DataTable
            columns={commentColumns}
            rows={visibleRows as CommentRecord[]}
            page={activeResponse?.page ?? 1}
            totalPages={activeResponse?.totalPages ?? 1}
            onPageChange={setPage}
            searchValue={localFilter}
            onSearchChange={setLocalFilter}
            filtersSlot={
              <div className="flex items-center gap-2">
                <span>Server query:</span>
                <input
                  value={search}
                  onChange={(event) => {
                    setPage(1);
                    setSearch(event.target.value);
                  }}
                  className="rounded-full border border-[var(--ds-border-soft)] bg-[rgba(8,17,31,0.56)] px-3 py-1.5 text-sm text-[var(--ds-text-primary)] outline-none"
                  placeholder="Search API data"
                />
              </div>
            }
          />
        )}
      </Card>
    </div>
  );
}
