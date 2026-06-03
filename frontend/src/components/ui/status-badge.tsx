import { cn } from "@/lib/utils";

export function StatusBadge({ tone, label }: { tone: "success" | "running" | "warning" | "error" | "neutral" | "info"; label: string }) {
  const cls = {
    success: "badge-sq-success",
    running: "badge-sq-running",
    warning: "badge-sq-warning",
    error: "badge-sq-danger",
    neutral: "badge-sq-muted",
    info: "badge-sq-running",
  }[tone];

  return <span className={cn("badge-sq rounded-none", cls)}>{label}</span>;
}
