"use client";
export const dynamic = "force-dynamic";

import { cn } from "@/lib/utils";
import { LoaderCircle, Save, Eye, EyeOff, CheckCircle2, AlertCircle } from "lucide-react";
import { useEffect, useState, useCallback } from "react";
import { useForm } from "react-hook-form";
import { PanelSkeleton } from "@/components/ui/panel-skeleton";
import { StatusBadge } from "@/components/ui/status-badge";
import { PageErrorBoundary, ErrorBoundary } from "@/components/ui/error-boundary";

import { useSettings, useSaveSettings } from "@/hooks/use-reddit-crawler";
import type { SettingsPayload } from "@/lib/types";
import { kvToSettings, settingsToKVPayload } from "@/lib/types";

const LLM_PROVIDERS = ["ollama", "openai", "claude"] as const;
type LlmProvider = (typeof LLM_PROVIDERS)[number];

// Fallback defaults to use before data arrives
const FALLBACK: SettingsPayload = {
  llmSettings: { provider: "ollama", model: "", apiKey: "" },
  proxySettings: { enabled: false, host: "", port: 8080, authUsername: "", authPassword: "" },
  crawlerDefaults: { defaultSubreddit: "machinelearning" },
};

export default function SettingsPage() {
  const [activeTab, setActiveTab] = useState<"llm" | "proxy" | "crawler">("llm");
  const settingsQuery = useSettings();
  const saveMutation = useSaveSettings();

  // Convert backend KV response → flat SettingsPayload (or fallback)
  const payload: SettingsPayload = settingsQuery.data ? kvToSettings(settingsQuery.data) : FALLBACK;

  const form = useForm<SettingsPayload>({ defaultValues: payload });

  // Sync form when data arrives
  useEffect(() => {
    if (settingsQuery.data) {
      form.reset(kvToSettings(settingsQuery.data));
    }
  }, [settingsQuery.data]);

  const handleSave = useCallback(async () => {
    await saveMutation.mutateAsync(form.getValues());
  }, [form, saveMutation]);

  return (
    <PageErrorBoundary>
      <div className="flex w-full flex-col gap-3 min-w-0">
        {/* Header */}
        <section className="panel-sq-dense flex items-center justify-between flex-wrap gap-2">
          <span className="section-label block mb-0.5">Settings</span>
          <StatusBadge
            tone={saveMutation.isSuccess ? "success" : saveMutation.isError ? "error" : "neutral"}
            label={saveMutation.isSuccess ? "Saved" : saveMutation.isError ? "Error" : "Draft"}
          />
        </section>

        {settingsQuery.isLoading ? (
          <ErrorBoundary><PanelSkeleton className="h-48" /></ErrorBoundary>
        ) : (
          <>
            {/* Tab bar */}
            <section className="panel-sq-dense p-1 flex gap-1">
              {[
                ["llm", "LLM Configuration"],
                ["proxy", "Proxy Settings"],
                ["crawler", "Crawler Defaults"],
              ].map(([key, label]) => (
                <button
                  key={key}
                  type="button"
                  onClick={() => setActiveTab(key as typeof activeTab)}
                  className={cn(
                    "px-4 py-2 text-xs rounded transition-colors",
                    activeTab === key
                      ? "bg-accent-primary/20 text-accent-primary font-semibold"
                      : "text-fg-muted hover:bg-surface-dark",
                  )}
                >
                  {label}
                </button>
              ))}
            </section>

            {/* Main content area */}
            <section className="panel-sq-dense p-4 flex flex-col gap-5 max-w-2xl">
              {/* ─── LLM Configuration Tab ─── */}
              {activeTab === "llm" && (
                <fieldset className="flex flex-col gap-4">
                  <legend className="text-sm font-semibold text-fg-primary mb-1">LLM / AI Provider</legend>
                  <p className="text-xs text-fg-muted">Configure the local or remote LLM used for analysis, extraction, and summarization.</p>{" "}

                  {/* Provider Select */}                 <label className="flex flex-col gap-1.5">                    <span className="text-sm font-medium">Provider</span>
                    <select
                      value={form.watch("llmSettings.provider") || "ollama"}
                      onChange={(e) => form.setValue("llmSettings.provider", e?.target?.value as LlmProvider, { shouldDirty: true })}
                      disabled={saveMutation.isPending}
                      className="form-input"
                    >                      {LLM_PROVIDERS.map((p) => (                        <option key={p} value={p}>{p.charAt(0).toUpperCase() + p.slice(1)}</option>
                      ))}                    </select>                  </label>

                  {/* Model Name */}                  <label className="flex flex-col gap-1.5">
                    <span className="text-sm font-medium">Model Name</span>                    <input                      type="text"
                      value={form.watch("llmSettings.model") || ""}
                      onChange={(e) => form.setValue("llmSettings.model", e?.target?.value ?? "", { shouldDirty: true })}
                      placeholder="e.g. qwen3.6:35b"
                      disabled={saveMutation.isPending}
                      className="form-input"                    />                  </label>

                  {/* API Key */}                  <ApiKeyField                    label="API Key (if required)"
                    value={form.watch("llmSettings.apiKey") || ""}                    onChange={(v) => form.setValue("llmSettings.apiKey", v, { shouldDirty: true })}
                    disabled={saveMutation.isPending}
                  />

                  {/* Save button */}
                  <button                    type="button"
                    onClick={handleSave}
                    disabled={saveMutation.isPending}                    className="flex items-center gap-2 bg-accent-primary text-white px-4 py-2 rounded-xl text-sm font-medium disabled:opacity-60 self-start"
                  >
                    {saveMutation.isPending ? <LoaderCircle className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />} Save LLM Settings                  </button>

                  {/* Success / Error toast */}                  {saveMutation.isSuccess && (                    <div className="flex items-center gap-1.5 text-sm text-emerald-500">
                      <CheckCircle2 className="h-4 w-4" /> Settings saved successfully.
                    </div>                  )}                  {saveMutation.isError && (                    <div className="flex items-center gap-1.5 text-sm text-red-500">                      <AlertCircle className="h-4 w-4" /> Failed to save settings — check your server connection.

                    </div>
                  )}                </fieldset>              )}

              {/* ─── Proxy Settings Tab ─── */}
              {activeTab === "proxy" && (
                <fieldset className="flex flex-col gap-4">
                  <legend className="text-sm font-semibold text-fg-primary mb-1">Proxy Configuration</legend>
                  <p className="text-xs text-fg-muted">Useful for scraping through a proxy server or for API access control.</p>

                  {/* Enabled Toggle */}
                  <label className="flex items-center gap-3 cursor-pointer select-none">                    <div className="relative">                      <input                        type="checkbox"
                        checked={form.watch("proxySettings.enabled") || false}
                        onChange={(e: any) => form.setValue("proxySettings.enabled", e?.target?.checked ?? false, { shouldDirty: true })}
                        className="sr-only"                        disabled={saveMutation.isPending}                      />
                      <span
                        className={cn(                          "block w-10 h-5 rounded-full transition-colors",
                          form.watch("proxySettings.enabled") ? "bg-accent-primary" : "bg-fg-muted/30",
                        )}                        style={{ display: "block" }}                      >                        <span
                          className={cn(                            "block w-4 h-4 rounded-full bg-white shadow translate-x-1 transition-transform mt-0.5 ml-0.5",
                            form.watch("proxySettings.enabled") && "translate-x-5",

                          )}                          style={{ display: "block" }}                        />                      </span>                    </div>
                    <span className="text-sm font-medium">Enable Proxy</span>
                  </label>

                  {/* Host */}
                  <label className="flex flex-col gap-1.5">                    <span className="text-sm font-medium">Host</span>                    <input
                      type="text"
                      value={form.watch("proxySettings.host") || ""}
                      onChange={(e: any) => form.setValue("proxySettings.host", e?.target?.value ?? "", { shouldDirty: true })}
                      placeholder="e.g. 192.168.100.5"
                      disabled={saveMutation.isPending || !form.watch("proxySettings.enabled")}
                      className="form-input"                    />
                  </label>

                  {/* Port */}
                  <label className="flex flex-col gap-1.5">
                    <span className="text-sm font-medium">Port</span>                    <input                      type="number"
                      value={form.watch("proxySettings.port") || 8080}                      onChange={(e: any) => form.setValue("proxySettings.port", Number(e?.target?.value ?? 8080), { shouldDirty: true })}
                      min={1}                      max={65535}
                      disabled={saveMutation.isPending || !form.watch("proxySettings.enabled")}
                      className="form-input"                    />                  </label>

                  {/* Auth Username */}
                  <ApiKeyField                    label="Auth Username (optional)"                    value={form.watch("proxySettings.authUsername") || ""}
                    onChange={(v) => form.setValue("proxySettings.authUsername", v, { shouldDirty: true })}
                    disabled={saveMutation.isPending || !form.watch("proxySettings.enabled")}                  />

                  {/* Auth Password */}
                  <ApiKeyField                    label="Auth Password (optional)"
                    value={form.watch("proxySettings.authPassword") || ""}                    onChange={(v) => form.setValue("proxySettings.authPassword", v, { shouldDirty: true })}
                    disabled={saveMutation.isPending || !form.watch("proxySettings.enabled")}                  />

                  {/* Save button */}
                  <button                    type="button"
                    onClick={handleSave}
                    disabled={saveMutation.isPending}                    className="flex items-center gap-2 bg-accent-primary text-white px-4 py-2 rounded-xl text-sm font-medium disabled:opacity-60 self-start mt-1"
                  >                    {saveMutation.isPending ? <LoaderCircle className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />} Save Proxy Settings
                  </button>

                  {saveMutation.isSuccess && (                    <div className="flex items-center gap-1.5 text-sm text-emerald-500">                      <CheckCircle2 className="h-4 w-4" /> Proxy settings saved successfully.
                    </div>                  )}                  {saveMutation.isError && (                    <div className="flex items-center gap-1.5 text-sm text-red-500">                      <AlertCircle className="h-4 w-4" /> Failed to save proxy settings.
                    </div>                  )}
                </fieldset>
              )}

              {/* ─── Crawler Defaults Tab ─── */}              {activeTab === "crawler" && (
                <fieldset className="flex flex-col gap-4">
                  <legend className="text-sm font-semibold text-fg-primary mb-1">Crawler Defaults</legend>                  <p className="text-xs text-fg-muted">Default subreddit and other crawler startup values.</p>

                  {/* Default Subreddit */}
                  <label className="flex flex-col gap-1.5">                    <span className="text-sm font-medium">Default Subreddit</span>                    <input                      type="text"
                      value={form.watch("crawlerDefaults.defaultSubreddit") || "machinelearning"}
                      onChange={(e: any) => form.setValue("crawlerDefaults.defaultSubreddit", e?.target?.value ?? "machinelearning", { shouldDirty: true })}                      placeholder="e.g. machinelearning, golang, rust"
                      disabled={saveMutation.isPending}
                      className="form-input"                    />                  </label>

                  {/* Save button */}                  <button                    type="button"
                    onClick={handleSave}
                    disabled={saveMutation.isPending}
                    className="flex items-center gap-2 bg-accent-primary text-white px-4 py-2 rounded-xl text-sm font-medium disabled:opacity-60 self-start mt-1"                  >
                    {saveMutation.isPending ? <LoaderCircle className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />} Save Crawler Defaults                  </button>

                  {saveMutation.isSuccess && (                    <div className="flex items-center gap-1.5 text-sm text-emerald-500">                      <CheckCircle2 className="h-4 w-4" /> Crawler defaults saved.
                    </div>
                  )}                  {saveMutation.isError && (
                    <div className="flex items-center gap-1.5 text-sm text-red-500">                      <AlertCircle className="h-4 w-4" /> Failed to save crawler defaults.
                    </div>
                  )}                </fieldset>              )}

              {/* ─── Save All button (bottom) ─── */}
              <div className="flex items-center justify-end gap-3 pt-2 border-t">                {saveMutation.isSuccess && (                  <span className="text-sm text-emerald-500">✓ Saved</span>                )}                {saveMutation.isError && (
                  <span className="text-sm text-red-400">Failed to save</span>                )}
                <button                  type="button"                  onClick={handleSave}
                  disabled={saveMutation.isPending || !form.formState.isDirty}
                  className="flex items-center gap-2 bg-accent-primary text-white px-5 py-2 rounded-xl text-sm font-medium disabled:opacity-40 disabled:cursor-not-allowed hover:bg-accent-primary/90"                >
                  {saveMutation.isPending ? <LoaderCircle className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />} Save All Settings</button>
              </div>
            </section>
          </>

        )}
      </div>
    </PageErrorBoundary>
  );
}

// ─── Helper: small component for password/api-key inputs ───

function ApiKeyField({ label, value, onChange, disabled }: {
  label: string; value: string; onChange: (v: string) => void; disabled?: boolean;
}) {
  const [visible, setVisible] = useState(false);
  return (
    <label className="flex flex-col gap-1.5 relative">
      <span className="text-sm font-medium">{label}</span>
      <div className="relative">
        <input
          type={visible ? "text" : "password"}
          value={value}
          onChange={(e: any) => onChange(e?.target?.value ?? "")}
          disabled={disabled}
          placeholder="••••••••"
          className="form-input pr-10"
        />
        <button
          type="button"
          onClick={() => setVisible(!visible)}
          disabled={disabled}
          className="absolute right-2 top-1/2 -translate-y-1/2 text-fg-muted hover:text-fg-primary p-1"
        >
          {visible ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
        </button>
      </div>
    </label>
  );
}
