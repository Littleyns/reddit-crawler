"use client";
export const dynamic = "force-dynamic";

import { BarChart3 } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import axios from "axios";
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, PieChart, Pie, Cell, LineChart, Line } from "recharts";
import { PageErrorBoundary } from "@/components/ui/error-boundary";
import { PanelSkeleton, GridSkeleton } from "@/components/ui/panel-skeleton";

const BACKEND = process.env.NEXT_PUBLIC_API_URL || "/api";

interface PieSlice { name: string; value: number; color: string }
interface InsightItem { title: string; subtitle: string; category: string; confidence: number }

function formatRelativeTime(ts?: string): string {
  if (!ts) return "—";
  const seconds = Math.floor((Date.now() - new Date(ts).getTime()) / 1000);
  if (seconds < 60) return seconds + "s ago";
  if (seconds < 3600) return Math.floor(seconds / 60) + "m ago";
  return "live";
}

function StatCardsRow(props: { analytics: Record<string, unknown>; loading: boolean }) {
  const a = props.analytics || {};
  const totalPosts = Number(a.totalPosts ?? 0);
  const totalComments = Number(a.totalComments ?? 0);
  const kwCount = (a as any).keywordCount ?? ((a as any).keywords?.length ?? 0);
  const topicsCount = (a as any).topicsCount ?? 0;
  const ideasCount = (a as any).ideasCount ?? 0;
  return (
    <div className="grid grid-cols-2 md:grid-cols-5 gap-3">
      {[
        { label: "Keywords", value: String(kwCount), trend: "from backend" },
        { label: "Posts", value: String(totalPosts), trend: totalPosts > 0 ? "real" : "—" },
        { label: "Topics", value: String(topicsCount), trend: topicsCount > 0 ? "collected" : "—" },
        { label: "Ideas", value: String(ideasCount), trend: ideasCount > 0 ? "extracted" : "—" },
        { label: "Last Run", value: formatRelativeTime((a as any)?.lastRun), trend: (a ? "live" : "offline") },
      ].map(s => (
        <div key={s.label} className="panel-inset rounded-md p-3 flex flex-col justify-between">
          <span className="text-xs text-fg-muted">{s.label}</span>
          <span className="text-xl font-bold">{s.value}</span>
          <span className="text-[10px] text-fg-muted mt-auto">{s.trend}</span>
        </div>
      ))}
    </div>
  );
}

