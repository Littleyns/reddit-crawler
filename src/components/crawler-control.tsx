"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { LoaderCircle, PauseCircle, PlayCircle } from "lucide-react";
import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { useCrawlerControls, useCrawlerStatus } from "@/hooks/use-reddit-crawler";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { FormActions, FormGrid, FormSection } from "@/components/ui/form";
import { CheckboxField, Input } from "@/components/ui/input";
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

  useEffect(() => {
    if (status?.config) {
      form.reset(status.config);
    }
  }, [form, status?.config]);

  return (
    <Card variant="spotlight">
      <CardHeader
        title="Start, stop, and retune live jobs"
        description="Use this panel to launch a crawl run, adjust collection depth, and narrow the dataset with keyword filters."
        action={
          <StatusBadge
            tone={status?.isRunning ? "running" : "neutral"}
            label={status?.isRunning ? `Live • ${status.mode}` : "Idle"}
          />
        }
      >
        <p className="text-xs uppercase tracking-[0.28em] text-[var(--ds-text-muted)]">
          Crawler Control
        </p>
      </CardHeader>

      <CardContent>
        <form
          className="space-y-5"
          onSubmit={form.handleSubmit(async (values) => {
            await startMutation.mutateAsync(values);
          })}
        >
          <FormSection title="Run configuration">
            <FormGrid>
              <Input
                label="Target subreddit"
                placeholder="machinelearning"
                {...form.register("subreddit")}
                error={form.formState.errors.subreddit?.message}
              />
              <Input
                label="Keywords"
                placeholder="llm, agents, benchmark"
                {...form.register("keywords")}
              />
              <Input
                label="Depth"
                type="number"
                {...form.register("depth")}
                error={form.formState.errors.depth?.message}
              />
              <Input
                label="Limit"
                type="number"
                {...form.register("limit")}
                error={form.formState.errors.limit?.message}
              />
            </FormGrid>

            <CheckboxField
              label="Include comment scraping in this session"
              description="Expect longer runtimes and higher API usage when enabled."
              {...form.register("includeComments")}
            />
          </FormSection>

          <FormActions>
            <Button
              type="submit"
              loading={startMutation.isPending}
              leadingIcon={
                startMutation.isPending ? (
                  <LoaderCircle className="h-4 w-4 animate-spin" />
                ) : (
                  <PlayCircle className="h-4 w-4" />
                )
              }
            >
              Start crawler
            </Button>
            <Button
              type="button"
              variant="secondary"
              loading={stopMutation.isPending}
              disabled={!status?.isRunning}
              leadingIcon={
                stopMutation.isPending ? (
                  <LoaderCircle className="h-4 w-4 animate-spin" />
                ) : (
                  <PauseCircle className="h-4 w-4" />
                )
              }
              onClick={() => stopMutation.mutate()}
            >
              Stop crawler
            </Button>
          </FormActions>
        </form>
      </CardContent>
    </Card>
  );
}
