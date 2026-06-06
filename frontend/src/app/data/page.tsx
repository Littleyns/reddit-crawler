"use client";

import { useState, useEffect } from "react";
import { useQuery } from "@tanstack/react-query";
import axios from "axios";
import { DataTable } from "@/components/data-table";
import { StatCard } from "@/components/stat-card";
import { ChartSkeleton } from "@/components/ui/chart-skeleton";

const BACKEND = process.env.NEXT_PUBLIC_API_URL || "/api";

interface PostRow {
  id: number;
  title: string;
  subreddit: string;
  author: string;
  upvotes: number;
  commentsCount: number;
  createdUtc: string;
}

interface CommentRow {
  id: number;
  postTitle: string;
  author: string;
  bodyPreview: string;
  upvotes: number;
  createdAt: string;
}

function formatTime(ts: string) {
  if (!ts) return "—";
  const d = new Date(ts);
  return d.toLocaleDateString("en-US", { month: "short", day: "numeric" });
}

export default function DataPage() {
  const [view, setView] = useState<"posts" | "comments">("posts");
  const [subredditFilter, setSubredditFilter] = useState("");
  const [page, setPage] = useState(0);
  const pageSize = 20;

  // Fetch posts from real PostgreSQL via API
  const { data: postsData, isLoading: postsLoading } = useQuery({
    queryKey: ["data-posts", subredditFilter, page],
    queryFn: async () => {
      const params = new URLSearchParams();
      if (subredditFilter) params.set("subreddit", subredditFilter);
      params.set("page", String(page));
      params.set("size", String(pageSize));
      const res = await axios.get(`${BACKEND}/data/posts?${params}`);
      return res.data;
    },
  });

  // Fetch comments from real PostgreSQL via API
  const { data: commentsData, isLoading: commentsLoading } = useQuery({
    queryKey: ["data-comments", subredditFilter, page],
    queryFn: async () => {
      const params = new URLSearchParams();
      if (subredditFilter) params.set("subreddit", subredditFilter);
      params.set("page", String(page));
      params.set("size", String(pageSize));
      const res = await axios.get(`${BACKEND}/data/comments?${params}`);
      return res.data;
    },
  });

  // Fetch available subreddits
  const { data: subredditsData } = useQuery({
    queryKey: ["data-subreddits"],
    queryFn: async () => {
      const res = await axios.get(`${BACKEND}/data/subreddits`);
      return res.data;
    },
  });

  const totalPosts = postsData?.totalElements ?? 0;
  const totalPages = postsData?.totalPages ?? 0;
  const totalComments = commentsData?.totalElements ?? 0;

const columns = view === "posts"
    ? [
        { key: "id" as const, label: "ID" },
        { key: "title" as const, label: "Title" },
        { key: "subreddit" as const, label: "Subreddit" },
        { key: "author" as const, label: "Author" },
        { key: "upvotes" as const, label: "Upvotes" },
        { key: "commentsCount" as const, label: "Comments" },
        { key: "createdUtc" as const, label: "Date", render: (row: Record<string, any>) => formatTime(String(row.createdUtc || "")) },
      ]
    : [
        { key: "id" as const, label: "ID" },
        { key: "postTitle" as const, label: "Post" },
        { key: "author" as const, label: "Author" },
        { key: "bodyPreview" as const, label: "Body Preview" },
        { key: "upvotes" as const, label: "Upvotes" },
        { key: "createdAt" as const, label: "Date", render: (row: Record<string, any>) => formatTime(String(row.createdAt || "")) },
      ];



  const rows = view === "posts" ? (postsData?.content ?? []) : (commentsData?.content ?? []);

  return (
    <div className="flex flex-col gap-4">
      {/* Stats Summary */}
      <div className="dense-grid sm:grid-cols-3">
        {postsLoading || commentsLoading
          ? [...Array(3)].map((_, i) => <ChartSkeleton key={i} className="h-[72px]" />)
          : [
              <StatCard
                key="posts"
                label="Total Posts"
                value={totalPosts.toLocaleString()}
                trend="+ crawled"
                icon="database"
              />,
              <StatCard
                key="comments"
                label="Total Comments"
                value={totalComments.toLocaleString()}
                trend="+ crawled"
                icon="message"
              />,
              <StatCard
                key="subreddits"
                label="Subreddits"
                value={String(subredditsData?.length ?? 0)}
                icon="hash"
              />,
            ]}
      </div>

      {/* Filters */}
      <div className="flex flex-wrap items-center gap-3">
        <button
          onClick={() => setView("posts")}
          className={`px-4 py-2 text-sm font-medium rounded-lg border transition-colors ${
            view === "posts"
              ? "bg-accent-primary text-white border-transparent"
              : "border-border hover:bg-surface-high"
          }`}
        >
          Posts ({totalPosts})
        </button>
        <button
          onClick={() => setView("comments")}
          className={`px-4 py-2 text-sm font-medium rounded-lg border transition-colors ${
            view === "comments"
              ? "bg-accent-primary text-white border-transparent"
              : "border-border hover:bg-surface-high"
          }`}
        >
          Comments ({totalComments})
        </button>

        <input
          type="text"
          placeholder="Filter by subreddit..."
          value={subredditFilter}
          onChange={(e) => { setSubredditFilter(e.target.value); setPage(0); }}
          className="px-3 py-2 text-sm border border-border rounded-lg bg-surface-mid w-56"
        />
      </div>

      {/* Data Table */}
      <div className="overflow-x-auto rounded-lg border border-border">
        {rows.length === 0 && !postsLoading && !commentsLoading ? (
          <div className="p-8 text-center text-fg-muted">
            No data yet. Start a crawler job from /controls to populate this table.
          </div>
        ) : (
          <DataTable 
  columns={columns} 
  rows={rows} 
  page={page + 1} 
  totalPages={Math.max(totalPages, 1)} 
  onPageChange={(p) => setPage(p - 1)} 
/>
        )}

        {/* Pagination */}
        <div className="flex items-center justify-between px-4 py-3 border-t border-border">
          <span className="text-sm text-fg-muted">
            Page {page + 1} of {totalPages}
          </span>
          <div className="flex gap-2">
            <button
              disabled={page === 0}
              onClick={() => setPage(p => p - 1)}
              className="px-3 py-1 text-sm border border-border rounded-md hover:bg-surface-high disabled:opacity-30"
            >
              Previous
            </button>
            <button
              disabled={page >= totalPages - 1}
              onClick={() => setPage(p => p + 1)}
              className="px-3 py-1 text-sm border border-border rounded-md hover:bg-surface-high disabled:opacity-30"
            >
              Next
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}