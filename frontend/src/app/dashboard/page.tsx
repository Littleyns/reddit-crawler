"use client";

import Link from "next/link";
import { cn } from "@/lib/utils";
import { Database, Layers3, MessageSquareText, ArrowRight, Activity } from "lucide-react";
import { StatCard } from "@/components/stat-card";
import { StatusBadge } from "@/components/ui/status-badge";
import { useCrawlerStatus, useStats } from "@/hooks/use-reddit-crawler";
import { formatDate, formatRelativeTime } from "@/lib/utils";

export default function DashboardPage() {
  const { data: stats } = useStats();
  const { data: status } = useCrawlerStatus();

  return (
    <div className="flex w-full flex-col gap-3 min-w-0">
      {/* Stats row — compact dense 4-up, fills entire width */}
      <div className="dense-grid grid-cols-2 lg:grid-cols-4">
        <StatCard label="Total Posts" value={stats?.totalPosts ?? 0} change="+12.7% vs last week" icon={Layers3} />
        <StatCard label="Total Comments" value={stats?.totalComments ?? 0} change="+8.4% vs last week" icon={MessageSquareText} />
        <StatCard label="Sessions" value={stats?.totalSessions ?? 0} change={`${stats?.activeSubreddits ?? 0} subs`} icon={Activity} />
        <StatCard label="Queue Depth" value={stats?.queueDepth ?? 0} change="Next: r/MachineLearning" icon={Database} />
      </div>

      {/* Main grid: overview + sidebar — w-full */}
      <div className="dense-grid xl:grid-cols-[1fr_280px]">
        {/* Left panel: full-width live operations */}
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
        </section>

        {/* Right sidebar: quick actions + health */}
        <aside className="flex flex-col gap-3">
          {/* Quick Actions Panel */}
          <section className="panel-sq-dense">
            <span className="section-label block mb-1.5">Quick Actions</span>
            <div className="flex flex-col divide-y divide-[var(--color-border)] -mx-px px-px">
              {[
                { label: "Launch a crawl run", href: "/controls" },
                { label: "Inspect captured data", href: "/data" },
                { label: "Manage crawler defaults", href: "/settings" },
              ].map((link, i) => (
                <Link
                  key={link.href}
                  href={link.href}
                  className={cn(
                    "flex items-center justify-between py-1.5 px-2 text-[11px] font-medium text-[var(--color-accent-text)] hover:text-[var(--color-fg-secondary)] transition-colors",
                    i < 2 && "border-b border-[var(--color-border)]"
                  )}
                >
                  <span className="truncate">{link.label}</span>
                  <ArrowRight className="h-3 w-3 shrink-0 opacity-40 group-hover:opacity-100" />
                </Link>
              ))}
            </div>
          </section>

          {/* Health panel */}
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
