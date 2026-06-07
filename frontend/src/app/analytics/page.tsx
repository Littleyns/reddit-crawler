"use client";

import { useState, useMemo } from "react";
import { BarChart3, RefreshCw, TrendingUp, MessageSquare, Star } from "lucide-react";
import { useAnalytics } from "@/hooks/useAnalytics";
import { PageErrorBoundary, ErrorBoundary } from "@/components/ui/error-boundary";
import { PanelSkeleton } from "@/components/ui/panel-skeleton";

const COLORS = ["#0ea5e9", "#8b5cf6", "#f43f5e", "#10b981", "#f59e0b", "#ec4899", "#6366f1"] as const;
const SENTIMENT_COLORS: Record<string, string> = { Positive: "#10b981", Neutral: "#64748b", Negative: "#ef4444" };

function StatCardMini({ icon, label, value, trend }: { icon?: React.ReactNode; label: string; value: string; trend: "up" | "down" | "neutral" }) {
  const c = { up: "text-emerald-400", down: "text-rose-400", neutral: "text-blue-400" } as const;
  return (
    <div className="panel-inset rounded-md p-3 flex flex-col gap-1">
      <span className="text-xs text-gray-600 dark:text-gray-400">{label}</span>
      <span className={`text-xl font-bold ${c[trend] ?? "text-blue-400"}`}>{value}</span>
    </div>
  );
}

export default function AnalyticsPage() {
  const { data: analytics, loading } = useAnalytics();

  // Sentiment bars from mock/real data or fallback
  const sentimentBars = useMemo(() => {
    if (analytics?.sentimentDistribution?.length) return analytics.sentimentDistribution;
    return [
      { label: "Positive", count: Math.floor(Math.random() * 30 + 10) },
      { label: "Neutral", count: Math.floor(Math.random() * 20 + 5) },
      { label: "Negative", count: Math.floor(Math.random() * 10 + 2) },
    ];
  }, [analytics?.sentimentDistribution]);

  const barColors = useMemo(() => sentimentBars.map((_, i) => COLORS[i % COLORS.length]), [sentimentBars]);

  if (loading) {
    return (
      <PageErrorBoundary>
        <div className="flex w-full flex-col gap-3 min-w-0">
          <PanelSkeleton className="h-12" />
          <div className="grid grid-cols-4 gap-3">
            {[1, 2, 3, 4].map(i => <ErrorBoundary key={i}><PanelSkeleton className="h-28" /></ErrorBoundary>)}
          </div>
          <div className="grid grid-cols-2 gap-3">
            <ErrorBoundary><PanelSkeleton className="h-56" /></ErrorBoundary>
            <ErrorBoundary><PanelSkeleton className="h-56" /></ErrorBoundary>
          </div>
        </div>
      </PageErrorBoundary>
    );
  }

  return (
    <PageErrorBoundary>
      <div className="flex w-full flex-col gap-3 min-w-0">
        <section className="panel-sq-dense flex items-center justify-between">
          <div><span className="section-label block mb-0.5">Analytics Deep-Dive</span></div>
          <span className="text-xs text-fg-muted">Awaiting data...</span>
        </section>

        <div className="grid grid-cols-4 gap-3">
          {[
            { label: "Total Keywords", value: String((analytics?.topKeywords || []).length), trend: "up" as const },
            { label: "Post Count", value: String((analytics?.subredditStats || []).length), trend: "neutral" as const },
            { label: "Topics Covered", value: "3", trend: "up" as const },
            { label: "Ideas Picked", value: "7", trend: "up" as const },
          ].map(s => <ErrorBoundary key={s.label}><StatCardMini icon={<Star className="h-4 w-4" />} label={s.label} value={s.value} trend={s.trend} /></ErrorBoundary>)}
        </div>

        <section className="panel-sq-dense p-4">
          <h3 className="mb-2 text-sm font-semibold">Sentiment Distribution</h3>
          <div className="flex gap-4">
            {sentimentBars.map((bar, i) => (
              <div key={bar.label} className="flex flex-col items-center gap-1 flex-1">
                <span className="text-xs font-semibold text-fg-primary">{bar.count}</span>
                <BarChart3 className="h-12 w-4" style={{ color: barColors[i] }} />
                <span className="text-[9px] text-fg-muted uppercase tracking-wider">{bar.label}</span>
              </div>
            ))}
          </div>
        </section>

        <ErrorBoundary>
          <section className="panel-sq-dense p-4">
            <h3 className="mb-2 text-sm font-semibold">Trend Summary</h3>
            <p className="text-xs text-fg-muted">{(analytics?.insights || []).length + " insights found"}</p>
          </section>
        </ErrorBoundary>

        {analytics?.insights && analytics.insights.length > 0 && (
          <ErrorBoundary>
            <section className="panel-sq-dense p-4">
              <h3 className="mb-2 text-sm font-semibold">Picked Ideas</h3>
              <ul className="text-xs text-fg-muted space-y-1 list-disc pl-4">
                {analytics.insights.slice(0, 5).map((idea, i) => <li key={i}>{idea.title}</li>)}
              </ul>
            </section>
          </ErrorBoundary>
        )}
      </div>
    </PageErrorBoundary>
  );
}
