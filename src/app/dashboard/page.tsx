"use client";

import Link from "next/link";
import { Activity, ArrowRight, Database, Layers3, MessageSquareText, Sparkles } from "lucide-react";
import { StatCard } from "@/components/stat-card";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
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
      href: "/design-system",
      title: "Review component library",
      description: "Inspect design tokens, variants, and reusable form patterns.",
    },
  ];

  return (
    <div className="w-full space-y-6 px-4 sm:px-6 lg:px-8">
      <section className="grid gap-4 xl:grid-cols-[1.6fr_1fr]">
        <Card className="w-full rounded-[calc(var(--radius-xl)+0.3rem)] p-6 sm:p-8" variant="spotlight">
          <p className="text-xs uppercase tracking-[0.32em] text-[var(--ds-text-muted)]">
            Live Operations
          </p>
          <div className="mt-4 flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
            <div>
              <h1 className="max-w-2xl text-4xl font-semibold leading-tight text-balance">
                Monitor the Reddit Crawler pipeline from ingestion to export.
              </h1>
              <p className="mt-4 max-w-2xl text-sm leading-7 text-[var(--ds-text-secondary)]">
                The dashboard reflects live query status when backend endpoints are available and
                falls back to seeded operational data in local development.
              </p>
            </div>
            <Link href="/controls">
              <Button trailingIcon={<ArrowRight className="h-4 w-4" />}>Quick start</Button>
            </Link>
          </div>

          <div className="mt-8 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <StatCard label="Total posts" value={stats?.totalPosts ?? 0} change="+12.7% vs last week" icon={Layers3} />
            <StatCard label="Total comments" value={stats?.totalComments ?? 0} change="+8.4% vs last week" icon={MessageSquareText} />
            <StatCard label="Sessions" value={stats?.totalSessions ?? 0} change="14 active this month" icon={Activity} />
            <StatCard label="Queue depth" value={stats?.queueDepth ?? 0} change={`${stats?.activeSubreddits ?? 0} active subreddits`} icon={Database} />
          </div>
        </Card>

        <Card variant="elevated" className="rounded-[calc(var(--radius-xl)+0.3rem)]">
          <CardHeader
            title="Current run"
            description="Pipeline status rendered from reusable surface, badge, and action primitives."
            action={
              <StatusBadge
                tone={status?.isRunning ? "running" : "neutral"}
                label={status?.isRunning ? status.mode : "idle"}
              />
            }
          >
            <p className="text-xs uppercase tracking-[0.28em] text-[var(--ds-text-muted)]">
              Session Snapshot
            </p>
          </CardHeader>

          <CardContent className="space-y-6">
            <div>
              <p className="text-sm text-[var(--ds-text-secondary)]">Subreddit</p>
              <p className="mt-1 text-2xl font-semibold">{status?.currentSubreddit ?? "No active job"}</p>
            </div>

            <div>
              <div className="flex items-center justify-between text-sm">
                <span className="text-[var(--ds-text-secondary)]">Progress</span>
                <span className="font-medium">{status?.progress ?? 0}%</span>
              </div>
              <div className="mt-3 h-3 rounded-full bg-[rgba(103,179,255,0.12)]">
                <div className="h-3 rounded-full bg-[var(--ds-primary-500)]" style={{ width: `${status?.progress ?? 0}%` }} />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="rounded-[var(--radius-md)] border border-[var(--ds-border-soft)] bg-[rgba(255,255,255,0.04)] p-4">
                <p className="text-xs uppercase tracking-[0.2em] text-[var(--ds-text-muted)]">Workers</p>
                <p className="mt-2 text-2xl font-semibold">{status?.activeWorkers ?? 0}</p>
              </div>
              <div className="rounded-[var(--radius-md)] border border-[var(--ds-border-soft)] bg-[rgba(255,255,255,0.04)] p-4">
                <p className="text-xs uppercase tracking-[0.2em] text-[var(--ds-text-muted)]">RPM</p>
                <p className="mt-2 text-2xl font-semibold">{status?.requestsPerMinute ?? 0}</p>
              </div>
            </div>

            <div className="rounded-[var(--radius-lg)] border border-[var(--ds-border-strong)] bg-[linear-gradient(180deg,rgba(103,179,255,0.12),rgba(8,17,31,0.12))] p-5">
              <p className="text-xs uppercase tracking-[0.22em] text-[var(--ds-text-muted)]">Last run</p>
              <p className="mt-2 text-xl font-semibold">{status?.lastRunAt ? formatDate(status.lastRunAt) : "Unavailable"}</p>
              <p className="mt-2 text-sm text-[var(--ds-text-secondary)]">
                Success rate {stats?.successRate ?? 0}% over recent sessions.
              </p>
            </div>
          </CardContent>
        </Card>
      </section>

      <section className="grid gap-6 xl:grid-cols-[1.25fr_0.95fr]">
        <Card className="w-full rounded-[calc(var(--radius-xl)+0.3rem)]">
          <div className="flex items-center gap-3">
            <Sparkles className="h-5 w-5 text-[var(--ds-primary-500)]" />
            <div>
              <p className="text-xs uppercase tracking-[0.28em] text-[var(--ds-text-muted)]">Recent Activity</p>
              <h2 className="mt-2 text-2xl font-semibold">Timeline</h2>
            </div>
          </div>

          <div className="mt-8 space-y-5">
            {stats?.activities.map((item) => (
              <article
                key={item.id}
                className="flex gap-4 rounded-[var(--radius-lg)] border border-[var(--ds-border-soft)] bg-[rgba(255,255,255,0.03)] p-4"
              >
                <div className="mt-1 h-3 w-3 rounded-full bg-[var(--ds-primary-500)]" />
                <div className="min-w-0 flex-1">
                  <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                    <h3 className="text-base font-semibold">{item.title}</h3>
                    <StatusBadge tone={item.status} label={item.status} />
                  </div>
                  <p className="mt-2 text-sm leading-6 text-[var(--ds-text-secondary)]">{item.description}</p>
                  <p className="mt-3 text-xs uppercase tracking-[0.18em] text-[var(--ds-text-muted)]">
                    {formatRelativeTime(item.occurredAt)}
                  </p>
                </div>
              </article>
            ))}
          </div>
        </Card>

        <div className="space-y-6">
          <Card variant="outline" className="rounded-[calc(var(--radius-xl)+0.3rem)]">
            <p className="text-xs uppercase tracking-[0.28em] text-[var(--ds-text-muted)]">Quick Actions</p>
            <div className="mt-6 space-y-4">
              {quickActions.map((action) => (
                <Link
                  key={action.href}
                  href={action.href}
                  className="block rounded-[var(--radius-lg)] border border-[var(--ds-border-soft)] bg-[rgba(255,255,255,0.03)] p-5 hover:bg-[rgba(103,179,255,0.08)]"
                >
                  <div className="flex items-start justify-between gap-4">
                    <div>
                      <h3 className="text-lg font-semibold">{action.title}</h3>
                      <p className="mt-2 text-sm leading-6 text-[var(--ds-text-secondary)]">{action.description}</p>
                    </div>
                    <ArrowRight className="mt-1 h-4 w-4 text-[var(--ds-text-muted)]" />
                  </div>
                </Link>
              ))}
            </div>
          </Card>

          <Card variant="outline" className="rounded-[calc(var(--radius-xl)+0.3rem)]">
            <p className="text-xs uppercase tracking-[0.28em] text-[var(--ds-text-muted)]">Notes</p>
            <h2 className="mt-3 text-xl font-semibold">Development mode fallback</h2>
            <p className="mt-4 text-sm leading-7 text-[var(--ds-text-secondary)]">
              The frontend is resilient to missing backend routes so the app remains demoable while
              the API surface is still being implemented.
            </p>
          </Card>
        </div>
      </section>
    </div>
  );
}
