"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Loader, Pause, Play } from "lucide-react";
import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { useCrawlerControls, useCrawlerStatus } from "@/hooks/use-reddit-crawler";
import { StatusBadge } from "@/components/ui/status-badge";

const controlSchema = z.object({
  subreddit: z.string().min(2, "Subreddit is required"),
  depth: z.preprocess((val) => Number(val), z.number().min(1).max(10)),
  limit: z.preprocess((val) => Number(val), z.number().min(10).max(1000)),
  includeComments: z.boolean(),
  keywords: z.string().optional(),
});
type ControlFormInput = z.input<typeof controlSchema>;

export function CrawlerControl() {
  const { data: status } = useCrawlerStatus();
  const { startMutation, stopMutation } = useCrawlerControls();
  const form = useForm<ControlFormInput>({
    resolver: zodResolver(controlSchema),
    defaultValues: {
      subreddit: status?.config.subreddit ?? "machinelearning",
      depth: status?.config.depth ?? 4,
      limit: status?.config.limit ?? 250,
      includeComments: status?.config.includeComments ?? true,
      keywords: status?.config.keywords ?? "",
    },
  });

  useEffect(() => {
    if (status?.config) form.reset(status.config);
  }, [form, status?.config]);

  const starting = startMutation.isPending;
  const stopping = stopMutation.isPending;

  return (
    <section className="panel-sq-dense w-full rounded-none overflow-hidden border border-[var(--color-border)] bg-[var(--color-surface-mid)] p-3 sm:p-4">
      {/* Header row */}
      <div className="flex items-start justify-between mb-3">
        <div>
          <span className="section-label block mb-0.5">Crawler Control</span>
          <h3 className="text-sm font-semibold tracking-tight leading-tight text-[var(--color-fg-primary)]">
            Start, stop, and retune live jobs
          </h3>
        </div>
        <StatusBadge tone={status?.isRunning ? "running" : "neutral"} label={status?.isRunning ? status.mode : "Idle"} />
      </div>

      {/* Controls form */}
      <form className="flex flex-col gap-3" onSubmit={form.handleSubmit((v) => startMutation.mutate(v))}>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6 gap-2">
          <label className="xl:col-span-2 flex flex-col gap-1">
            <span className="text-[9px] font-semibold uppercase tracking-wider text-[var(--color-fg-muted)]">Target subreddit</span>
            <input {...form.register("subreddit")} className="input-sq rounded-none" placeholder="machinelearning" />
            {form.formState.errors.subreddit && (
              <span className="text-[9px] text-[var(--color-danger-text)]">{form.formState.errors.subreddit.message}</span>
            )}
          </label>
          <label className="flex flex-col gap-1">
            <span className="text-[9px] font-semibold uppercase tracking-wider text-[var(--color-fg-muted)]">Keywords</span>
            <input {...form.register("keywords")} className="input-sq rounded-none" placeholder="llm, agents" />
          </label>
          <label className="flex flex-col gap-1">
            <span className="text-[9px] font-semibold uppercase tracking-wider text-[var(--color-fg-muted)]">Depth</span>
            <input type="number" {...form.register("depth")} className="input-sq rounded-none" />
          </label>
          <label className="flex flex-col gap-1">
            <span className="text-[9px] font-semibold uppercase tracking-wider text-[var(--color-fg-muted)]">Limit</span>
            <input type="number" {...form.register("limit")} className="input-sq rounded-none" />
          </label>
          <label className="xl:col-span-2 flex items-center gap-1.5 py-[4px] px-3 border border-[var(--color-border)] bg-[var(--color-surface-high)] text-[11px] text-[var(--color-fg-muted)] cursor-pointer hover:border-[var(--color-border-muted)] transition-colors rounded-none">
            <input type="checkbox" {...form.register("includeComments")} className="accent-accent-primary h-3 w-3 rounded-none" />
            <span className="text-[11px] font-medium">Include comments</span>
          </label>
        </div>

        {/* Actions */}
        <div className="flex items-center gap-2 pt-0.5">
          <button type="submit" disabled={starting} className="btn-sq btn-sq-primary rounded-none px-3 py-[5px] disabled:opacity-50 flex items-center gap-1.5">
            {starting ? <Loader className="h-3.5 w-3.5 animate-spin" /> : <Play className="h-3.5 w-3.5" />}
            Start Crawler
          </button>
          <button type="button" onClick={() => stopMutation.mutate()} disabled={stopping || !status?.isRunning}
            className="btn-sq btn-sq-muted px-3 py-[5px] disabled:opacity-30 flex items-center gap-1.5 rounded-none">
            {stopping ? <Loader className="h-3.5 w-3.5 animate-spin" /> : <Pause className="h-3.5 w-3.5" />}
            Stop Crawler
          </button>
        </div>
      </form>
    </section>
  );
}
