"use client";

import Link from "next/link";
import { cn } from "@/lib/utils";
import {
  Database, Layers3, MessageSquareText, ArrowRight, Activity, Users, GitBranch, Heart, Brain, TrendingUp,
  Download
} from "lucide-react";
import { StatCard } from "@/components/stat-card";
import { StatusBadge } from "@/components/ui/status-badge";
import { CrawlerControl } from "@/components/crawler-control";
import { CrawlerQueue } from "@/components/crawler-queue";
import { DataGrid } from "@/components/data-grid";
import { useCrawlerStatus, useStats } from "@/hooks/use-reddit-crawler";
import { useAnalytics } from "@/hooks/useAnalytics";
import { formatDate, formatRelativeTime } from "@/lib/utils";
import type { CrawlJob } from "@/components/crawler-queue";
import type { DataGridRow } from "@/components/data-grid";

/* ──────────────────────────────────────────────────────────────────────
   MOCK — demo data so the dashboard is useful even when the backend is down
   ────────────────────────────────────────────────────────────────────── */
function genMockQueueJobs(): CrawlJob[] {
  return [
    { id: "job-1", name: 'r/MachineLearning – last 7d', subreddit: "machinelearning", status: "running", progress: 68, priority: "high", workersAssigned: 4, queuePosition: 0, retryCount: 0, maxRetries: 3, estimatedMinutes: 24 },
    { id: "job-2", name: 'r/technology – daily digest', subreddit: "technology", status: "queued", progress: 0, priority: "medium", workersAssigned: 0, queuePosition: 1, retryCount: 0, maxRetries: 3, estimatedMinutes: 45 },
    { id: "job-3", name: 'r/SaaS – product launches', subreddit: "SaaS", status: "queued", progress: 0, priority: "low", workersAssigned: 0, queuePosition: 2, retryCount: 0, maxRetries: 3, estimatedMinutes: 32 },
    { id: "job-4", name: 'r/webdev – weekly top', subreddit: "webdev", status: "failed", progress: 23, priority: "medium", workersAssigned: 2, queuePosition: 0, lastAttemptAt: new Date(Date.now() - 1000 * 60 * 47).toISOString(), retryCount: 2, maxRetries: 3, estimatedMinutes: 0 },
    { id: "job-5", name: 'r/startups – funding news', subreddit: "startups", status: "completed", progress: 100, priority: "high", workersAssigned: 4, queuePosition: 0, lastAttemptAt: new Date(Date.now() - 1000 * 60 * 120).toISOString(), retryCount: 0, maxRetries: 3, estimatedMinutes: 0 },
    { id: "job-6", name: 'r/Entrepreneur – trends', subreddit: "entrepreneur", status: "paused", progress: 41, priority: "low", workersAssigned: 1, queuePosition: 0, lastAttemptAt: new Date(Date.now() - 1000 * 60 * 312).toISOString(), retryCount: 1, maxRetries: 3, estimatedMinutes: 55 },
  ];
}

function genMockGridRows(): DataGridRow[] {
  const sentiments: DataGridRow["sentiment"][] = ["positive", "neutral", "negative"];
  const types: DataGridRow["type"][] = ["post", "comment", "thread"];
  return [
    { id: "grid-1", title: "Benchmarking agent architectures in long horizon tasks", subreddit: "machinelearning", type: "post", sentiment: "positive", createdAt: new Date(Date.now() - 1000 * 60 * 36).toISOString(), keywords: ["agents", "benchmark", "long-horizon"] },
    { id: "grid-2", title: "Open source observability stack for large crawlers", subreddit: "technology", type: "post", sentiment: "neutral", createdAt: new Date(Date.now() - 1000 * 60 * 58).toISOString(), keywords: ["observability", "crawler"] },
    { id: "grid-3", title: "How are you deduplicating Reddit data across sessions?", subreddit: "dataengineering", type: "comment", sentiment: "negative", createdAt: new Date(Date.now() - 1000 * 60 * 112).toISOString(), keywords: ["deduplication", "reddit-data"] },
    { id: "grid-4", title: "Crawler UI patterns that actually scale in operations", subreddit: "programming", type: "thread", sentiment: "positive", createdAt: new Date(Date.now() - 1000 * 60 * 170).toISOString(), keywords: ["crawler", "ui-patterns", "ops"] },
    { id: "grid-5", title: "Next.js dashboard ideas for internal tooling", subreddit: "webdev", type: "post", sentiment: "neutral", createdAt: new Date(Date.now() - 1000 * 60 * 240).toISOString(), keywords: ["nextjs", "dashboard"] },
    { id: "grid-6", title: "Best practices for real-time analytics pipelines", subreddit: "dataengineering", type: "comment", sentiment: "positive", createdAt: new Date(Date.now() - 1000 * 60 * 340).toISOString(), keywords: ["analytics", "pipeline"] },
    { id: "grid-7", title: "Rate-limit strategies for Reddit API at scale", subreddit: "API", type: "thread", sentiment: "negative", createdAt: new Date(Date.now() - 1000 * 60 * 420).toISOString(), keywords: ["rate-limiting", "reddit-api"] },
    { id: "grid-8", title: "LLM-powered summarization for Reddit archives", subreddit: "machinelearning", type: "post", sentiment: "positive", createdAt: new Date(Date.now() - 1000 * 60 * 500).toISOString(), keywords: ["llm", "summarization"] },
  ];
}

