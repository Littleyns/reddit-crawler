"use client";
export const dynamic = "force-dynamic";

import { cn } from "@/lib/utils";
import { zodResolver } from "@hookform/resolvers/zod";
import { LoaderCircle, Save } from "lucide-react";
import { useEffect, useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { z } from "zod";
import { ApiKeyInput } from "@/components/api-key-input";
import { AuthForm } from "@/components/auth-form";
import { StatusBadge } from "@/components/ui/status-badge";
import { useSaveSettings, useSettings } from "@/hooks/use-reddit-crawler";
import { LLMConfigPanel } from "@/components/llm/llm-config-panel";
import type { UserSummary, SettingsPayload } from "@/lib/types";
import { PageErrorBoundary, ErrorBoundary } from "@/components/ui/error-boundary";
import { PanelSkeleton } from "@/components/ui/panel-skeleton";

const formSchema = z.object({
  apiKey: z.string().min(8, "API key is required"),
  defaultSubreddit: z.string().min(2),
  defaultDepth: z.number(),
  defaultLimit: z.number(),
  autoExport: z.boolean(),
  exportFormat: z.enum(["csv", "json"]),
  sessionTimeoutMinutes: z.number(),
});

export default function SettingsPage() {
  const [activeTab, setActiveTab] = useState<"llm-config" | "credentials" | "users">("credentials");
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
  } as SettingsPayload);

  type FormInput = z.infer<typeof formSchema>;
  const form = useForm<FormInput>({
    resolver: zodResolver(formSchema),
    defaultValues: formValues,
  });

  useEffect(() => { if (settingsQuery.data) form.reset(settingsQuery.data); }, [form, settingsQuery.data]);

  return (
    <PageErrorBoundary>
      <div className="flex w-full flex-col gap-3 min-w-0">
        <section className="panel-sq-dense flex items-center justify-between flex-wrap gap-2">
          <span className="section-label block mb-0.5">Settings</span>
          <StatusBadge tone={saveMutation.isSuccess ? "success" : "neutral"} label={saveMutation.isSuccess ? "Saved" : "Draft"} />
        </section>

        {settingsQuery.isLoading ? (
          <ErrorBoundary><PanelSkeleton className="h-48" /></ErrorBoundary>
        ) : (
          <>
            {/* Tab bar */}
            <section className="panel-sq-dense p-1 flex gap-1">
              {[["llm-config", "LLM Configuration"], ["credentials", "Credentials & Defaults"], ["users", "Users"]].map(([key, label]) => (
                <button type="button" key={key} onClick={() => setActiveTab(key as any)} className={cn("px-4 py-2 text-xs rounded transition-colors", activeTab === key ? "bg-accent-primary/20 text-accent-primary font-semibold" : "text-fg-muted hover:bg-surface-dark")}>{label}</button>
              ))}
            </section>

            <ErrorBoundary>
              {activeTab === "llm-config" && <LLMConfigPanel />}
              {activeTab === "credentials" && (
                <section className="panel-sq-dense p-4">
                  <form onSubmit={form.handleSubmit(() => {} )} className="flex flex-col gap-4 max-w-lg">
                    <ApiKeyInput value={String(form.watch("apiKey"))} onChange={(v) => form.setValue("apiKey", v)} provider="reddit" />
                    <select {...form.register("exportFormat")} className="form-input">{["csv", "json"].map(f => <option key={f} value={f}>{f}</option>)}</select>
                    <button type="submit" disabled={saveMutation.isPending} className="flex items-center gap-2 bg-accent-primary text-white px-4 py-2 rounded-xl text-sm font-medium disabled:opacity-60">
                      {saveMutation.isPending ? <LoaderCircle className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />} Save Settings
                    </button>
                  </form>
                </section>
              )}
              {activeTab === "users" && <section className="panel-sq-dense p-6 text-center"><p className="text-fg-muted">User management coming soon.</p></section>}
            </ErrorBoundary>
          </>
        )}
      </div>
    </PageErrorBoundary>
  );
}
