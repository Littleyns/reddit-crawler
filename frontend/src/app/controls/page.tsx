"use client";

import { useCrawlerStatus } from "@/hooks/use-reddit-crawler";
import { StatusBadge } from "@/components/ui/status-badge";
import { PageErrorBoundary, ErrorBoundary } from "@/components/ui/error-boundary";
import { PanelSkeleton } from "@/components/ui/panel-skeleton";

export default function ControlsPage() {
  const { data: status, isLoading } = useCrawlerStatus();

  return (
    <PageErrorBoundary>
      <div className="flex w-full flex-col gap-3 min-w-0">
        <section className="panel-sq-dense flex items-center justify-between mb-2">
          <span className="font-medium text-fg-primary">Crawler Controls</span>
          {isLoading ? (
            <ErrorBoundary><PanelSkeleton className="h-8 w-36" /></ErrorBoundary>
          ) : status?.isRunning ? (
            <StatusBadge tone="success" label={status.mode ?? "running"} />
          ) : (
            <StatusBadge tone="neutral" label="Idle" />
          )}
        </section>

        {isLoading ? (
          <div className="flex flex-col gap-3">
            <ErrorBoundary><PanelSkeleton className="h-40" /></ErrorBoundary>
            <ErrorBoundary><PanelSkeleton className="h-28" /></ErrorBoundary>
          </div>
        ) : status?.isRunning ? (
          <div className="flex flex-col gap-3">
            <section className="panel-sq-dense p-4">
              <span className="section-label block mb-1.5">Status</span>
              <p className="text-sm text-fg-primary">Crawler is actively running on {status.currentSubreddit ?? "unknown"}</p>
              <div className="mt-2 w-full bg-[var(--color-surface-high)] rounded-full h-2 border border-[var(--color-border)] overflow-hidden">
                <div className="h-full bg-accent-primary transition-all duration-500" style={{ width: `${status.progress ?? 0}%` }} />
              </div>
            </section>
            <section className="panel-sq-dense p-4">
              <span className="section-label block mb-1.5">Telemetry</span>
              <div className="flex gap-4 text-xs">
                <div><span className="text-fg-muted">Workers:</span> <span className="font-semibold tabular-nums">{status.activeWorkers ?? 0}</span></div>
                <div><span className="text-fg-muted">Req/min:</span> <span className="font-semibold tabular-nums">{status.requestsPerMinute ?? 0}</span></div>
              </div>
            </section>
          </div>
        ) : (
          <section className="panel-sq-dense p-6 text-center">
            <p className="text-fg-muted">No active crawler. Start a new crawl via the Controls panel.</p>
          </section>
        )}

        {/* Always show error boundary wrapper for CrawlerControl if it exists */}
        {status && (
          <ErrorBoundary>
            {/* Placeholder for actual CrawlerControl component — wrapped for crash safety */}
            <section className="panel-sq-dense p-4">
              <span className="section-label block mb-1.5">Configuration</span>
              <div className="flex gap-4 text-xs">
                <div><span className="text-fg-muted">Subreddit:</span> <span className="font-semibold">{status.currentSubreddit ?? "—"}</span></div>
                <div><span className="text-fg-muted">Depth:</span> <span className="font-semibold tabular-nums">{status.config?.depth ?? 0}</span></div>
                <div><span className="text-fg-muted">Limit:</span> <span className="font-semibold tabular-nums">{status.config?.limit ?? 0}</span></div>
              </div>
            </section>
          </ErrorBoundary>
        )}
      </div>
    </PageErrorBoundary>
  );
}