function KeywordsChart(props: { keywords: Array<{ keyword: string; frequency: number }> }) {
  const COLORS = ["#0ea5e9", "#8b5cf6", "#f43f5e", "#10b981", "#f59e0b"];
  const data = (props.keywords || []).slice(0, 15);
  if (data.length === 0) return <PanelSkeleton className="h-72" />;
  return (
    <div className="panel-inset rounded-md p-4 min-h-[300px]">
      <span className="section-label block mb-2">Top Keywords by Frequency</span>
      <ResponsiveContainer width="100%" height={280}>
        <BarChart data={data} layout="vertical" margin={{ left: 0, right: 20 }}>
          <CartesianGrid strokeDasharray="3 3" opacity={0.15} />
          <XAxis type="number" hide />
          <YAxis dataKey="keyword" type="category" width={80} tick={{ fontSize: 11 }} />
          <Tooltip contentStyle={{ background: "#1e1e2e", border: "none", borderRadius: 8, fontSize: 12 }} labelFormatter={(l) => "Keyword: " + l} />
          <Bar dataKey="frequency" fill="#0ea5e9" radius={[0, 4, 4, 0]} barSize={20}>
            {data.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

function SentimentPie(props: { sentiments: Array<{ label: string; count: number }> }) {
  const COLORS = ["#10b981", "#64748b", "#f43f5e"];
  const sents = props.sentiments || [];
  if (sents.length === 0) return <PanelSkeleton className="h-72" />;
  return (
    <div className="panel-inset rounded-md p-4 min-h-[300px]">
      <span className="section-label block mb-2">Sentiment Distribution</span>
      <div className="grid grid-cols-2 gap-4 h-full">
        <ResponsiveContainer width="50%" height={280}>
          <PieChart>
            <Pie data={sents} dataKey="count" nameKey="label" cx="50%" cy="50%" outerRadius={90} label={false}>
              {sents.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}
            </Pie>
          </PieChart>
        </ResponsiveContainer>
        <div className="flex flex-col gap-2 py-6 justify-center">
          {sents.map((s, i) => (
            <div key={i} className="flex items-center gap-2 text-sm">
              <span className="inline-block w-3 h-3 rounded" style={{ backgroundColor: COLORS[i % COLORS.length] }} />
              <span className="text-fg-muted">{s.label}</span>
              <span className="font-bold ml-auto">{s.count}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

function ActivityChart(props: { activity: Array<{ date: string; posts: number; comments: number }> }) {
  const data = props.activity || [];
  if (data.length === 0) return <PanelSkeleton className="h-72" />;
  const last30 = data.slice(-30);
  return (
    <div className="panel-inset rounded-md p-4 min-h-[280px]">
      <span className="section-label block mb-2">Daily Activity (Last 30)</span>
      <ResponsiveContainer width="100%" height={260}>
        <LineChart data={last30} margin={{ left: 0, right: 20 }}>
          <CartesianGrid strokeDasharray="3 3" opacity={0.15} />
          <XAxis dataKey="date" tick={{ fontSize: 10 }} interval="preserveStartEnd" minTickGap={30} />
          <YAxis tick={{ fontSize: 10 }} />
          <Tooltip contentStyle={{ background: "#1e1e2e", border: "none", borderRadius: 8, fontSize: 12 }} />
          <Line type="monotone" dataKey="posts" stroke="#0ea5e9" strokeWidth={2} dot={false} name="Posts" />
          <Line type="monotone" dataKey="comments" stroke="#f43f5e" strokeWidth={2} dot={false} name="Comments" />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}

function SubredditTable(props: { subreddits: Array<{ subreddit: string; postCount: number; commentCount: number; sentimentScore: number }> }) {
  const subs = props.subreddits || [];
  if (subs.length === 0) return <PanelSkeleton className="h-64" />;
  return (
    <div className="panel-inset rounded-md p-4 min-h-[250px]">
      <span className="section-label block mb-2">Subreddit Breakdown</span>
      <div className="overflow-auto max-h-60">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-fg-muted text-left border-b border-border/50">
              <th className="py-2 font-medium">Subreddit</th>
              <th className="py-2 font-medium text-right">Posts</th>
              <th className="py-2 font-medium text-right">Comments</th>
              <th className="py-2 font-medium text-right">Sentiment</th>
            </tr>
          </thead>
          <tbody>
            {subs.map((sub, i) => {
              const c = sub.sentimentScore >= 0.3 ? "#10b981" : sub.sentimentScore <= -0.3 ? "#f43f5e" : "#64748b";
              return (
                <tr key={i} className="border-b border-border/30 hover:bg-white/5">
                  <td className="py-2 font-medium">{sub.subreddit}</td>
                  <td className="py-2 text-right">{sub.postCount}</td>
                  <td className="py-2 text-right">{sub.commentCount}</td>
                  <td className="py-2 text-right"><span className="font-bold" style={{ color: c }}>{sub.sentimentScore.toFixed(2)}</span></td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function InsightsTable(props: { insights: InsightItem[] | [] }) {
  const items = props.insights || [];
  if (items.length === 0) return <PanelSkeleton className="h-64" />;
  return (
    <div className="panel-inset rounded-md p-4 min-h-[250px]">
      <span className="section-label block mb-2">Extracted Ideas &amp; Topics</span>
      <div className="overflow-auto max-h-60">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-fg-muted text-left border-b border-border/50">
              <th className="py-2 font-medium">Title</th>
              <th className="py-2 font-medium text-right">Category</th>
              <th className="py-2 font-medium text-right">Confidence</th>
            </tr>
          </thead>
          <tbody>
            {items.slice(0, 15).map((ins, i) => {
              const c = ins.confidence >= 0.7 ? "#10b981" : ins.confidence >= 0.4 ? "#f59e0b" : "#64748b";
              return (
                <tr key={i} className="border-b border-border/30 hover:bg-white/5">
                  <td className="py-2 max-w-xs truncate">{ins.title}</td>
                  <td className="py-2 text-right"><span className="px-2 py-0.5 rounded-full text-[10px] bg-fg-muted/10 uppercase">{ins.category}</span></td>
                  <td className="py-2 text-right font-bold" style={{ color: c }}>{(ins.confidence * 100).toFixed(0)}%</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export default function AnalyticsPage() {
  // Removed hardcoded localhost — all fetches now use relative /api/... paths

  const { data: analytics, isLoading: loading } = useQuery({
    queryKey: ["analytics-real-snapshot"],
    queryFn: async () => {
      try {
        const res = await fetch(BACKEND + "/api/analytics/snapshot", { signal: AbortSignal.timeout(10_000) });
        return res.ok ? res.json() : {};
      } catch { return {}; }
    },
    staleTime: 30_000, retry: 2,
  });

  const { data: subreddits = [] } = useQuery({
    queryKey: ["analytics-subreddits"],
    queryFn: async () => {
      try { const res = await fetch("/api/data/subreddits"); return res.ok ? res.json() : []; } catch { return []; }
    }, staleTime: 120_000,
  });

  const { data: keywords = [] } = useQuery({
    queryKey: ["analytics-keywords"],
    queryFn: async () => {
      try { const res = await fetch("/api/analysis/keywords?topN=30"); return res.ok ? res.json() : []; } catch { return []; }
    }, staleTime: 120_000,
  });

  const { data: sentimentDist = [] } = useQuery({
    queryKey: ["analytics-sentiment-dist"],
    queryFn: async () => {
      try {
        const res = await fetch("/api/analysis/sentiment");
        if (!res.ok) return (analytics as any)?.sentimentDistribution || [];
        const items: Array<{ sentiment: string }> = await res.json();
        const agg: Record<string, number> = { positive: 0, neutral: 0, negative: 0 };
        for (const item of items) {
          const key = item.sentiment === "positive" ? "positive" : item.sentiment === "negative" ? "negative" : "neutral";
          agg[key] = (agg[key] || 0) + 1;
        }
        return Object.entries(agg).map(([label, count]) => ({ label, count })).filter(s => s.count > 0);
      } catch { return []; }
    }, staleTime: 120_000,
  });

  const activity = Array.isArray((analytics as any)?.dailyActivity) ? (analytics as any).dailyActivity : [];

  const { data: insights = [] } = useQuery({
    queryKey: ["analytics-insights"],
    queryFn: async () => {
      try {
        const [res1, res2] = await Promise.all([
          fetch("/api/analysis/insights").catch(() => null),
          fetch("/api/analysis/ideas").catch(() => null),
        ]);
        const arr1: Array<{ title: string; subtitle: string; category: string }> = res1?.ok ? await res1.json() : [];
        const arr2: Array<{ title: string; subtitle: string; category: string }> = res2?.ok ? await res2.json() : [];
        return [...arr1, ...arr2].map((item) => ({
          title: item.title || "Untitled idea",
          subtitle: item.subtitle || "",
          category: (item.category || "idea").toLowerCase(),
          confidence: 0.5,
        }));
      } catch { return []; }
    }, staleTime: 120_000,
  });

  if (loading) {
    return (
      <div className="flex w-full flex-col gap-3 p-4">
        <span className="section-label block mb-1">Analytics Deep-Dive</span>
        <GridSkeleton columns={4} rows={2} />
      </div>
    );
  }

  return (
    <PageErrorBoundary>
      <div className="flex w-full flex-col gap-3 p-4">
        <div className="flex items-center justify-between">
          <span className="section-label block mb-0">Analytics Deep-Dive — Live Data</span>
          <BarChart3 className="w-5 h-5 text-fg-muted" />
        </div>
        <StatCardsRow analytics={analytics || {}} loading={false} />
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
          <KeywordsChart keywords={keywords as any[]} />
          <SentimentPie sentiments={sentimentDist} />
        </div>
        <ActivityChart activity={activity} />
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
          <SubredditTable subreddits={subreddits as any[]} />
          <InsightsTable insights={insights as any} />
        </div>
        <div className="text-center text-[10px] text-fg-muted pt-2">
          Data refreshed every 30s from Reddit Crawler Backend API • Last query: {new Date().toLocaleTimeString()}
        </div>
      </div>
    </PageErrorBoundary>
  );
}
