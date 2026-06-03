"use client";

import { Radar, TimerReset, Waypoints } from "lucide-react";
import { CrawlerControl } from "@/components/crawler-control";
import { Card } from "@/components/ui/card";
import { StatusBadge } from "@/components/ui/status-badge";
import { useCrawlerStatus } from "@/hooks/use-reddit-crawler";
import { formatDate } from "@/lib/utils";

export default function ControlsPage() {
  const { data: status } = useCrawlerStatus();

  return (
    <div className="space-y-6">
      <section className="grid gap-6 xl:grid-cols-[1.3fr_0.9fr]">
        <CrawlerControl />

        <aside className="space-y-6">
          <Card variant="outline" className="rounded-[calc(var(--radius-xl)+0.3rem)]">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-xs uppercase tracking-[0.28em] text-[var(--ds-text-muted)]">Runtime State</p>
                <h2 className="mt-3 text-2xl font-semibold">Live telemetry</h2>
              </div>
              <StatusBadge
                tone={status?.isRunning ? "running" : "neutral"}
                label={status?.isRunning ? "running" : "idle"}
              />
            </div>

            <div className="mt-8 space-y-4">
              <div className="rounded-[var(--radius-lg)] border border-[var(--ds-border-soft)] bg-[rgba(255,255,255,0.03)] p-4">
                <div className="flex items-center gap-3">
                  <Radar className="h-5 w-5 text-[var(--ds-primary-500)]" />
                  <div>
                    <p className="text-sm font-medium">Current target</p>
                    <p className="mt-1 text-sm text-[var(--ds-text-secondary)]">
                      {status?.currentSubreddit ?? "No active job"}
                    </p>
                  </div>
                </div>
              </div>

              <div className="rounded-[var(--radius-lg)] border border-[var(--ds-border-soft)] bg-[rgba(255,255,255,0.03)] p-4">
                <div className="flex items-center gap-3">
                  <Waypoints className="h-5 w-5 text-[var(--ds-warning-500)]" />
                  <div>
                    <p className="text-sm font-medium">Collection depth</p>
                    <p className="mt-1 text-sm text-[var(--ds-text-secondary)]">
                      {status?.config.depth ?? 0} levels, limit {status?.config.limit ?? 0}
                    </p>
                  </div>
                </div>
              </div>

              <div className="rounded-[var(--radius-lg)] border border-[var(--ds-border-soft)] bg-[rgba(255,255,255,0.03)] p-4">
                <div className="flex items-center gap-3">
                  <TimerReset className="h-5 w-5 text-[var(--ds-danger-500)]" />
                  <div>
                    <p className="text-sm font-medium">Last change</p>
                    <p className="mt-1 text-sm text-[var(--ds-text-secondary)]">
                      {status?.lastRunAt ? formatDate(status.lastRunAt) : "Unavailable"}
                    </p>
                  </div>
                </div>
              </div>
            </div>
          </Card>

          <Card variant="outline" className="rounded-[calc(var(--radius-xl)+0.3rem)]">
            <p className="text-xs uppercase tracking-[0.28em] text-[var(--ds-text-muted)]">Control Notes</p>
            <ul className="mt-5 space-y-3 text-sm leading-6 text-[var(--ds-text-secondary)]">
              <li>Use smaller limits during tuning to validate scraper behavior quickly.</li>
              <li>Keyword filtering is optional and helps reduce irrelevant payload volume.</li>
              <li>When comments are enabled, expect longer runtimes and higher API consumption.</li>
            </ul>
          </Card>
        </aside>
      </section>
    </div>
  );
}
