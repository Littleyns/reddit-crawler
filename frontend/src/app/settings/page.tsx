"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { LoaderCircle, Save } from "lucide-react";
import { useEffect } from "react";
import { useForm, useWatch } from "react-hook-form";
import { z } from "zod";
import { ApiKeyInput } from "@/components/api-key-input";
import { AuthForm } from "@/components/auth-form";
import { StatusBadge } from "@/components/ui/status-badge";
import { useSaveSettings, useSettings } from "@/hooks/use-reddit-crawler";

const settingsSchema = z.object({
  apiKey: z.string().min(8, "API key is required"),
  defaultSubreddit: z.string().min(2),
  defaultDepth: z.coerce.number().min(1).max(10),
  defaultLimit: z.coerce.number().min(10).max(1000),
  autoExport: z.boolean(),
  exportFormat: z.enum(["csv", "json"]),
  sessionTimeoutMinutes: z.coerce.number().min(5).max(240),
  users: z.array(
    z.object({
      id: z.string(),
      name: z.string(),
      email: z.string().email(),
      role: z.enum(["admin", "analyst", "viewer"]),
    }),
  ),
});
type SettingsFormInput = z.input<typeof settingsSchema>;
type SettingsFormValues = z.output<typeof settingsSchema>;

export default function SettingsPage() {
  const settingsQuery = useSettings();
  const saveMutation = useSaveSettings();
  const form = useForm<SettingsFormInput, unknown, SettingsFormValues>({
    resolver: zodResolver(settingsSchema),
    defaultValues: settingsQuery.data,
  });
  const apiKey = useWatch({ control: form.control, name: "apiKey" });

  useEffect(() => {
    if (settingsQuery.data) {
      form.reset(settingsQuery.data);
    }
  }, [form, settingsQuery.data]);

  return (
    <div className="grid gap-6 xl:grid-cols-[1.25fr_0.85fr]">
      <section className="panel rounded-[32px] border-white/45 p-6">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <p className="text-xs uppercase tracking-[0.32em] text-[var(--color-muted)]">
              Settings
            </p>
            <h1 className="mt-3 text-3xl font-semibold">API credentials and scrape defaults</h1>
          </div>
          <StatusBadge
            tone={saveMutation.isSuccess ? "success" : "neutral"}
            label={saveMutation.isSuccess ? "saved" : "draft"}
          />
        </div>

        <form
          className="mt-8 space-y-6"
          onSubmit={form.handleSubmit(async (values) => {
            await saveMutation.mutateAsync(values);
          })}
        >
          <label className="flex flex-col gap-2">
            <span className="text-sm font-medium">Reddit API key</span>
            <ApiKeyInput value={apiKey ?? ""} onChange={(value) => form.setValue("apiKey", value)} />
            <span className="text-sm text-[var(--color-danger)]">
              {form.formState.errors.apiKey?.message}
            </span>
          </label>

          <div className="grid gap-4 md:grid-cols-2">
            <label className="flex flex-col gap-2">
              <span className="text-sm font-medium">Default subreddit</span>
              <input
                {...form.register("defaultSubreddit")}
                className="rounded-2xl border border-[var(--color-border)] bg-white/80 px-4 py-3 outline-none focus:border-[var(--color-accent)]"
              />
            </label>

            <label className="flex flex-col gap-2">
              <span className="text-sm font-medium">Session timeout (minutes)</span>
              <input
                type="number"
                {...form.register("sessionTimeoutMinutes")}
                className="rounded-2xl border border-[var(--color-border)] bg-white/80 px-4 py-3 outline-none focus:border-[var(--color-accent)]"
              />
            </label>

            <label className="flex flex-col gap-2">
              <span className="text-sm font-medium">Default depth</span>
              <input
                type="number"
                {...form.register("defaultDepth")}
                className="rounded-2xl border border-[var(--color-border)] bg-white/80 px-4 py-3 outline-none focus:border-[var(--color-accent)]"
              />
            </label>

            <label className="flex flex-col gap-2">
              <span className="text-sm font-medium">Default limit</span>
              <input
                type="number"
                {...form.register("defaultLimit")}
                className="rounded-2xl border border-[var(--color-border)] bg-white/80 px-4 py-3 outline-none focus:border-[var(--color-accent)]"
              />
            </label>
          </div>

          <div className="grid gap-4 md:grid-cols-2">
            <label className="flex items-center gap-3 rounded-2xl border border-[var(--color-border)] bg-white/75 px-4 py-3">
              <input type="checkbox" {...form.register("autoExport")} className="h-4 w-4" />
              <span className="text-sm font-medium">Auto export after successful runs</span>
            </label>

            <label className="flex flex-col gap-2">
              <span className="text-sm font-medium">Export format</span>
              <select
                {...form.register("exportFormat")}
                className="rounded-2xl border border-[var(--color-border)] bg-white/80 px-4 py-3 outline-none focus:border-[var(--color-accent)]"
              >
                <option value="json">JSON</option>
                <option value="csv">CSV</option>
              </select>
            </label>
          </div>

          <button
            type="submit"
            disabled={saveMutation.isPending}
            className="inline-flex items-center gap-2 rounded-2xl bg-[var(--color-surface-dark)] px-5 py-3 text-sm font-medium text-white disabled:opacity-60"
          >
            {saveMutation.isPending ? <LoaderCircle className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
            Save settings
          </button>
        </form>
      </section>

      <div className="space-y-6">
        <AuthForm />

        <section className="panel rounded-[32px] border-white/45 p-6">
          <p className="text-xs uppercase tracking-[0.28em] text-[var(--color-muted)]">
            User Management
          </p>
          <h2 className="mt-3 text-2xl font-semibold">Current operators</h2>
          <div className="mt-6 space-y-3">
            {settingsQuery.data?.users.map((user) => (
              <div
                key={user.id}
                className="flex items-center justify-between rounded-3xl border border-[var(--color-border)] bg-white/75 px-4 py-4"
              >
                <div>
                  <p className="font-medium">{user.name}</p>
                  <p className="mt-1 text-sm text-[var(--color-muted)]">{user.email}</p>
                </div>
                <StatusBadge tone="neutral" label={user.role} />
              </div>
            ))}
          </div>
        </section>
      </div>
    </div>
  );
}
