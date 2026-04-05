import { AppShell } from "@/components/dashboard/AppShell";
import { Panel } from "@/components/dashboard/Panel";

const actions = [
  { label: "Start crawl", detail: "Launch subreddit workers with the current priority map." },
  { label: "Pause crawl", detail: "Drain queues safely without dropping in-flight jobs." },
  { label: "Export JSON", detail: "Package the active segment into a structured archive." },
  { label: "Export CSV", detail: "Generate analyst-friendly extracts for offline review." }
];

export default function ControlsPage() {
  return (
    <AppShell
      title="Controls"
      description="Operator-focused commands grouped into large targets with tighter panel spacing for faster execution."
    >
      <main className="grid gap-6 xl:grid-cols-[1.1fr_0.9fr]">
        <Panel title="Crawler Commands" eyebrow="Primary actions">
          <div className="grid gap-3 sm:grid-cols-2">
            {actions.map((action, index) => (
              <button
                key={action.label}
                type="button"
                className={`rounded-[1.4rem] border px-4 py-4 text-left transition ${
                  index === 0
                    ? "border-sky-400/40 bg-sky-400/10 hover:bg-sky-400/15"
                    : "border-white/10 bg-white/5 hover:bg-white/10"
                }`}
              >
                <p className="text-base font-semibold text-white">{action.label}</p>
                <p className="mt-2 text-sm leading-6 text-slate-300">{action.detail}</p>
              </button>
            ))}
          </div>
        </Panel>
        <Panel title="Runbook Snapshot" eyebrow="Operational guidance">
          <div className="space-y-3 text-sm leading-6 text-slate-300">
            <p>Use the start action after confirming API health, database latency, and subreddit quotas.</p>
            <p>Pause before config edits to avoid partial exports or mismatched crawler assignments.</p>
            <p>Generate exports from stable windows only; the UI is compacted to keep these checks visible on one screen.</p>
          </div>
        </Panel>
      </main>
    </AppShell>
  );
}
