"use client";

import { cn } from "@/lib/utils";
import { Clock, Database, Layers3, Zap } from "lucide-react";
import { useCrawlerStatus } from "@/hooks/use-reddit-crawler";
import { formatDate, formatRelativeTime } from "@/lib/utils";
import { CrawlerControl } from "@/components/crawler-control";
import { StatusBadge } from "@/components/ui/status-badge";

export default function ControlsPage() {
  const { data: status } = useCrawlerStatus();

  return (
    <div className="flex w-full flex-col gap-3 min-w-0">
      {/* Top section: control panel + telemetry */}
      <div className="dense-grid xl:grid-cols-[1fr_280px]">
        {/* Left: full crawler control surface — fills available width */}
        <CrawlerControl />

        {/* Right: live telemetry sidebar */}
        <aside className="flex flex-col gap-3">
          {/* Telemetry panel */}
          <section className="panel-sq-dense">
            <span className="section-label block mb-1.5">Live Telemetry</span>

            <div className="mb-2">
              <StatusBadge tone={status?.isRunning ? "running" : "neutral"} label={status?.isRunning ? status.mode : "Idle"} />
            </div>

            <div className="flex flex-col divide-y divide-[var(--color-border)]">
              {[
                { icon: Layers3, label: "Target", value: status?.currentSubreddit ?? "—" },
                { icon: Zap, label: "Workers", value: String(status?.activeWorkers ?? 0) },
                { icon: Database, label: "Request/min", value: String(status?.requestsPerMinute ?? 0) },
              ].map(({ icon: Icon, label, value }) => (
                <div key={label} className="flex items-center gap-2 py-[5px] -mx-1 px-1 transition-colors hover:bg-[var(--color-accent-soft)] rounded-none">
                  <Icon className="h-3 w-3 shrink-0 text-[var(--color-fg-muted)]" />
                  <span className="text-[9px] font-semibold text-[var(--color-fg-muted)] uppercase tracking-wider">{label}</span>
                  <span className="ml-auto text-[10px] tabular-nums text-[var(--color-fg-primary)]">{value}</span>
                </div>
              ))}
            </div>

            {/* Progress */}
            <div className="mt-2 space-y-1">
              <div className="flex justify-between text-[9px] text-[var(--color-fg-muted)]">
                <span>Progress</span>
                <span className="tabular-nums">{status?.progress ?? 0}%</span>
              </div>
              <div className="h-[3px] bg-[var(--color-surface-high)] border border-[var(--color-border)] overflow-hidden">
                <div
                  className="h-full bg-[var(--color-accent)] transition-[width]"
                  style={{ width: `${status?.progress ?? 0}%` }}
                />
              </div>
            </div>

            {/* Last run */}
            {status?.lastRunAt && (
              <div className="mt-2 flex items-center gap-1.5 py-0.5">
                <Clock className="h-[10px] w-[10px] text-[var(--color-fg-muted)]" />
                <span className="text-[9px] text-[var(--color-fg-muted)]">Last run: </span>
                <span className="text-[9px] tabular-nums text-[var(--color-fg-primary)]">{formatRelativeTime(status.lastRunAt)}</span>
              </div>
            )}
          </section>

          {/* Active Configuration panel */}
          <section className="panel-sq-dense">
            <span className="section-label block mb-1.5">Active Configuration</span>
            <div className="flex flex-col divide-y divide-[var(--color-border)] -mx-px px-px">
              {[
                { k: "Subreddit", v: status?.currentSubreddit ?? "—" },
                { k: "Depth", v: String(status?.config.depth ?? 0) },
                { k: "Limit", v: String(status?.config.limit ?? 0) },
                { k: "Comments", v: status?.config.includeComments ? "true" : "false" },
                { k: "Keywords", v: status?.config.keywords || "—" },
              ].map(({ k, v }) => (
                <div key={k} className="flex items-center py-[4px] -mx-1 px-2">
                  <span className="text-[9px] text-[var(--color-fg-muted)] w-20 shrink-0 tabular-nums uppercase tracking-wider">{k}</span>
                  <span className="ml-auto text-[10px] tabular-nums font-medium truncate max-w-[calc(100%-6rem)] text-[var(--color-fg-primary)]">{v || "—"}</span>
                </div>
              ))}
            </div>
          </section>

          {/* Control Tips panel */}
          <section className="panel-sq-dense px-3 py-2">
            <span className="section-label block mb-1.5">Tips</span>
            <ul className="flex flex-col gap-1 text-[10px] text-[var(--color-fg-muted)] leading-relaxed">
              {[
                "Use smaller limits during tuning to validate scraper behavior.",
                "Keyword filtering is optional and helps reduce payload volume.",
                "With comments enabled, expect longer runtimes and higher API usage.",
              ].map((tip) => (
                <li key={tip} className="flex items-start gap-1.5">
                  <span className="mt-[2px] h-[3px] w-[3px] shrink-0 rounded-none bg-[var(--color-border-muted)]" />
                  <span>{tip}</span>
                </li>
              ))}
            </ul>
          </section>
        </aside>
      </div>
    </div>
  );
}
