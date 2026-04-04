"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { LoaderCircle, PauseCircle, PlayCircle } from "lucide-react";
import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { useCrawlerControls, useCrawlerStatus } from "@/hooks/use-reddit-crawler";
import { StatusBadge } from "@/components/ui/status-badge";

const controlSchema = z.object({
  subreddit: z.string().min(2, "Subreddit is required"),
  depth: z.coerce.number().min(1).max(10),
  limit: z.coerce.number().min(10).max(1000),
  includeComments: z.boolean(),
  keywords: z.string().optional(),
});

type ControlFormInput = z.input<typeof controlSchema>;
type ControlFormValues = z.output<typeof controlSchema>;

export function CrawlerControl() {
  const { data: status } = useCrawlerStatus();
  const { startMutation, stopMutation } = useCrawlerControls();
  const form = useForm<ControlFormInput, unknown, ControlFormValues>({
    resolver: zodResolver(controlSchema),
    defaultValues: {
      subreddit: status?.config.subreddit ?? "machinelearning",
      depth: status?.config.depth ?? 4,
      limit: status?.config.limit ?? 250,
      includeComments: status?.config.includeComments ?? true,
      keywords: status?.config.keywords ?? "",
    },
  });

  const isStarting = startMutation.isPending;
  const isStopping = stopMutation.isPending;

  useEffect(() => {
    if (status?.config) {
      form.reset(status.config);
    }
  }, [form, status?.config]);

  return (
    <section className="panel rounded-[30px] border-white/45 p-6">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <p className="text-xs uppercase tracking-[0.28em] text-[var(--color-muted)]">
            Crawler Control
          </p>
          <h3 className="mt-3 text-2xl font-semibold">Start, stop, and retune live jobs</h3>
          <p className="mt-2 max-w-2xl text-sm leading-6 text-[var(--color-muted)]">
            Use this panel to launch a crawl run, adjust collection depth, and optionally narrow
            the dataset with keyword filters.
          </p>
        </div>
        <StatusBadge
          tone={status?.isRunning ? "running" : "neutral"}
          label={status?.isRunning ? `Live • ${status.mode}` : "Idle"}
        />
      </div>

      <form
        className="mt-8 grid gap-4 lg:grid-cols-2"
        onSubmit={form.handleSubmit(async (values) => {
          await startMutation.mutateAsync(values);
        })}
      >
        <label className="flex flex-col gap-2">
          <span className="text-sm font-medium">Target subreddit</span>
          <input
            {...form.register("subreddit")}
            className="rounded-2xl border border-[var(--color-border)] bg-white/80 px-4 py-3 outline-none focus:border-[var(--color-accent)]"
            placeholder="machinelearning"
          />
          <span className="text-sm text-[var(--color-danger)]">
            {form.formState.errors.subreddit?.message}
          </span>
        </label>

        <label className="flex flex-col gap-2">
          <span className="text-sm font-medium">Keywords</span>
          <input
            {...form.register("keywords")}
            className="rounded-2xl border border-[var(--color-border)] bg-white/80 px-4 py-3 outline-none focus:border-[var(--color-accent)]"
            placeholder="llm, agents, benchmark"
          />
        </label>

        <label className="flex flex-col gap-2">
          <span className="text-sm font-medium">Depth</span>
          <input
            type="number"
            {...form.register("depth")}
            className="rounded-2xl border border-[var(--color-border)] bg-white/80 px-4 py-3 outline-none focus:border-[var(--color-accent)]"
          />
        </label>

        <label className="flex flex-col gap-2">
          <span className="text-sm font-medium">Limit</span>
          <input
            type="number"
            {...form.register("limit")}
            className="rounded-2xl border border-[var(--color-border)] bg-white/80 px-4 py-3 outline-none focus:border-[var(--color-accent)]"
          />
        </label>

        <label className="flex items-center gap-3 rounded-2xl border border-[var(--color-border)] bg-white/70 px-4 py-3 lg:col-span-2">
          <input type="checkbox" {...form.register("includeComments")} className="h-4 w-4" />
          <span className="text-sm font-medium">Include comment scraping in this session</span>
        </label>

        <div className="lg:col-span-2 flex flex-col gap-3 sm:flex-row">
          <button
            type="submit"
            disabled={isStarting}
            className="inline-flex items-center justify-center gap-2 rounded-2xl bg-[var(--color-surface-dark)] px-5 py-3 text-sm font-medium text-white disabled:opacity-60"
          >
            {isStarting ? <LoaderCircle className="h-4 w-4 animate-spin" /> : <PlayCircle className="h-4 w-4" />}
            Start crawler
          </button>
          <button
            type="button"
            onClick={() => stopMutation.mutate()}
            disabled={isStopping || !status?.isRunning}
            className="inline-flex items-center justify-center gap-2 rounded-2xl border border-[var(--color-border)] bg-white/80 px-5 py-3 text-sm font-medium disabled:opacity-60"
          >
            {isStopping ? <LoaderCircle className="h-4 w-4 animate-spin" /> : <PauseCircle className="h-4 w-4" />}
            Stop crawler
          </button>
        </div>
      </form>
    </section>
  );
}
