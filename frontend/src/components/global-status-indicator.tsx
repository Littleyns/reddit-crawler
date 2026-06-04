"use client";

import { useGlobalStatus } from "@/hooks/use-global-status";
import { cn } from "@/lib/utils";

function GlobalStatusDot() {
  const { status } = useGlobalStatus();

  let indicatorClasses = "bg-[var(--color-fg-muted)]/40";
  if (status.isCrawling) {
    indicatorClasses = "bg-[var(--color-success-text)] animate-pulse";
  } else if (status.isProcessing) {
    indicatorClasses = "bg-[var(--color-warning-text)] animate-pulse";
  }

  return <div className={cn("h-[6px] w-[6px] shrink-0 rounded-full", indicatorClasses)} />;
}

export function GlobalStatusIndicator() {
  const { status } = useGlobalStatus();

  const isActive = status.isCrawling || status.isProcessing;

  return (
    <div className={cn(
      "flex items-center gap-1.5 border border-[var(--color-border)] bg-[var(--color-surface-mid)] px-2 py-0.5 transition-opacity",
      isActive ? "opacity-100" : "opacity-50",
    )}>
      <GlobalStatusDot />
      {isActive && (
        <span className="hidden sm:inline text-[9px] font-mono text-[var(--color-fg-muted)] leading-none">
          {status.isCrawling ? "CRAWLING" : status.processingMessage || "PROCESSING…"}
        </span>
      )}
    </div>
  );
}
