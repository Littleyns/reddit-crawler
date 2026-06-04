"use client";

import { useMemo, useState } from "react";
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip as RechartsTooltip,
  Legend,
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
  RadialBar,
  AreaChart,
  Area,
  Tooltip,
  LabelList,
} from "recharts";
import { Database, Lightbulb, TrendingUp, Star, Activity } from "lucide-react";

import { useHeatmap, useIdeas, useKeywords, useReport, useTrends } from "@/hooks/use-analytics";
import { Skeleton, ChartSkeleton, CardSkeleton, ListSkeleton } from "@/components/ui/skeleton";
import { cn, formatNumber } from "@/lib/utils";

// ---------------------------------------------------------------------------
// Category config
// ---------------------------------------------------------------------------

const CATEGORY_OPTIONS = [
  "web-dev",
  "mobile-app",
  "ai-ml",
  "devtools",
  "infrastructure",
  "education",
  "design",
  "data-science",
  "game-dev",
  "other",
] as const;

type CategoryOption = (typeof CATEGORY_OPTIONS)[number];

const CATEGORY_COLORS: Record<CategoryOption, string> = {
  "web-dev": "#3b82f6",
  "mobile-app": "#8b5cf6",
  "ai-ml": "#ec4899",
  "devtools": "#10b981",
  "infrastructure": "#f59e0b",
  "education": "#6366f1",
  "design": "#f472b6",
  "data-science": "#14b8a6",
  "game-dev": "#f97316",
  "other": "#6b7280",
};

const HEATMAP_COLORS = ["#22c55e", "#a1a1aa", "#ef4444"]; // green, gray, red

// Simple hue-based palette for the word cloud / keywords pie chart.
function huePalette(index: number, total: number) {
  const hue = ((360 / total) * index + 205) % 360;
  return `hsl(${hue}, 75%, 60%)`;
}

// ---------------------------------------------------------------------------
// Section A: Sentiment Analysis Charts (heatmap stacked bars)
// ---------------------------------------------------------------------------

