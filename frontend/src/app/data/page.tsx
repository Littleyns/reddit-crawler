"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import axios from "axios";
import { DataTable } from "@/components/data-table";
import { StatCard } from "@/components/stat-card";
import { PageErrorBoundary, ErrorBoundary } from "@/components/ui/error-boundary";
import { PanelSkeleton, TableSkeleton } from "@/components/ui/panel-skeleton";

const BACKEND = process.env.NEXT_PUBLIC_API_URL || "/api";

function formatTime(ts: string) {
  if (!ts) return "\u2014";
  const d = new Date(ts);
  return d.toLocaleDateString("en-US", { month: "short", day: "numeric" });
}

export default function DataPage() {
  const [view, setView] = useState<"posts" | "comments">("posts");
  const [subredditFilter, setSubredditFilter] = useState("");
  const [page, setPage] = useState(0);
  const pageSize = 20;

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

  const { data: subredditsData } = useQuery({
    queryKey: ["data-subreddits"],
    queryFn: async () => {
      const res = await axios.get(`${BACKEND}/data/subreddits`);
      return res.data;
    },
  });

  return (
    <PageErrorBoundary>
      <div className="flex w-full flex-col gap-3 min-w-0">
        <section className="panel-sq-dense flex items-center justify-between">
          <span className="section-label block mb-0.5">Data Explorer</span>
          <div className="flex gap-2">
            <button type="button" onClick={() => setView("posts")} className={`px-3 py-1 text-xs rounded ${view === "posts" ? "bg-accent-primary/20 text-accent-primary" : "bg-surface-mid text-fg-muted"}`}>Posts</button>
            <button type="button" onClick={() => setView("comments")} className={`px-3 py-1 text-xs rounded ${view === "comments" ? "bg-accent-primary/20 text-accent-primary" : "bg-surface-mid text-fg-muted"}`}>Comments</button>
          </div>
        </section>

        <div className="grid grid-cols-3 gap-3">
          <ErrorBoundary><StatCard label="Total Posts" value={String(postsData?.totalElements ?? 0)} icon="database" /></ErrorBoundary>
          <ErrorBoundary><StatCard label="Total Comments" value={String(commentsData?.totalElements ?? 0)} icon="message" /></ErrorBoundary>
          <ErrorBoundary><StatCard label="Subreddits" value={String(subredditsData?.length ?? 0)} icon="clock" /></ErrorBoundary>
        </div>

        <ErrorBoundary>
          {postsLoading || commentsLoading ? (
            <TableSkeleton rowCount={5} columns={6} />
          ) : view === "posts" && postsData ? (
            <DataTable rows={(postsData.content ?? []).map((p: any) => ({ ...p }))} />
          ) : view === "comments" && commentsData ? (
            <DataTable rows={(commentsData.content ?? []).map((c: any) => ({ ...c }))} />
          ) : (
            <section className="panel-sq-dense p-6 text-center"><p className="text-fg-muted">No data available.</p></section>
          )}
        </ErrorBoundary>

        {/* Pagination */}
        <div className="flex justify-center gap-2">
          <button type="button" disabled={page === 0} onClick={() => setPage(page - 1)} className="px-3 py-1 text-xs bg-surface-mid rounded disabled:opacity-40">Prev</button>
          <span className="text-xs tabular-nums px-3 py-1">{page + 1}</span>
          <button type="button" onClick={() => setPage(page + 1)} className="px-3 py-1 text-xs bg-surface-mid rounded">Next</button>
        </div>
      </div>
    </PageErrorBoundary>
  );
}
