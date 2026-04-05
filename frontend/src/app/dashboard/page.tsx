"use client";

import Link from "next/link";
import {
  Activity,
  ArrowRight,
  Database,
  Layers3,
  MessageSquareText,
  Sparkles,
} from "lucide-react";
import { StatCard } from "@/components/stat-card";
import { StatusBadge } from "@/components/ui/status-badge";
import { useCrawlerStatus, useStats } from "@/hooks/use-reddit-crawler";
import { formatDate, formatRelativeTime } from "@/lib/utils";

export default function DashboardPage() {
  const { data: stats } = useStats();
  const { data: status } = useCrawlerStatus();

  const quickActions = [
    {
      href: "/controls",
      title: "Launch a crawl run",
      description: "Update subreddit, depth, and limits before dispatching workers.",
    },
    {
      href: "/data",
      title: "Inspect captured data",
      description: "Search posts and comments, then export a filtered slice.",
    },
    {
      href: "/settings",
      title: "Manage crawler defaults",
      description: "Configure API credentials, session settings, and operators.",
    },
  ];

  return (
    <div className="space-y-6">
      <section className="grid gap-4 xl:grid-cols-[1.6fr_1fr]">
        <div className="panel rounded-[32px] border-white/45 p-6 sm:p-8">
          <p className="text-xs uppercase tracking-[0.32em] text-[var(--color-muted)]">
            Live Operations
          </p>
          <div className="mt-4 flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
            <div>
              <h1 className="max-w-2xl text-4xl font-semibold leading-tight text-balance">
                Monitor the Reddit Crawler pipeline from ingestion to export.
              </h1>
              <p className="mt-4 max-w-2xl text-sm leading-7 text-[var(--color-muted)]">
                The dashboard reflects live query status when backend endpoints are available and
                falls back to seeded operational data in local development.
              </p>
            </div>
            <Link
              href="/controls"
              className="inline-flex items-center gap-2 rounded-2xl bg-[var(--color-surface-dark)] px-5 py-3 text-sm font-medium text-white"
            >
              Quick start
              <ArrowRight className="h-4 w-4" />
            </Link>
          </div>

          <div className="mt-8 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <StatCard label="Total posts" value={stats?.totalPosts ?? 0} change="+12.7% vs last week" icon={Layers3} />
            <StatCard
              label="Total comments"
              value={stats?.totalComments ?? 0}
              change="+8.4% vs last week"
              icon={MessageSquareText}
            />
            <StatCard
              label="Sessions"
              value={stats?.totalSessions ?? 0}
              change="14 active this month"
              icon={Activity}
            />
            <StatCard
              label="Queue depth"
              value={stats?.queueDepth ?? 0}
              change={`${stats?.activeSubreddits ?? 0} active subreddits`}
              icon={Database}
            />
          </div>
        </div>

        <div className="panel rounded-[32px] border-white/45 p-6">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-xs uppercase tracking-[0.28em] text-[var(--color-muted)]">
                Session Snapshot
              </p>
              <h2 className="mt-3 text-2xl font-semibold">Current run</h2>
            </div>
            <StatusBadge
              tone={status?.isRunning ? "running" : "neutral"}
              label={status?.isRunning ? status.mode : "idle"}
            />
          </div>

          <div className="mt-8 space-y-6">
            <div>
              <p className="text-sm text-[var(--color-muted)]">Subreddit</p>
              <p className="mt-1 text-2xl font-semibold">
                {status?.currentSubreddit ?? "No active job"}
              </p>
            </div>

            <div>
              <div className="flex items-center justify-between text-sm">
                <span className="text-[var(--color-muted)]">Progress</span>
                <span className="font-medium">{status?.progress ?? 0}%</span>
              </div>
              <div className="mt-3 h-3 rounded-full bg-[var(--color-accent-soft)]">
                <div
                  className="h-3 rounded-full bg-[var(--color-accent)]"
                  style={{ width: `${status?.progress ?? 0}%` }}
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="rounded-2xl bg-white/75 p-4">
                <p className="text-xs uppercase tracking-[0.2em] text-[var(--color-muted)]">
                  Workers
                </p>
                <p className="mt-2 text-2xl font-semibold">{status?.activeWorkers ?? 0}</p>
              </div>
              <div className="rounded-2xl bg-white/75 p-4">
                <p className="text-xs uppercase tracking-[0.2em] text-[var(--color-muted)]">
                  RPM
                </p>
                <p className="mt-2 text-2xl font-semibold">{status?.requestsPerMinute ?? 0}</p>
              </div>
            </div>

            <div className="rounded-2xl bg-[var(--color-surface-dark)] p-5 text-white">
              <p className="text-xs uppercase tracking-[0.22em] text-white/60">Last run</p>
              <p className="mt-2 text-xl font-semibold">
                {status?.lastRunAt ? formatDate(status.lastRunAt) : "Unavailable"}
              </p>
              <p className="mt-2 text-sm text-white/65">
                Success rate {stats?.successRate ?? 0}% over recent sessions.
              </p>
            </div>
          </div>
        </div>
      </section>

      <section className="grid gap-6 xl:grid-cols-[1.25fr_0.95fr]">
        <div className="panel rounded-[32px] border-white/45 p-6">
          <div className="flex items-center gap-3">
            <Sparkles className="h-5 w-5 text-[var(--color-accent)]" />
            <div>
              <p className="text-xs uppercase tracking-[0.28em] text-[var(--color-muted)]">
                Recent Activity
              </p>
              <h2 className="mt-2 text-2xl font-semibold">Timeline</h2>
            </div>
          </div>

          <div className="mt-8 space-y-5">
            {stats?.activities.map((item) => (
              <article
                key={item.id}
                className="flex gap-4 rounded-3xl border border-[var(--color-border)] bg-white/70 p-4"
              >
                <div className="mt-1 h-3 w-3 rounded-full bg-[var(--color-accent)]" />
                <div className="min-w-0 flex-1">
                  <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                    <h3 className="text-base font-semibold">{item.title}</h3>
                    <StatusBadge tone={item.status} label={item.status} />
                  </div>
                  <p className="mt-2 text-sm leading-6 text-[var(--color-muted)]">
                    {item.description}
                  </p>
                  <p className="mt-3 text-xs uppercase tracking-[0.18em] text-[var(--color-muted)]">
                    {formatRelativeTime(item.occurredAt)}
                  </p>
                </div>
              </article>
            ))}
          </div>
        </div>

        <div className="space-y-6">
          <section className="panel rounded-[32px] border-white/45 p-6">
            <p className="text-xs uppercase tracking-[0.28em] text-[var(--color-muted)]">
              Quick Actions
            </p>
            <div className="mt-6 space-y-4">
              {quickActions.map((action) => (
                <Link
                  key={action.href}
                  href={action.href}
                  className="block rounded-3xl border border-[var(--color-border)] bg-white/70 p-5 hover:bg-white"
                >
                  <div className="flex items-start justify-between gap-4">
                    <div>
                      <h3 className="text-lg font-semibold">{action.title}</h3>
                      <p className="mt-2 text-sm leading-6 text-[var(--color-muted)]">
                        {action.description}
                      </p>
                    </div>
                    <ArrowRight className="mt-1 h-4 w-4 text-[var(--color-muted)]" />
                  </div>
                </Link>
              ))}
            </div>
          </section>

          <section className="panel rounded-[32px] border-white/45 p-6">
            <p className="text-xs uppercase tracking-[0.28em] text-[var(--color-muted)]">
              Notes
            </p>
            <h2 className="mt-3 text-xl font-semibold">Development mode fallback</h2>
            <p className="mt-4 text-sm leading-7 text-[var(--color-muted)]">
              The frontend is resilient to missing backend routes so the app remains demoable while
              the API surface is still being implemented. Once live endpoints respond, React Query
              will use them automatically.
            </p>
          </section>
        </div>
      </section>
    </div>
  );
}
