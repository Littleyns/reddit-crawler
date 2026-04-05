import { AppShell } from "@/components/dashboard/AppShell";
import { MiniChart } from "@/components/dashboard/MiniChart";
import { Panel } from "@/components/dashboard/Panel";
import { StatCard } from "@/components/dashboard/StatCard";
import { pipelineStages, statCards } from "@/lib/data";

export default function DashboardPage() {
  return (
    <AppShell
      title="Dashboard"
      description="Dense monitoring view optimized for analysts watching throughput, freshness, and incident signals."
    >
      <main className="grid gap-6">
        <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          {statCards.map((card) => (
            <StatCard key={card.label} {...card} />
          ))}
        </section>
        <section className="grid gap-6 xl:grid-cols-[1.3fr_0.9fr]">
          <Panel title="Ingestion Velocity" eyebrow="Real-time trend">
            <MiniChart />
          </Panel>
          <Panel title="Pipeline Balance" eyebrow="Throughput by stage">
            <div className="grid gap-3">
              {pipelineStages.map((stage) => (
                <div key={stage.name} className="rounded-[1.25rem] border border-white/5 bg-slate-950/50 px-4 py-4">
                  <div className="flex items-center justify-between">
                    <p className="font-medium text-white">{stage.name}</p>
                    <p className="text-lg font-semibold text-white">{stage.value}</p>
                  </div>
                  <p className="mt-1 text-sm text-slate-400">{stage.detail}</p>
                </div>
              ))}
            </div>
          </Panel>
        </section>
      </main>
    </AppShell>
  );
}
