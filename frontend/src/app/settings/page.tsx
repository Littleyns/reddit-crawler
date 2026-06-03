"use client";

import { cn } from "@/lib/utils";
import { zodResolver } from "@hookform/resolvers/zod";
import { LoaderCircle, Save } from "lucide-react";
import { useEffect } from "react";
import { useForm, useWatch } from "react-hook-form";
import { z } from "zod";
import { ApiKeyInput } from "@/components/api-key-input";
import { AuthForm } from "@/components/auth-form";
import { StatusBadge } from "@/components/ui/status-badge";
import { useSaveSettings, useSettings } from "@/hooks/use-reddit-crawler";
import type { UserSummary, SettingsPayload } from "@/lib/types";

const formSchema = z.object({
  apiKey: z.string().min(8, "API key is required"),
  defaultSubreddit: z.string().min(2),
  defaultDepth: z.number(),
  defaultLimit: z.number(),
  autoExport: z.boolean(),
  exportFormat: z.enum(["csv", "json"]),
  sessionTimeoutMinutes: z.number(),
  users: z.array(z.object({ id: z.string(), name: z.string(), email: z.string().email(), role: z.enum(["admin", "analyst", "viewer"]) })),
});

// Helper to coerce the zod output (strings from HTML inputs) into SettingsPayload
function toPayload(data: z.output<typeof formSchema>): SettingsPayload {
  return data as unknown as SettingsPayload;
}

const tabDefs = [
  { key: "credentials" as const, label: "Credentials & Defaults" },
  { key: "users" as const, label: "Users" },
] as const;

