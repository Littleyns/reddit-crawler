import { AppShell } from "@/components/dashboard/AppShell";
import { DataTable } from "@/components/dashboard/DataTable";
import { MiniChart } from "@/components/dashboard/MiniChart";
import { Panel } from "@/components/dashboard/Panel";
import { StatCard } from "@/components/dashboard/StatCard";
import { activityFeed, pipelineStages, statCards } from "@/lib/data";

export default function HomePage() {
  return (
    <AppShell
      title="Operational clarity for high-volume Reddit crawling."
      description="Slate-themed monitoring workspace with tighter spacing, full-width panels, and a production-ready information hierarchy for crawler operations."
    >
      <main className="grid gap-6">
        <section className="grid gap-4 xl:grid-cols-[1.4fr_0.9fr]">
          <div className="surface flex min-h-[320px] flex-col justify-between px-6 py-6">
            <div className="space-y-4">
              <span className="eyebrow">Live Control Plane</span>
              <div className="space-y-3">
                <h2 className="max-w-3xl text-4xl font-semibold tracking-tight text-white sm:text-5xl">
                  Crawl, enrich, export, and inspect without losing screen real estate.
                </h2>
                <p className="max-w-2xl text-sm leading-6 text-slate-300 sm:text-base">
                  The layout is stretched to full container width, high-signal panels stay above the fold,
                  and the slate palette keeps dense operational views readable over long sessions.
                </p>
              </div>
            </div>
            <div className="grid gap-3 pt-6 sm:grid-cols-3">
              {pipelineStages.slice(0, 3).map((stage) => (
                <div key={stage.name} className="rounded-[1.4rem] border border-white/10 bg-white/5 px-4 py-4">
                  <p className="text-xs uppercase tracking-[0.24em] text-slate-400">{stage.name}</p>
                  <p className="mt-3 text-2xl font-semibold text-white">{stage.value}</p>
                  <p className="mt-1 text-sm text-slate-400">{stage.detail}</p>
                </div>
              ))}
            </div>
          </div>
          <Panel title="Shift Brief" eyebrow="Current state">
            <div className="space-y-3">
              {activityFeed.map((item) => (
                <div key={item} className="rounded-[1.3rem] border border-white/5 bg-slate-950/50 px-4 py-4 text-sm text-slate-200">
                  {item}
                </div>
              ))}
            </div>
          </Panel>
        </section>

        <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          {statCards.map((card) => (
            <StatCard key={card.label} {...card} />
          ))}
        </section>

        <section className="grid gap-6 xl:grid-cols-[1.15fr_0.85fr]">
          <Panel title="Volume Trend" eyebrow="Last 12 sync windows">
            <MiniChart />
          </Panel>
          <Panel title="Pipeline Stages" eyebrow="Processed today">
            <div className="grid gap-3">
              {pipelineStages.map((stage) => (
                <div
                  key={stage.name}
                  className="flex items-center justify-between rounded-[1.3rem] border border-white/5 bg-slate-950/50 px-4 py-4"
                >
                  <div>
                    <p className="font-medium text-white">{stage.name}</p>
                    <p className="text-sm text-slate-400">{stage.detail}</p>
                  </div>
                  <p className="text-2xl font-semibold text-slate-100">{stage.value}</p>
                </div>
              ))}
            </div>
          </Panel>
        </section>

        <Panel title="Priority Subreddits" eyebrow="Top monitored sources">
          <DataTable />
        </Panel>
      </main>
    </AppShell>
  );
}
