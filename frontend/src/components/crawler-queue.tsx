"use client";

import { useState } from "react";
import { RotateCw, Clock, CheckCircle2, AlertCircle, Loader2 } from "lucide-react";

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export interface CrawlJob {
  id: string;
  name: string;          // e.g. "r/MachineLearning – last 7d"
  subreddit: string;
  status: "queued" | "running" | "paused" | "failed" | "completed";
  progress: number;       // 0–100
  priority: "low" | "medium" | "high";
  workersAssigned: number;
  queuePosition: number;
  lastAttemptAt?: string;
  retryCount: number;
  maxRetries: number;
  estimatedMinutes: number;
}

// ---------------------------------------------------------------------------
// Progress bar with pulse animation on active job
// ---------------------------------------------------------------------------

function ProgressBar({ value, status }: { value: number; status: string }) {
  const isComplete = status === "completed";
  const isError = status === "failed" || status === "paused";
  const isRunning = status === "running";

  return (
    <div className="w-full h-[5px] bg-[var(--color-surface-high)] overflow-hidden rounded-sm">
      <div
        className={(() => {
          const cls = isComplete
            ? "bg-emerald-400"
            : isError
              ? "bg-red-400"
              : isRunning
                ? "bg-sky-400 animate-pulse transition-[width] duration-700"
                : "bg-amber-400 transition-[width] duration-700";
          return cls;
        })()}
        style={{ width: `${Math.min(100, Math.max(0, value))}%` }}
      />
    </div>
  );
}

function StatusIcon({ status }: { status: string }) {
  if (status === "running") return <Loader2 className="h-4 w-4 animate-spin text-sky-400" />;
  if (status === "completed") return <CheckCircle2 className="h-4 w-4 text-emerald-400" />;
  if (status === "failed") return <AlertCircle className="h-4 w-4 text-red-400" />;
  if (status === "paused") return <Clock className="h-4 w-4 text-amber-400" />;
  return ( // queued
    <div className="h-4 w-4 rounded-full border-2 border-slate-500 animate-pulse" />
  );
}

function PriorityDot({ priority }: { priority: "low" | "medium" | "high" }) {
  const cls =
    priority === "high"
      ? "bg-red-400"
      : priority === "medium"
        ? "bg-amber-400"
        : "bg-slate-400";
  return <span className={`inline-block h-[6px] w-[6px] rounded-full ${cls}`} title={priority} />;
}

// ---------------------------------------------------------------------------
// Job row
// ---------------------------------------------------------------------------

