import { AppShell } from "@/components/dashboard/AppShell";
import { Panel } from "@/components/dashboard/Panel";

export default function SettingsPage() {
  return (
    <AppShell
      title="Settings"
      description="Configuration panels tuned for production use with concise grouping and enough width for infrastructure details."
    >
      <main className="grid gap-6 xl:grid-cols-2">
        <Panel title="API Endpoint" eyebrow="Connectivity">
          <div className="space-y-4">
            <div className="space-y-2">
              <label className="text-sm font-medium text-slate-200" htmlFor="api-url">
                Backend URL
              </label>
              <input
                id="api-url"
                defaultValue="https://api.reddit-crawler.internal"
                className="w-full rounded-2xl border border-white/10 bg-slate-950/60 px-4 py-3 text-sm text-white outline-none transition focus:border-white/20"
              />
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium text-slate-200" htmlFor="refresh-rate">
                Refresh interval
              </label>
              <select
                id="refresh-rate"
                className="w-full rounded-2xl border border-white/10 bg-slate-950/60 px-4 py-3 text-sm text-white outline-none transition focus:border-white/20"
                defaultValue="15s"
              >
                <option>5s</option>
                <option>15s</option>
                <option>30s</option>
                <option>60s</option>
              </select>
            </div>
          </div>
        </Panel>
        <Panel title="Export Policy" eyebrow="Data handling">
          <div className="space-y-3 text-sm leading-6 text-slate-300">
            <p>Default package format: compressed JSON.</p>
            <p>Retention window: 30 days for raw payloads, 180 days for normalized summaries.</p>
            <p>Webhook delivery remains disabled until outbound signing keys are configured.</p>
            <button
              type="button"
              className="mt-2 rounded-2xl border border-white/10 bg-white text-sm font-medium text-slate-950 px-4 py-3 transition hover:bg-slate-200"
            >
              Save settings
            </button>
          </div>
        </Panel>
      </main>
    </AppShell>
  );
}