function SentimentChart() {
  const { data: heatmap } = useHeatmap();

  if (!heatmap || heatmap.length === 0) {
    return (
      <div className="panel-sq-dense p-4 flex flex-col gap-3">
        <SectionHeader icon={Database} title="Sentiment Distribution" />
        <ChartSkeleton />
      </div>
    );
  }

  const chartData = heatmap.map((h) => ({
    subreddit: h.subreddit,
    positive: h.positive || 0,
    neutral: h.neutral || 0,
    negative: h.negative || 0,
  }));

  return (
    <div className="panel-sq-dense p-4 flex flex-col gap-3">
      <SectionHeader icon={Database} title="Sentiment Distribution" />
      <ResponsiveContainer width="100%" height={320}>
        <BarChart data={chartData} barGap={0}>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" vertical={false} />
          <XAxis dataKey="subreddit" tick={{ fontSize: 10, fill: "var(--color-fg-muted)" }} axisLine={{ stroke: "var(--color-border)" }} tickLine={false} />
          <YAxis tick={{ fontSize: 10, fill: "var(--color-fg-muted)" }} axisLine={false} tickLine={false} />
          <RechartsTooltip
            contentStyle={{ backgroundColor: "var(--color-surface-high)", border: "1px solid var(--color-border)", borderRadius: 0, color: "var(--color-fg-primary)", fontSize: 12 }}
            itemStyle={{ color: "var(--color-fg-primary)" }}
          />
          <Legend wrapperStyle={{ fontSize: 11 }} />
          <Bar dataKey="positive" stackId="sentiment" fill={HEATMAP_COLORS[0]} name="Positive" />
          <Bar dataKey="neutral" stackId="sentiment" fill={HEATMAP_COLORS[1]} name="Neutral" />
          <Bar dataKey="negative" stackId="sentiment" fill={HEATMAP_COLORS[2]} name="Negative" />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Section B: Keyword Frequency Word Cloud (PieChart + RadialBar)
// ---------------------------------------------------------------------------

function KeywordWordCloud() {
  const { data: keywords } = useKeywords(30);

  const pieData = useMemo(() => {
    if (!keywords || keywords.length === 0) return [];
    return keywords.map((kw, i) => ({ name: kw.keyword.length > 12 ? kw.keyword.slice(0, 12) + "…" : kw.keyword, value: kw.frequency, color: huePalette(i, keywords.length) }));
  }, [keywords]);

  if (!keywords || keywords.length === 0) {
    return (
      <div className="panel-sq-dense p-4 flex flex-col gap-3">
        <SectionHeader icon={Star} title="Keyword Frequency" />
        <ChartSkeleton />
      </div>
    );
  }

  return (
    <div className="panel-sq-dense p-4 flex flex-col gap-3">
      <SectionHeader icon={Star} title="Keyword Frequency" />
      <div className="grid grid-cols-1 xl:grid-cols-[1fr_280px] gap-4 items-start">
        {/* Pie chart */}
        <ResponsiveContainer width="100%" height={320}>
          <PieChart>
            <Pie
              data={pieData}
              cx="50%"
              cy="50%"
              innerRadius={60}
              outerRadius={140}
              paddingAngle={2}
              dataKey="value"
              animationDuration={600}
            >
              {pieData.map((entry, i) => (
                <Cell key={`cell-${i}`} fill={entry.color} stroke="var(--color-bg-base)" strokeWidth={1} />
              ))}
              <LabelList position="outside" fill="var(--color-fg-secondary)" fontSize={10} stroke="none" dataKey="name" />
            </Pie>
            <RechartsTooltip
              contentStyle={{ backgroundColor: "var(--color-surface-high)", border: "1px solid var(--color-border)", borderRadius: 0, color: "var(--color-fg-primary)", fontSize: 12 }}
              formatter={(_value: unknown) => [String(_value), "frequency"]}
            />
          </PieChart>
        </ResponsiveContainer>

        {/* Radial bar side panel */}
        <ResponsiveContainer width="100%" height={320}>
          <RadialBar cx="50%" cy="50%" innerRadius={40} outerRadius="90%" data={pieData.map((kw, i) => ({
            name: kw.value > 30 ? kw.name : `${kw.keyword}`,
            value: kw.value,
            fill: pieData[i].color,
          }))} startAngle={90} endAngle={-270} barSize={6} dataKey="value">
            <Legend iconType="circle" iconSize={6} label={{ fontSize: 10, fill: "var(--color-fg-secondary)" }} wrapperStyle={{ fontSize: 10 }} />
            <RechartsTooltip
              contentStyle={{ backgroundColor: "var(--color-surface-high)", border: "1px solid var(--color-border)", borderRadius: 0, color: "var(--color-fg-primary)", fontSize: 12 }}
            />
          </RadialBar>
        </ResponsiveContainer>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Section C: Subreddit Trends Line Chart
// ---------------------------------------------------------------------------

function TrendsLineChart() {
  const { data: trends } = useTrends();

  if (!trends || trends.length === 0) {
    return (
      <div className="panel-sq-dense p-4 flex flex-col gap-3">
        <SectionHeader icon={TrendingUp} title="Subreddit Engagement Trends" />
        <ChartSkeleton />
      </div>
    );
  }

  // Transform trends into series per subreddit, using engagementVelocity as the key y metric.
  const series = trends.map((t) => ({
    name: t.subreddit.slice(3), // strip "r/" prefix for display
    score: Math.round(t.avgScore * 10) / 10,
    velocity: Math.round(t.engagementVelocity * 10) / 10,
    comments: Math.round(t.avgComments * 10) / 10,
    totalPosts: t.totalPosts || 0,
  }));

  const colors = ["#3b82f6", "#a855f7", "#22c55e", "#f97316", "#ec4899"];

  return (
    <div className="panel-sq-dense p-4 flex flex-col gap-3">
      <SectionHeader icon={TrendingUp} title="Subreddit Engagement Trends" />
      <ResponsiveContainer width="100%" height={320}>
        <AreaChart data={series} margin={{ top: 10, right: 10, left: -25, bottom: 0 }}>
          <defs>
            <linearGradient id="gradAvgScore" x1="0" y1="0" x2="0" y2="1">
              <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.35} />
              <stop offset="95%" stopColor="#3b82f6" stopOpacity={0} />
            </linearGradient>
          </defs>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" vertical={false} />
          <XAxis dataKey="name" tick={{ fontSize: 10, fill: "var(--color-fg-muted)" }} axisLine={false} tickLine={false} />
          <YAxis tick={{ fontSize: 10, fill: "var(--color-fg-muted)" }} axisLine={false} tickLine={false} />
          <Tooltip contentStyle={{ backgroundColor: "var(--color-surface-high)", border: "1px solid var(--color-border)", borderRadius: 0, color: "var(--color-fg-primary)", fontSize: 12 }} />
          <Legend wrapperStyle={{ fontSize: 10 }} iconType="circle" iconSize={6} />
          <Area
            type="monotone"
            dataKey="velocity"
            stroke="#f59e0b"
            strokeWidth={2}
            fill="url(#gradAvgScore)"
            name="Engagement Velocity"
          />
          {series.map((item, i) => (
            <Area
              key={`area-${i}`}
              type="monotone"
              dataKey="score"
              stroke={colors[i % colors.length]}
              strokeWidth={2}
              fill="none"
              name={`${item.name}`}
              animationDuration={600}
            />
          ))}
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Section D: Insights Extractor Panel
// ---------------------------------------------------------------------------

const INSIGHTS_CATEGORIES = CATEGORY_OPTIONS;

function categoryLabel(cat: string) {
  return cat.split("-").map((w) => w.charAt(0).toUpperCase() + w.slice(1)).join(" ");
}

function InsightsExtractorPanel() {
  const [activeFilter, setActiveFilter] = useState<string>("all");
  const { data: ideas, isLoading } = useIdeas(activeFilter === "all" ? undefined : activeFilter);
  // Fetch full unfiltered count for stats row.
  const { data: allIdeas } = useIdeas();

  const categoryBreakdown = useMemo(() => {
    const map: Record<string, number> = {};
    (allIdeas || []).forEach((idea) => {
      const cat = idea.category || "other";
      map[cat] = (map[cat] ?? 0) + 1;
    });
    return map;
  }, [allIdeas]);

  const filteredIdeas = ideas || [];

  return (
    <div className="panel-sq-dense p-4 flex flex-col gap-3">
      {/* Header */}
      <div className="flex items-center justify-between">
        <SectionHeader icon={Lightbulb} title="Insights Extractor" />
        {isLoading && <span className="text-[10px] text-[var(--color-fg-muted)] animate-pulse">Loading…</span>}
      </div>

      {/* Summary stat row */}
      <div className="grid grid-cols-3 gap-2">
        <StatMini label="Total Ideas" value={String(allIdeas?.length ?? "?")} />
        {Object.entries(categoryBreakdown).slice(0, 5).map(([cat, count]) => (
          <StatMini key={cat} label={categoryLabel(cat)} value={String(count)} />
        ))}
      </div>

      {/* Category filter chips */}
      <div className="flex flex-wrap gap-1.5">
        {["all", ...INSIGHTS_CATEGORIES].map((cat) => (
          <button
            key={cat}
            onClick={() => setActiveFilter(cat)}
            className={cn(
              "px-2.5 py-[3px] text-[10px] font-medium transition-all hover:scale-[1.02]",
              activeFilter === cat
                ? cn("bg-[var(--color-accent)] text-white border border-transparent", CATEGORY_COLORS[cat as CategoryOption] !== undefined && "shadow-md")
                : "border border-[var(--color-border)] bg-[var(--color-surface-mid)] text-[var(--color-fg-muted)] hover:text-[var(--color-fg-primary)] hover:bg-[var(--color-surface-high)]",
            )}
          >
            {cat === "all" ? "All Categories" : categoryLabel(cat)}
          </button>
        ))}
      </div>

      {/* Ideas list */}
      <div className="flex flex-col gap-2 min-h-[200px]">
        {!isLoading && filteredIdeas.length === 0 && (
          <div className="flex items-center justify-center h-32 text-[var(--color-fg-muted)] text-xs">No ideas found.</div>
        )}
        {filteredIdeas.length > 0 ? (
          <div className="flex flex-col gap-1.5">
            {filteredIdeas.map((idea, i) => (
              <IdeaCard key={i} idea={idea} />
            ))}
          </div>
        ) : (
          <ListSkeleton lines={4} />
        )}
      </div>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Sub-components used across sections                                        */
/* -------------------------------------------------------------------------- */

function SectionHeader({ icon: Icon, title }: { icon: React.ElementType; title: string }) {
  return (
    <div className="flex items-center gap-2">
      <Icon className="h-4 w-4 text-[var(--color-accent-text)] shrink-0" />
      <span className="text-xs font-semibold tracking-tight text-[var(--color-fg-primary)]">{title}</span>
    </div>
  );
}

function StatMini({ label, value }: { label: string; value: string }) {
  return (
    <div className="bg-[var(--color-surface-mid)] border border-[var(--color-border)] px-2 py-1.5 text-center transition-colors hover:bg-[var(--color-surface-high)]">
      <p className="text-[9px] text-[var(--color-fg-muted)] uppercase tracking-wider font-medium truncate">{label}</p>
      <p className="text-sm font-bold tabular-nums leading-none mt-auto text-[var(--color-accent-text)]">{value}</p>
    </div>
  );
}

function IdeaCard({ idea }: { idea: { title: string; description: string; category: string } }) {
  const cat = idea.category || "other";
  const catColor = CATEGORY_COLORS[cat as CategoryOption] || "#6b7280";
  return (
    <article
      className="border border-[var(--color-border)] bg-[var(--color-surface-low)] p-2.5 transition-all hover:border-[var(--color-accent)/40] hover:bg-[var(--color-surface-high)]"
    >
      <div className="flex items-start gap-2">
        <Lightbulb className="h-3.5 w-3.5 text-[var(--color-warning-text)] shrink-0 mt-[1px]" />
        <div className="min-w-0 flex-1">
          <p className="text-xs font-semibold leading-tight truncate text-[var(--color-fg-primary)]">{idea.title}</p>
          {idea.description !== null && idea.description !== "" && (
            <p className="text-[11px] text-[var(--color-fg-muted)] mt-0.5 line-clamp-2">{idea.description}</p>
          )}
        </div>
      </div>
      <div className="flex items-center gap-1.5 mt-1.5">
        <span
          className="inline-flex items-center px-1.5 py-0.5 text-[9px] font-semibold uppercase tracking-wider"
          style={{ backgroundColor: catColor + "18", color: catColor, border: `1px solid ${catColor}40` }}
        >
          {categoryLabel(cat)}
        </span>
      </div>
    </article>
  );
}

// ---------------------------------------------------------------------------
// Section E: Quick Stats Summary Row (via report)
// ---------------------------------------------------------------------------

function QuickStatsRow() {
  const { data: report } = useReport();

  const summary = report?.sentimentSummary;

  if (!summary) return <CardSkeleton className="mb-2" />;

  const cards = [
    { label: "Total Analyzed", value: formatNumber(summary.totalCount), sub: `${summary.positivePercent.toFixed(0)}% of sentiments processed` },
    { label: "Positive", value: `${summary.positivePercent.toFixed(1)}%`, sub: `${formatNumber(summary.positiveCount)} analyses`, color: "#22c55e" },
    { label: "Neutral", value: `${summary.neutralPercent.toFixed(1)}%`, sub: `${formatNumber(summary.neutralCount)} analyses`, color: "#a1a1aa" },
    { label: "Negative", value: `${summary.negativePercent.toFixed(1)}%`, sub: `${formatNumber(summary.negativeCount)} analyses`, color: "#ef4444" },
  ];

  return (
    <div className="grid grid-cols-2 md:grid-cols-4 gap-2">
      {cards.map((c) => (
        <div
          key={c.label}
          className="panel-sq-dense p-3 flex flex-col justify-between transition-all hover:bg-[var(--color-surface-high)] cursor-default"
          style={{ borderLeft: `3px solid ${c.color}` }}
        >
          <span className="text-[10px] font-semibold tracking-[0.08em] uppercase text-[var(--color-fg-muted)]">{c.label}</span>
          <p className="text-xl font-bold tabular-nums leading-none mt-auto text-[var(--color-fg-primary)]">{c.value}</p>
          <p className="text-[10px] text-[var(--color-fg-muted)] mt-0.5 truncate" title={c.sub}>{c.sub}</p>
        </div>
      ))}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Page
// ---------------------------------------------------------------------------

export default function AnalyticsPage() {
  return (
    <div className="flex w-full flex-col gap-3 min-w-0">
      {/* E: Quick Stats */}
      <QuickStatsRow />

      {/* A + B: Charts row */}
      <div className="grid grid-cols-1 xl:grid-cols-[1fr_280px] gap-3 mb-4">
        <SentimentChart />
        {/* The second column gets the KeywordWordCloud which stacks below on small screens */}
      </div>

      <div className="grid grid-cols-1">
        <KeywordWordCloud />
      </div>

      {/* C: Trends row */}
      <TrendsLineChart />

      {/* D: Insights Extractor — full width panel */}
      <InsightsExtractorPanel />
    </div>
  );
}