export function CrawlerQueue({ jobs, onRetry }: {
  jobs: CrawlJob[];
  onRetry?: (id: string) => void;
}) {
  const runningCount = jobs.filter((j) => j.status === "running").length;
  const queuedCount = jobs.filter((j) => j.status === "queued").length;

  return (
    <div className="flex w-full flex-col overflow-hidden rounded-none border border-[var(--color-border)] bg-[var(--color-surface-mid)]">
      {/* Header */}
      <div className="p-3 flex items-center justify-between border-b border-[var(--color-border)] bg-[var(--color-surface-high)]">
        <div className="flex items-center gap-2">
          <Clock className="h-4 w-4 text-[var(--color-fg-muted)]" />
          <span className="text-sm font-semibold tracking-tight text-[var(--color-fg-primary)]">Crawler Queue</span>
          <span className="inline-flex items-center gap-1 rounded-none px-2 py-[2px] text-[9px] font-medium bg-sky-500/15 text-sky-300 border border-sky-500/30">
            {runningCount} running · {queuedCount} queued
          </span>
        </div>
      </div>

      {/* Job rows */}
      <div className="flex-1 overflow-auto">
        {jobs.length === 0 && (
          <div className="flex flex-col items-center justify-center h-36 gap-2 text-[var(--color-fg-muted)] text-xs">
            <Clock className="h-5 w-5 opacity-30" />
            <span>No jobs in queue</span>
          </div>
        )}

        {jobs.map((job) => (
          <JobRow key={job.id} job={job} onRetry={onRetry} />
        ))}
      </div>

      {/* Footer */}
      <div className="flex items-center justify-between px-3 py-1.5 border-t border-[var(--color-border)] bg-[var(--color-surface-low)] text-[9px] text-[var(--color-fg-muted)] font-mono tabular-nums">
        <span>{jobs.length} job{jobs.length !== 1 ? "s" : ""}</span>
        <span>
          {jobs.reduce((s, j) => s + j.progress, 0).toFixed(0)}% overall progress
        </span>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Single job row
// ---------------------------------------------------------------------------

function JobRow({ job, onRetry }: { job: CrawlJob; onRetry?: (id: string) => void }) {
  const [expanded, setExpanded] = useState(false);

  const statusLabel = job.status.charAt(0).toUpperCase() + job.status.slice(1);
  const canRetry = (job.status === "failed" || job.status === "paused") && job.retryCount < job.maxRetries;

  return (
    <div className="border-b border-[var(--color-border)] hover:bg-[var(--color-accent-soft)] transition-colors">
      {/* Main row */}
      <div className="p-3 flex items-center gap-3 text-xs">
        <StatusIcon status={job.status} />

        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1">
            <PriorityDot priority={job.priority} />
            <span className={(() => {
              const c =
                job.status === "completed"
                  ? "text-emerald-400 line-through opacity-60"
                  : job.status === "failed"
                    ? "text-red-400"
                    : "text-[var(--color-fg-primary)]";
              return c;
            })()}>{job.name}</span>
          </div>

          <ProgressBar value={job.progress} status={job.status} />
        </div>

        {/* Right side */}
        <div className="flex items-center gap-4 shrink-0 text-[9px] font-mono tabular-nums text-[var(--color-fg-muted)]">
          <span className="hidden lg:block w-[60px]">{job.progress.toFixed(0)}%</span>
          <span className="hidden md:block w-[70px] truncate">
            <span className="text-[var(--color-accent-text)] font-medium">{job.subreddit}</span>
          </span>
          <span className="w-[65px] inline-flex items-center gap-1">
            <StatusBadged>{statusLabel}</StatusBadged>
          </span>
          <span className="w-[40px] text-right">{job.queuePosition > 0 && job.status !== "running" ? `#${job.queuePosition}` : ""}</span>

          {/* Retry button */}
          {canRetry && onRetry && (
            <button
              type="button"
              onClick={() => onRetry(job.id)}
              className="flex items-center gap-1 h-[20px] px-2 text-[8px] font-semibold uppercase tracking-wider border border-sky-500/30 bg-sky-500/15 text-sky-300 hover:bg-sky-500/25 rounded-none transition-colors cursor-pointer"
              title={`Retry (${job.retryCount}/${job.maxRetries})`}
            >
              <RotateCw className="h-3 w-3" /> Retry
            </button>
          )}

          {/* Expand detail */}
          {onRetry || canRetry ? (
            <button
              type="button"
              onClick={() => setExpanded(!expanded)}
              className="text-[var(--color-fg-muted)] hover:text-[var(--color-fg-primary)] transition-colors cursor-pointer"
              title="Details"
            >
              {expanded ? "▾" : "▸"}
            </button>
          ) : null}
        </div>
      </div>

      {/* Details */}
      {expanded && (
        <div className="px-3 pb-3 border-t border-[var(--color-border)] bg-[var(--color-surface-high)]">
          <div className="grid grid-cols-2 md:grid-cols-4 gap-x-6 gap-y-1 mt-2 text-[9px] text-[var(--color-fg-muted)] font-mono">
            <div>
              <span className="">ID</span>:<br /><span className="text-[var(--color-fg-primary)]">{job.id}</span>
            </div>
            <div>
              <span className="">Workers</span>:<br /><span className="text-[var(--color-fg-primary)]">{job.workersAssigned}</span>
            </div>
            <div>
              <span className="">ETA</span>:<br /><span className="text-[var(--color-fg-primary)]">{job.estimatedMinutes} min</span>
            </div>
            <div>
              <span className="">Retry</span>:<br /><span className="text-[var(--color-fg-primary)]">{job.retryCount}/{job.maxRetries}</span>
            </div>
            {job.lastAttemptAt && (
              <div className="col-span-2 md:col-span-4">
                <span className="">Last attempt</span>:<br /><span className="text-[var(--color-fg-primary)]">{job.lastAttemptAt}</span>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Badge helper
// ---------------------------------------------------------------------------

function StatusBadged({ children }: { children: React.ReactNode }) {
  return (
    <span className="inline-flex items-center gap-1.5 rounded-none px-[6px] py-[2px] text-[8px] font-semibold uppercase tracking-wider border">
      {children}
    </span>
  );
}