export default function SettingsPage() {
  const settingsQuery = useSettings();
  const saveMutation = useSaveSettings();

  const formValues = settingsQuery.data ?? ({
    apiKey: "",
    defaultSubreddit: "",
    defaultDepth: 4,
    defaultLimit: 250,
    autoExport: false,
    exportFormat: "json" as const,
    sessionTimeoutMinutes: 30,
    users: [],
  } as SettingsPayload);

  type FormInput = z.infer<typeof formSchema>;
  const form = useForm<FormInput & { users: UserSummary[] }>({
    resolver: zodResolver(formSchema as any),
    defaultValues: formValues,
  });

  const apiKey = useWatch({ control: form.control, name: "apiKey" });

  useEffect(() => {
    if (settingsQuery.data) form.reset(settingsQuery.data);
  }, [form, settingsQuery.data]);

  return (
    <div className="flex w-full flex-col gap-3 min-w-0">
      {/* Top bar */}
      <section className="panel-sq-dense flex items-center justify-between flex-wrap gap-2 rounded-none overflow-hidden border border-[var(--color-border)] bg-[var(--color-surface-mid)]">
        <div>
          <span className="section-label block mb-0.5">Settings</span>
          <h2 className="text-sm font-semibold tracking-tight text-[var(--color-fg-primary)]">API credentials, crawl defaults, and user access.</h2>
        </div>
        <StatusBadge tone={saveMutation.isSuccess ? "success" : "neutral"} label={saveMutation.isSuccess ? "Saved" : "Draft"} />
      </section>

      {/* Tab bar */}
      <section className="flex gap-0 border-b border-[var(--color-border)] bg-[var(--color-surface-low)] -mx-px px-px overflow-x-auto">
        {tabDefs.map((t) => (
          <button key={t.key} type="button"
            className={cn(
              "text-[10px] font-semibold uppercase tracking-wider py-2 px-4 text-[var(--color-fg-muted)] border-b-[2px] border-transparent hover:text-[var(--color-fg-secondary)] transition-colors whitespace-nowrap rounded-none",
            )}
          >
            {t.label}
          </button>
        ))}
      </section>

      {/* Main grid */}
      <div className="dense-grid xl:grid-cols-[1fr_280px]">
        {/* Left: Settings form */}
        <section className="panel-sq-dense flex flex-col gap-4 rounded-none overflow-hidden border border-[var(--color-border)] bg-[var(--color-surface-mid)] p-3 sm:p-4">
          <span className="section-label block mb-0.5">Credentials & Scrape Defaults</span>

          <form className="flex flex-col gap-4" onSubmit={form.handleSubmit((data) => saveMutation.mutate(toPayload(data)))}>
            {/* API Key */}
            <label className="flex flex-col gap-1">
              <span className="text-[9px] font-semibold uppercase tracking-wider text-[var(--color-fg-muted)]">Reddit API Key</span>
              <ApiKeyInput value={apiKey ?? ""} onChange={(val) => form.setValue("apiKey", val as any)} />
              {form.formState.errors.apiKey && (
                <span className="text-[9px] text-[var(--color-danger-text)]">{form.formState.errors.apiKey.message}</span>
              )}
            </label>

            {/* Row 1: Subreddit, Timeout, Depth */}
            <div className="dense-grid md:grid-cols-3">
              <label className="flex flex-col gap-1">
                <span className="text-[9px] font-semibold uppercase tracking-wider text-[var(--color-fg-muted)]">Default Subreddit</span>
                <input {...form.register("defaultSubreddit")} className="input-sq rounded-none" />
              </label>
              <label className="flex flex-col gap-1">
                <span className="text-[9px] font-semibold uppercase tracking-wider text-[var(--color-fg-muted)]">Session Timeout (min)</span>
                <input type="number" step="1" {...form.register("sessionTimeoutMinutes")} className="input-sq rounded-none" />
              </label>
              <label className="flex flex-col gap-1">
                <span className="text-[9px] font-semibold uppercase tracking-wider text-[var(--color-fg-muted)]">Default Depth</span>
                <input type="number" step="1" {...form.register("defaultDepth")} className="input-sq rounded-none" />
              </label>
            </div>

            {/* Row 2: Limit, Export Format, Checkbox */}
            <div className="dense-grid md:grid-cols-3">
              <label className="flex flex-col gap-1">
                <span className="text-[9px] font-semibold uppercase tracking-wider text-[var(--color-fg-muted)]">Default Limit</span>
                <input type="number" step="1" {...form.register("defaultLimit")} className="input-sq rounded-none" />
              </label>
              <label className="flex flex-col gap-1">
                <span className="text-[9px] font-semibold uppercase tracking-wider text-[var(--color-fg-muted)]">Export Format</span>
                <select {...form.register("exportFormat")} className="input-sq rounded-none bg-[var(--color-surface-high)] text-[var(--color-fg-primary)]">
                  <option value="json">JSON</option>
                  <option value="csv">CSV</option>
                </select>
              </label>
              <div className="flex items-end pb-0.5">
                <label className="flex items-center gap-1.5 text-[11px] text-[var(--color-fg-muted)] cursor-pointer bg-[var(--color-surface-high)] border border-[var(--color-border)] px-2 py-[5px] w-full hover:border-[var(--color-border-muted)] transition-colors rounded-none">
                  <input type="checkbox" {...form.register("autoExport")} className="accent-accent-primary h-3 w-3 rounded-none" />
                  <span>Auto-export after runs</span>
                </label>
              </div>
            </div>

            {/* Save button */}
            <div className="flex items-center gap-2 pt-1">
              <button type="submit" disabled={saveMutation.isPending} className="btn-sq btn-sq-primary rounded-none">
                {saveMutation.isPending ? (
                  <>
                    <LoaderCircle className="h-3.5 w-3.5 animate-spin mr-1" /> Saving…
                  </>
                ) : (
                  <>
                    <Save className="h-3.5 w-3.5 mr-1" /> Save Settings
                  </>
                )}
              </button>
            </div>
          </form>
        </section>

        {/* Right sidebar */}
        <aside className="flex flex-col gap-3 h-fit">
          <AuthForm />
          <section className="panel-sq-dense rounded-none overflow-hidden border border-[var(--color-border)] bg-[var(--color-surface-mid)] px-3 py-2">
            <span className="section-label block mb-1.5">Active Users</span>
            <div className="flex flex-col divide-y divide-[var(--color-border)] -mx-px px-px">
              {settingsQuery.data?.users.map((user) => (
                <div key={user.id} className="flex items-center gap-2 py-[4px]">
                  <div className="flex h-6 w-6 shrink-0 items-center justify-center bg-[var(--color-accent)]/10 border border-[var(--color-border-muted)] text-[8px] font-bold text-[var(--color-accent-text)] rounded-none">
                    {user.name.split(" ").map((n) => n[0]).join("").slice(0, 2).toUpperCase()}
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="text-[11px] font-medium leading-tight truncate text-[var(--color-fg-primary)]">{user.name}</p>
                    <p className="text-[9px] text-[var(--color-fg-muted)] tabular-nums whitespace-nowrap">{user.email}</p>
                  </div>
                  <StatusBadge tone="neutral" label={user.role} />
                </div>
              ))}
            </div>
          </section>
        </aside>
      </div>
    </div>
  );
}
