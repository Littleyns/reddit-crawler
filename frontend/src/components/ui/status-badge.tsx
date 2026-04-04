import { cn } from "@/lib/utils";

export function StatusBadge({
  tone,
  label,
}: {
  tone: "success" | "running" | "warning" | "error" | "neutral";
  label: string;
}) {
  const toneClass = {
    success: "bg-emerald-100 text-emerald-800",
    running: "bg-sky-100 text-sky-800",
    warning: "bg-amber-100 text-amber-800",
    error: "bg-rose-100 text-rose-800",
    neutral: "bg-zinc-100 text-zinc-700",
  }[tone];

  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full px-2.5 py-1 text-xs font-medium uppercase tracking-[0.18em]",
        toneClass,
      )}
    >
      {label}
    </span>
  );
}
