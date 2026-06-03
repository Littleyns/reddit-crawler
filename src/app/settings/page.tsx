"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { LoaderCircle, Save } from "lucide-react";
import { useEffect } from "react";
import { useForm, useWatch } from "react-hook-form";
import { z } from "zod";
import { ApiKeyInput } from "@/components/api-key-input";
import { AuthForm } from "@/components/auth-form";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { FormActions, FormGrid, FormSection } from "@/components/ui/form";
import { CheckboxField, Input, Select } from "@/components/ui/input";
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
      <Card variant="spotlight" className="rounded-[calc(var(--radius-xl)+0.3rem)]">
        <CardHeader
          title="API credentials and scrape defaults"
          description="Settings now use the shared input, checkbox, button, and card primitives for a consistent operator surface."
          action={
            <StatusBadge
              tone={saveMutation.isSuccess ? "success" : "neutral"}
              label={saveMutation.isSuccess ? "saved" : "draft"}
            />
          }
        >
          <p className="text-xs uppercase tracking-[0.32em] text-[var(--ds-text-muted)]">Settings</p>
        </CardHeader>

        <CardContent>
          <form
            className="space-y-6"
            onSubmit={form.handleSubmit(async (values) => {
              await saveMutation.mutateAsync(values);
            })}
          >
            <FormSection title="Credentials">
              <ApiKeyInput
                value={apiKey ?? ""}
                onChange={(value) => form.setValue("apiKey", value)}
                error={form.formState.errors.apiKey?.message}
              />
            </FormSection>

            <FormSection title="Crawler defaults">
              <FormGrid>
                <Input label="Default subreddit" {...form.register("defaultSubreddit")} />
                <Input
                  label="Session timeout (minutes)"
                  type="number"
                  {...form.register("sessionTimeoutMinutes")}
                />
                <Input label="Default depth" type="number" {...form.register("defaultDepth")} />
                <Input label="Default limit" type="number" {...form.register("defaultLimit")} />
              </FormGrid>
            </FormSection>

            <FormSection title="Automation">
              <div className="grid gap-4 md:grid-cols-2">
                <CheckboxField
                  label="Auto export after successful runs"
                  description="Generates a file immediately when a crawl completes."
                  {...form.register("autoExport")}
                />
                <Select label="Export format" {...form.register("exportFormat")}>
                  <option value="json">JSON</option>
                  <option value="csv">CSV</option>
                </Select>
              </div>
            </FormSection>

            <FormActions>
              <Button
                type="submit"
                loading={saveMutation.isPending}
                leadingIcon={
                  saveMutation.isPending ? (
                    <LoaderCircle className="h-4 w-4 animate-spin" />
                  ) : (
                    <Save className="h-4 w-4" />
                  )
                }
              >
                Save settings
              </Button>
            </FormActions>
          </form>
        </CardContent>
      </Card>

      <div className="space-y-6">
        <AuthForm />

        <Card variant="outline" className="rounded-[calc(var(--radius-xl)+0.3rem)]">
          <p className="text-xs uppercase tracking-[0.28em] text-[var(--ds-text-muted)]">User Management</p>
          <h2 className="mt-3 text-2xl font-semibold">Current operators</h2>
          <div className="mt-6 space-y-3">
            {settingsQuery.data?.users.map((user) => (
              <div
                key={user.id}
                className="flex items-center justify-between rounded-[var(--radius-lg)] border border-[var(--ds-border-soft)] bg-[rgba(255,255,255,0.03)] px-4 py-4"
              >
                <div>
                  <p className="font-medium">{user.name}</p>
                  <p className="mt-1 text-sm text-[var(--ds-text-secondary)]">{user.email}</p>
                </div>
                <StatusBadge tone="neutral" label={user.role} />
              </div>
            ))}
          </div>
        </Card>
      </div>
    </div>
  );
}
