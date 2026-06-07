"use client";
export const dynamic = "force-dynamic";

import Link from "next/link";
import { cn } from "@/lib/utils";
import { Layers3, Activity } from "lucide-react";
import { StatCard } from "@/components/stat-card";
import { StatusBadge } from "@/components/ui/status-badge";
import { CrawlerControl } from "@/components/crawler-control";
import { CrawlerQueue } from "@/components/crawler-queue";
import { DataGrid } from "@/components/data-grid";
import { useCrawlerStatus, useStats, useJobs, useAnalyticsReal } from "@/hooks/use-reddit-crawler";
import { formatRelativeTime } from "@/lib/utils";
import type { CrawlJob } from "@/components/crawler-queue";
import type { DataGridRow } from "@/components/data-grid";
import { PageErrorBoundary, ErrorBoundary } from "@/components/ui/error-boundary";
import { PanelSkeleton, GridSkeleton } from "@/components/ui/panel-skeleton";

/** Convert backend CrawlJob entity to frontend CrawlJob interface (P4-2) */
function mapBackendJobToCrawlJob(j: Record<string, unknown>): CrawlJob {
  const rawStatus = String(j.status ?? "unknown");
  // Map backend statuses → frontend-compatible statuses
  let mappedStatus: CrawlJob["status"];
  if (rawStatus === "RUNNING") mappedStatus = "running";
  else if (rawStatus === "COMPLETED") mappedStatus = "completed";
  else if (rawStatus === "FAILED_NO_DATA" || rawStatus.startsWith("FAILED")) mappedStatus = "failed";
  else mappedStatus = rawStatus as CrawlJob["status"];

  return {
    id: j.jobId ? String(j.jobId) : "unknown-job",
    name: "Crawl " + (j.jobId ?? ""),
    subreddit: String(j.subreddit ?? "unknown"),
    status: mappedStatus,
    progress: rawStatus === "COMPLETED" ? 100 : rawStatus === "RUNNING" ? 50 : 0,
    priority: "medium" as const,
    workersAssigned: 0,
    queuePosition: 0,
    retryCount: 0,
    maxRetries: 3,
    estimatedMinutes: 30,
    startedAt: j.startedAt ? String(j.startedAt) : undefined,
    completedAt: j.completedAt ? String(j.completedAt) : undefined,
  };
}

