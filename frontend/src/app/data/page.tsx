import { AppShell } from "@/components/dashboard/AppShell";
import { DataTable } from "@/components/dashboard/DataTable";
import { Panel } from "@/components/dashboard/Panel";

export default function DataPage() {
  return (
    <AppShell
      title="Data Explorer"
      description="Wide table layout with reduced chrome so more source records stay visible without sacrificing readability."
    >
      <main className="grid gap-6">
        <Panel title="Indexed Sources" eyebrow="Freshest activity">
          <div className="mb-4 flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
            <div className="flex flex-1 gap-3">
              <input
                aria-label="Search subreddits"
                placeholder="Search subreddit, keyword, author"
                className="w-full rounded-2xl border border-white/10 bg-slate-950/60 px-4 py-3 text-sm text-white outline-none transition placeholder:text-slate-500 focus:border-white/20"
              />
              <button
                type="button"
                className="rounded-2xl border border-white/10 bg-white/5 px-4 py-3 text-sm font-medium text-white transition hover:bg-white/10"
              >
                Filters
              </button>
            </div>
            <p className="text-sm text-slate-400">4 collections shown, synced in the last 5 minutes.</p>
          </div>
          <DataTable />
        </Panel>
      </main>
    </AppShell>
  );
}
