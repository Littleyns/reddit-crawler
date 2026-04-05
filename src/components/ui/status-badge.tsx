import { cn } from "@/lib/utils";

export function StatusBadge({
  tone,
  label,
}: {
  tone: "success" | "running" | "warning" | "error" | "neutral";
  label: string;
}) {
  const toneClass = {
    success: "border-[rgba(82,211,166,0.28)] bg-[rgba(15,46,39,0.75)] text-[var(--ds-success-500)]",
    running: "border-[rgba(103,179,255,0.32)] bg-[rgba(16,34,58,0.82)] text-[var(--ds-primary-500)]",
    warning: "border-[rgba(255,199,107,0.28)] bg-[rgba(51,33,11,0.78)] text-[var(--ds-warning-500)]",
    error: "border-[rgba(255,107,127,0.34)] bg-[rgba(52,16,24,0.82)] text-[var(--ds-danger-500)]",
    neutral: "border-[var(--ds-border-soft)] bg-[rgba(16,29,49,0.82)] text-[var(--ds-text-secondary)]",
  }[tone];

  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full border px-2.5 py-1 text-xs font-medium uppercase tracking-[0.18em]",
        toneClass,
      )}
    >
      {label}
    </span>
  );
}