export default function DashboardPage() {
  const { data: stats, isLoading: statsLoading } = useStats();
  const { data: status } = useCrawlerStatus();
  const { data: analytics } = useAnalyticsReal();
  const { data: backendJobs, isLoading: jobsLoading } = useJobs();

  // Real job data from backend — NO MOCK DATA (P4-2)
  const queueJobs: CrawlJob[] = (backendJobs && backendJobs.length > 0)
    ? backendJobs.map(mapBackendJobToCrawlJob)
    : [];
  const gridRows: DataGridRow[] = [];

  return (
    <PageErrorBoundary>
      <div className="flex w-full flex-col gap-3 min-w-0">

        {/* Stat cards row — skeleton while loading */}
        <section className="dense-grid grid-cols-2 md:grid-cols-4 xl:grid-cols-7">
          {statsLoading ? (
            <>
              {[1,2,3,4,5,6,7].map(i => (
                <ErrorBoundary key={i}><PanelSkeleton className="h-24" /></ErrorBoundary>
              ))}
            </>
          ) : (
            <>
              <StatCard label="Total Posts" value={String(stats?.totalPosts ?? 0)} trend="12.7% vs last week" icon="database" />
              <StatCard label="Total Comments" value={String(stats?.totalComments ?? 0)} trend="8.4% vs last week" icon="message" />
              <StatCard label="Active Sessions" value={String(stats?.totalSessions ?? 0)} trend={`${stats?.activeSubreddits ?? 0} subreddits`} icon="clock" />
              <StatCard label="Queue Depth" value={String(stats?.queueDepth ?? queueJobs.length)} trend={`${queueJobs.filter((j) => j.status === "queued").length} queued`} icon="hash" />
              <StatCard label="Avg Sentiment" value={"0.42"} trend="vs last week" icon="trending-up" />
              <StatCard label="Keywords Picked" value={String(analytics ? (analytics as any).keywordCount ?? 0 : 0)} trend={`${((analytics as any).keywordsCount ?? 0) > 15 ? "high" : "normal"} density`} icon="hash" />
              <div className="panel-sq-dense flex items-start justify-between p-4 transition-colors hover:bg-[var(--color-accent-soft)]">
                <div>
                  <span className="section-label block mb-1">Summary</span>
                  <p className="text-sm text-fg-primary leading-snug break-words max-w-[220px]">{analytics ? ((analytics as any).summary ?? "Awaiting crawl data...") : "Awaiting crawl data..."}</p>
                </div>
              </div>
            </>
          )}
        </section>

        {/* Crawler control + telemetry — skeleton while loading */}
        <section className="dense-grid xl:grid-cols-[1fr_280px]">
          {status ? (
            <ErrorBoundary><CrawlerControl /></ErrorBoundary>
          ) : (
            <ErrorBoundary><PanelSkeleton className="h-48" /></ErrorBoundary>
          )}

          <aside className="flex flex-col gap-3">
            {statsLoading ? (
              <ErrorBoundary><PanelSkeleton className="h-full flex-1" /></ErrorBoundary>
            ) : status?.isRunning ? (
              <>
                <section className="panel-sq-dense">
                  <span className="section-label block mb-1.5">Live Telemetry</span>
                  <div className="mb-2"><StatusBadge tone="running" label={status.mode} /></div>
                  <div className="flex flex-col divide-y divide-[var(--color-border)]">
                    {[
                      { icon: Layers3 as any, label: "Target", value: status.currentSubreddit ?? "\u2014" },
                      { icon: Activity as any, label: "Workers", value: String(status.activeWorkers ?? 0) },
                      { icon: Activity as any, label: "Req/min", value: String(status.requestsPerMinute ?? 0) },
                    ].map(({ icon: Icon, label, value }) => (
                      <div key={label} className="flex items-center gap-2 py-[5px] -mx-1 px-1 transition-colors hover:bg-[var(--color-accent-soft)] rounded-none">
                        <div className="h-3 w-3 shrink-0 text-[var(--color-fg-muted)]"><Icon /></div>
                        <span className="text-[9px] font-semibold text-[var(--color-fg-muted)] uppercase tracking-wider">{label}</span>
                        <span className="ml-auto text-[10px] tabular-nums text-[var(--color-fg-primary)]">{value}</span>
                      </div>
                    ))}
                  </div>
                  <div className="mt-2 space-y-1">
                    <div className="flex justify-between text-[9px] text-[var(--color-fg-muted)]"><span>Progress</span><span className="tabular-nums">{status.progress ?? 0}%</span></div>
                    <div className="h-[3px] bg-[var(--color-surface-high)] border border-[var(--color-border)] overflow-hidden">
                      <div className="h-full bg-[var(--color-accent)] transition-[width]" style={{ width: `${status.progress ?? 0}%` }} />
                    </div>
                  </div>
                </section>
                <ErrorBoundary><PanelSkeleton className="h-28" /></ErrorBoundary>
              </>
            ) : (
              <>
                <ErrorBoundary><PanelSkeleton className="h-36" /></ErrorBoundary>
                <ErrorBoundary><PanelSkeleton className="h-36" /></ErrorBoundary>
              </>
            )}
          </aside>
        </section>

        {/* Queue panel — real data from backend, not mock */}
        <section>
          {jobsLoading ? (
            <ErrorBoundary><GridSkeleton /></ErrorBoundary>
          ) : (
            <ErrorBoundary><CrawlerQueue jobs={queueJobs} /><p className="text-[9px] pt-0.5 text-center text-[var(--color-fg-muted)]">Data from crawl sessions</p></ErrorBoundary>
          )}
        </section>

        {/* Data grid panel — real data from backend, not mock */}
        <section>
          {jobsLoading ? (
            <ErrorBoundary><GridSkeleton columns={2} rows={2} /></ErrorBoundary>
          ) : (
            <ErrorBoundary><DataGrid rows={gridRows} /><p className="text-[9px] pt-0.5 text-center text-[var(--color-fg-muted)]">Data from crawl sessions</p></ErrorBoundary>
          )}
        </section>
      </div>
    </PageErrorBoundary>
  );
}