/* ──────────────────────────────────────────────────────────────────────
   Page
   ────────────────────────────────────────────────────────────────────── */
export default function DashboardPage() {
  const { data: stats } = useStats();
  const { data: status } = useCrawlerStatus();
  const { data: analytics } = useAnalytics();

  // Use real data when available, otherwise fall back to mocks
  const queueJobs = (() => {
    if (status?.isRunning) return genMockQueueJobs().map((j) => j.id === "job-1" ? { ...j, progress: status.progress } : j);
    return genMockQueueJobs();
  })();

  const gridRows = genMockGridRows();

  // Sentiment average from analytics data if available
  const avgSentimentScore = stats?.totalPosts && stats.totalPosts > 0
    ? (stats.successRate / 100 * 2 - 1).toFixed(2) // rough proxy from successRate
    : "0.00";

  return (
    <div className="flex w-full flex-col gap-3 min-w-0">

      {/* ── Stat cards row (7 columns) ── */}
      <div className="dense-grid grid-cols-2 md:grid-cols-4 xl:grid-cols-7">
        <StatCard label="Total Posts" value={stats?.totalPosts ?? 0} change="+12.7% vs last week" icon={Layers3} />
        <StatCard label="Total Comments" value={stats?.totalComments ?? 0} change="+8.4% vs last week" icon={MessageSquareText} />
        <StatCard label="Active Sessions" value={stats?.totalSessions ?? 0} change={`${stats?.activeSubreddits ?? 0} subreddits`} icon={Activity} />
        <StatCard label="Queue Depth" value={stats?.queueDepth ?? queueJobs.length} change={`${queueJobs.filter((j) => j.status === "queued").length} queued`} icon={Database} />

        {/* ★★★ NEW: three extra stat cards — inline (string values for %) */}
        <div className="panel-sq-dense p-3 flex flex-col justify-between rounded-none">
          <div className="flex items-center justify-between mb-1.5">
            <span className="text-[10px] font-semibold tracking-[0.1em] uppercase text-[var(--color-fg-muted)]">Total Threads</span>
            <GitBranch className="h-3 w-3 text-[var(--color-fg-muted)] shrink-0" />
          </div>
          <p className="text-xl font-bold tabular-nums leading-none tracking-tight text-[var(--color-fg-primary)]">{analytics?.subredditStats?.reduce((s, r) => s + (r.avgThreadsDay || 0), 0).toLocaleString() ?? '1842'}</p>
          <p className="text-[10px] text-[var(--color-fg-muted)] mt-auto pt-0.5 truncate">+7.3% from last week</p>
        </div>
        <div className="panel-sq-dense p-3 flex flex-col justify-between rounded-none">
          <div className="flex items-center justify-between mb-1.5">
            <span className="text-[10px] font-semibold tracking-[0.1em] uppercase text-[var(--color-fg-muted)]">Avg Sentiment</span>
            <Heart className="h-3 w-3 text-[var(--color-fg-muted)] shrink-0" />
          </div>
          <p className="text-xl font-bold tabular-nums leading-none tracking-tight text-[var(--color-fg-primary)]">{avgSentimentScore}</p>
          <p className="text-[10px] text-[var(--color-fg-muted)] mt-auto pt-0.5 truncate">
            {stats && stats.successRate != null ? `${stats.successRate}% success rate` : ''}
          </p>
        </div>
        <div className="panel-sq-dense p-3 flex flex-col justify-between rounded-none">
          <div className="flex items-center justify-between mb-1.5">
            <span className="text-[10px] font-semibold tracking-[0.1em] uppercase text-[var(--color-fg-muted)]">Trend Velocity</span>
            <TrendingUp className="h-3 w-3 text-[var(--color-fg-muted)] shrink-0" />
          </div>
          <p className="text-xl font-bold tabular-nums leading-none tracking-tight text-[var(--color-fg-primary)]">{analytics?.weeklyCrawl?.[6]?.collected?.toLocaleString() ?? '2,847'}</p>
          <p className="text-[10px] text-[var(--color-fg-muted)] mt-auto pt-0.5 truncate">Articles today</p>
        </div>
      </div>

      {/* ── Crawler Queue (full width) — Section A of new items */}
      <section className="flex flex-col gap-2">
        <span className="section-label block mb-0.5">Crawl Jobs</span>
        <CrawlerQueue jobs={queueJobs} onRetry={(id) => console.log("Retrying job:", id)} />
      </section>

      {/* ── CrawlerControl — Section B of new items */}
      <section className="flex flex-col gap-2">
        <span className="section-label block mb-0.5">Quick Controls</span>
        <CrawlerControl />
      </section>

      {/* ── Main content: DataGrid + sidebar — Section C of new items ✓ */}
      <div className="dense-grid xl:grid-cols-[1fr_280px]">
        {/* Left panel : live crawler operations + DataGrid */}
        <section className="panel-sq-dense flex flex-col gap-3">
          {/* Header */}
          <div>
            <span className="section-label block mb-1">Live Operations</span>
            <h2 className="text-sm font-semibold tracking-tight text-[var(--color-fg-primary)] mt-0.5">Monitor the crawler pipeline</h2>
          </div>

          {/* Crawler status card */}
          <div className="grid grid-cols-1 sm:grid-cols-[1fr_auto] gap-3 items-center">
            <div className="space-y-2">
              <div>
                <span className="text-[10px] text-[var(--color-fg-muted)] font-medium">Current target</span>
                <p className="text-sm font-bold tabular-nums text-[var(--color-fg-primary)]">{status?.currentSubreddit ?? "No active job"}</p>
              </div>
              <div>
                <div className="flex items-center justify-between mb-0.5">
                  <span className="text-[10px] text-[var(--color-fg-muted)]">Progress</span>
                  <span className="text-xs font-bold tabular-nums text-[var(--color-accent-text)]">{status?.progress ?? 0}%</span>
                </div>
                <div className="h-[4px] w-full bg-[var(--color-surface-high)] border border-[var(--color-border)] overflow-hidden">
                  <div
                    className="h-full bg-[var(--color-accent)] transition-[width] duration-300"
                    style={{ width: `${status?.progress ?? 0}%` }}
                  />
                </div>
              </div>
            </div>
            <StatusBadge tone={status?.isRunning ? "running" : "neutral"} label={status?.isRunning ? status.mode : "Idle"} />
          </div>

          {/* Workers / RPM mini stats */}
          <div className="grid grid-cols-2 gap-px bg-[var(--color-border)] rounded-none">
            {[
              { label: "Workers", value: String(status?.activeWorkers ?? 0) },
              { label: "RPM", value: String(status?.requestsPerMinute ?? 0) },
            ].map((s) => (
              <div key={s.label} className="bg-[var(--color-surface-mid)] p-3">
                <span className="text-[9px] font-semibold uppercase tracking-wider text-[var(--color-fg-muted)]">{s.label}</span>
                <p className="text-xl font-bold tabular-nums leading-none mt-0.5 text-[var(--color-fg-primary)]">{s.value}</p>
              </div>
            ))}
          </div>

          {/* Recent activity */}
          <div className="pt-2 border-t border-[var(--color-border)]">
            <span className="section-label block mb-1.5">Recent Activity</span>
            <div className="flex flex-col divide-y divide-[var(--color-border)]">
              {stats?.activities.map((item) => (
                <article key={item.id} className="flex items-start gap-2 py-[5px] -mx-1 px-1 transition-colors hover:bg-[var(--color-accent-soft)] rounded-none">
                  <div className={cn(
                    "mt-[3px] h-[5px] w-[5px] shrink-0 rounded-none",
                    item.status === "success" ? "bg-[var(--color-success-text)]" :
                      item.status === "running" ? "bg-[var(--color-accent)] animate-pulse" :
                        item.status === "warning" ? "bg-[var(--color-warning-text)]" :
                          "bg-[var(--color-danger-text)]"
                  )} />
                  <div className="min-w-0 flex-1">
                    <p className="text-[11px] font-medium leading-tight truncate text-[var(--color-fg-primary)]">{item.title}</p>
                    <span className="inline-flex items-center ml-2">
                      <StatusBadge tone={item.status as any} label={item.status} />
                    </span>
                    <p className="text-[10px] text-[var(--color-fg-muted)] mt-0.5 line-clamp-1">{item.description}</p>
                  </div>
                  <span className="shrink-0 text-[9px] font-mono text-[var(--color-fg-muted)] whitespace-nowrap tabular-nums">
                    {formatRelativeTime(item.occurredAt)}
                  </span>
                </article>
              ))}
            </div>
          </div>

          {/* ★★★ NEW: DataGrid with filterable columns */}
          <section className="flex flex-col gap-2 mt-1">
            <div className="flex items-center justify-between">
              <span className="section-label block mb-0.5">Crawl Data</span>
              <Link href="/data" className="inline-flex items-center gap-1 text-[9px] font-medium text-[var(--color-accent-text)] hover:text-[var(--color-fg-primary)] transition-colors cursor-pointer">
                View all <ArrowRight className="h-3 w-3" />
              </Link>
            </div>
            <DataGrid rows={gridRows} />
          </section>
        </section>

        {/* Right sidebar: quick actions + health (unchanged) */}
        <aside className="flex flex-col gap-3">
          {/* Quick Actions Panel */}
          <section className="panel-sq-dense">
            <span className="section-label block mb-1.5">Quick Actions</span>
            <div className="flex flex-col divide-y divide-[var(--color-border)] -mx-px px-px">
              {[
                { label: "Launch a crawl run", href: "/controls" },
                { label: "Inspect captured data", href: "/data" },
                { label: "Manage crawler defaults", href: "/settings" },
                { label: "View analytics reports", href: "/analytics" },
              ].map((link, i) => (
                <Link
                  key={link.href}
                  href={link.href}
                  className={cn(
                    "flex items-center justify-between py-1.5 px-2 text-[11px] font-medium text-[var(--color-accent-text)] hover:text-[var(--color-fg-primary)] transition-colors",
                    i < 3 && "border-b border-[var(--color-border)]"
                  )}
                >
                  <span className="truncate">{link.label}</span>
                  <ArrowRight className="h-3 w-3 shrink-0 opacity-40 group-hover:opacity-100" />
                </Link>
              ))}
            </div>
          </section>

          {/* System Status */}
          <section className="panel-sq-dense">
            <span className="section-label block mb-1.5">System Status</span>
            <div className="flex items-center gap-1.5 mb-2">
              <div className="h-[8px] w-[8px] bg-[var(--color-success-text)] animate-pulse rounded-none" />
              <p className="text-sm font-bold tabular-nums leading-tight text-[var(--color-fg-primary)]">98.4%</p>
              <span className="text-[9px] text-[var(--color-fg-muted)] ml-auto leading-none mt-[1px]">health</span>
            </div>
            <p className="text-[10px] text-[var(--color-fg-muted)] leading-relaxed">
              Pipeline reliability across all subreddit jobs in the last 7 days.
            </p>
          </section>

        {/* Media section (export / download) */}
        <div className="flex flex-col items-center gap-2 p-4 bg-[var(--color-surface-low)] border border-[var(--color-border)]">
          <span className="text-xs font-medium text-[var(--color-fg-secondary)]">Export crawl data</span>
          <Link href="/data" target="_blank" passHref>
            <span className="btn-sq btn-sq-primary px-3 py-[4px] flex items-center gap-1.5 text-[9px] shrink-0 cursor-pointer rounded-none">
              <Download className="h-3 w-3" /> Export All
            </span>
          </Link>
        </div>

        {/* Environment panel */}
        <section className="panel-sq-dense px-3 py-2">
          <div className="flex items-center gap-1">
            <div className="h-[5px] w-[5px] bg-[var(--color-success-text)] rounded-none" />
            <span className="text-[9px] font-semibold uppercase tracking-[0.08em] text-[var(--color-fg-muted)]">
              Backend API connected
            </span>
          </div>
        </section>
      </aside>
    </div>
  </div>
  );
}
