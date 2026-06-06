"use client"

// src/app/analytics/page.tsx — Real-time analytics dashboard with recharts

import { useState, useEffect } from "react"
import { 
  LineChart, Line, BarChart, Bar, PieChart, Pie, Cell,
  XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer 
} from "recharts"
import type { SubredditStats, ThreadInsight, AnalyticsData } from "@/lib/mock-analytics"
import { generateMockAnalyticsData } from "@/lib/mock-analytics"
import { 
  BarChart3, LineChart as LineChartIcon, PieChart as PieChartIcon, 
  Search, RefreshCw, Download, Filter, Users, Globe, TrendingUp, MessageSquare,
  Target, Lightbulb, Briefcase
} from "lucide-react"

// Colors for charts
const COLORS = ['#0ea5e9', '#8b5cf6', '#f43f5e', '#10b981', '#f59e0b', '#ec4899', '#6366f1'] as const
const SENTIMENT_COLORS: Record<string, string> = {
  Positive: '#10b981',
  Neutral: '#64748b',
  Negative: '#ef4444'
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
    positive: h.positivePercent || 0,
    neutral: h.neutralPercent || 0,
    negative: h.negativePercent || 0,
    positiveCount: h.positive || 0,
    neutralCount: h.neutral || 0,
    negativeCount: h.negative || 0,
    total: h.total || 0,
  }));

  const customTooltip = ({ active, payload }: any) => {
    if (!active || !payload?.[0]) return null;
    const item = payload[0].payload as typeof chartData[0];
    return (
      <div className="panel-sq-dense p-3 text-xs border border-[var(--color-border)] bg-[var(--color-bg-base)] shadow-lg">
        <p className="font-semibold mb-1">{item.subreddit}</p>
        <div className="flex flex-col gap-0.5 font-mono tabular-nums">
          <span className="text-[#22c55e]">▲ Pos</span>
          <span>{item.positiveCount}/{item.total} ({item.positive.toFixed(1)}%)</span>
        </div>
        <div className="flex flex-col gap-0.5 font-mono tabular-nums">
          <span className="text-[#a1a1aa]">● Neu</span>
          <span>{item.neutralCount}/{item.total} ({item.neutral.toFixed(1)}%)</span>
        </div>
        <div className="flex flex-col gap-0.5 font-mono tabular-nums border-t border-[var(--color-border)] mt-1 pt-1">
          <span className="text-[#ef4444]">▼ Neg</span>
          <span>{item.negativeCount}/{item.total} ({item.negative.toFixed(1)}%)</span>
        </div>
      </div>
    );
  };

  return (
    <div className="panel-sq-dense p-4 flex flex-col gap-3">
      <div className="flex items-center justify-between">
        <SectionHeader icon={Database} title="Sentiment Distribution (% of total)" />
        <span className="text-[9px] text-[var(--color-fg-muted)]">Stacked to 100%</span>
      </div>
      <ResponsiveContainer width="100%" height={320}>
        <BarChart data={chartData} barGap={0}>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" vertical={false} />
          <XAxis dataKey="subreddit" tick={{ fontSize: 10, fill: "var(--color-fg-muted)" }} axisLine={{ stroke: "var(--color-border)" }} tickLine={false} />
          <YAxis tick={{ fontSize: 10, fill: "var(--color-fg-muted)" }} axisLine={false} tickLine={false} domain={[0, 100]} tickFormatter={(v: number) => `${v}%`} />
          <RechartsTooltip content={customTooltip} />
          <Legend wrapperStyle={{ fontSize: 11 }} formatter={() => null} />
          <Bar dataKey="positive" stackId="sentiment" fill="#22c55e" name="Positive" />
          <Bar dataKey="neutral" stackId="sentiment" fill="#a1a1aa" name="Neutral" />
          <Bar dataKey="negative" stackId="sentiment" fill="#ef4444" name="Negative" />
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

        {/* Keyword frequency list (side panel) */}
        <div className="flex flex-col gap-1.5 h-full overflow-auto">
          {(pieData || []).map((kw, i) => {
            const original = keywords[i];
            const displayWidth = pieData.length > 0 ? Math.min(pieData[i].value / (pieData[0].value || 1), 1) * 100 : 0;
            return (
              <div key={i} className="flex items-center gap-1.5 text-[10px]">
                {/* Color dot */}
                <span className="inline-block w-2 h-2 rounded-full shrink-0" style={{ backgroundColor: pieData[i].color }} />
                {/* Keyword name (full, shown) */}
                <span className="truncate font-mono tabular-nums min-w-0 flex-shrink text-[var(--color-fg-secondary)]">{original.keyword}</span>
                {/* Count + relative bar */}
                <div className="flex items-end gap-1 shrink-0 w-[60px]">
                  <span className="tabular-nums text-[9px] text-[var(--color-fg-muted)]">{pieData[i].value}</span>
                  <div className="h-1 w-full bg-[var(--color-border)] rounded">
                    <div className="h-full rounded transition-all" style={{ width: `${displayWidth}%`, backgroundColor: pieData[i].color }} />
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  )
}

function Badge({ children, color }: { children: React.ReactNode; color: 'emerald' | 'pink' | 'slate' | 'amber' | 'sky' }) {
  const colors = {
    emerald: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20',
    pink: 'bg-pink-500/10 text-pink-400 border-pink-500/20',
    slate: 'bg-slate-500/10 text-slate-400 border-slate-500/20',
    amber: 'bg-amber-500/10 text-amber-400 border-amber-500/20',
    sky: 'bg-sky-500/10 text-sky-400 border-sky-500/20',
  }
  return (
    <span className={`inline-flex items-center gap-1 px-2 py-0.5 border ${colors[color]} rounded text-[10px] uppercase tracking-wider`}>
      {children}
    </span>
  )
}

function TabsNav({ defaultValue, tabs, contentPanels }: { 
  defaultValue: string
  tabs: { value: string; label: React.ReactNode }[]
  contentPanels: Record<string, React.ReactNode>
}) {
  const [active, setActive] = useState(defaultValue)
  return (
    <div className="w-full">
      <div className="flex gap-1 bg-gray-800/60 border border-gray-700/50 p-1 rounded overflow-x-auto -mx-4 px-4">
        {tabs.map(t => (
          <button
            key={t.value}
            onClick={() => setActive(t.value)}
            className={`px-3 py-2 text-[11px] font-medium whitespace-nowrap transition-all rounded-sm ${
              active === t.value
                ? 'bg-sky-500 text-white'
                : 'text-slate-400 hover:text-white'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>
      <div className="mt-6">{contentPanels[active]}</div>
    </div>
  )
}

// ===== MAIN PAGE =====
export default function AnalyticsPage() {
  const [analyticsData] = useState<AnalyticsData>(generateMockAnalyticsData)
  const [lastUpdated, setLastUpdated] = useState(new Date())
  const [searchQuery, setSearchQuery] = useState("")

  useEffect(() => {
    const interval = setInterval(() => setLastUpdated(new Date()), 5_000)
    return () => clearInterval(interval)
  }, [])

  // Insights filtering
  const filteredInsights = analyticsData.insights.filter(
    insight =>
      insight.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
      insight.subtitle.toLowerCase().includes(searchQuery.toLowerCase()) ||
      insight.keywords.some((k: string) => k.toLowerCase().includes(searchQuery.toLowerCase()))
  )

  // Sort subreddits by total activity
  const sortedSubs = [...analyticsData.subredditStats].sort(
    (a, b) => (b.postCount + b.commentCount) - (a.postCount + a.commentCount)
  )

  const totalSentiment = analyticsData.sentimentDistribution.reduce((s, d) => s + d.count, 0)
  const positivePct = totalSentiment > 0 
    ? ((analyticsData.sentimentDistribution.find(d => d.label === 'Positive')?.count || 0) / totalSentiment * 100).toFixed(1)
    : '0'

  return (
    <div className="flex flex-col w-full gap-6 p-8 bg-gradient-to-br from-black via-gray-950 to-black text-white min-h-screen">
      {/* HEADER */}
      <section className="flex items-start justify-between mb-2 flex-wrap gap-3">
        <div>
          <h1 className="text-4xl font-bold tracking-tight bg-gradient-to-r from-sky-400 via-purple-400 to-pink-400 bg-clip-text text-transparent">
            Reddit Analytics Dashboard
          </h1>
          <p className="text-sm text-gray-400 mt-2 max-w-xl leading-relaxed">
            Real-time crawling insights, sentiment analysis &amp; project opportunity discovery from {analyticsData.subredditStats.length} tracked subreddits. 
            Updated automatically every 5 seconds.
          </p>
        </div>
        <div className="flex gap-3 items-center shrink-0">
          <span className="inline-flex items-center gap-1.5 px-3 py-1 text-[11px] font-medium rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/30">
            <span className="w-1.5 h-1.5 bg-emerald-400 rounded-full animate-pulse" />
            LIVE
          </span>
          <span className="text-[10px] text-gray-500 uppercase tracking-wider">
            Last sync: {lastUpdated.toLocaleTimeString()}
          </span>
        </div>
      </section>

      {/* STAT CARDS */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <StatCard
          icon={<Globe className="w-5 h-5 text-sky-400" />}
          label="Tracked Subreddits"
          value={analyticsData.subredditStats.length}
          change="+3 this week"
          trend="up"
        />
        <StatCard
          icon={<MessageSquare className="w-5 h-5 text-purple-400" />}
          label="Total Posts Crawled"
          value={analyticsData.subredditStats.reduce((s, r) => s + r.postCount, 0).toLocaleString()}
          change="+12% from last week"
          trend="up"
        />
        <StatCard
          icon={<Users className="w-5 h-5 text-pink-400" />}
          label="Positive Sentiment Ratio"
          value={`${positivePct}%`}
          change="Trending up"
          trend="up"
        />
        <StatCard
          icon={<Lightbulb className="w-5 h-5 text-amber-400" />}
          label="Opportunities Found"
          value={analyticsData.insights.length}
          change="Requires action"
          trend="neutral"
        />
      </div>

      {/* TABS */}
      <TabsNav
        defaultValue="overview"
        tabs={[
          { value: 'overview', label: <><BarChart3 className="inline w-3.5 h-3.5 mr-1" /> Overview</> },
          { value: 'sentiment', label: <><PieChartIcon className="inline w-3.5 h-3.5 mr-1" /> Sentiment Breakdown</> },
          { value: 'keywords', label: <><TrendingUp className="inline w-3.5 h-3.5 mr-1" /> Top Keywords</> },
          { value: 'insights', label: <><Target className="inline w-3.5 h-3.5 mr-1" /> Insights & Ideas</> },
        ]}
        contentPanels={{

          // ========== OVERVIEW TAB ==========
          overview: (
            <div className="space-y-8">
              {/* Row 1: Daily Activity + Top Subreddits */}
              <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                {/* Line chart */}
                <div className="bg-gray-900/60 border border-gray-800 rounded-lg p-5">
                  <h3 className="text-sm font-semibold mb-4 text-white flex items-center gap-2">
                    <TrendingUp className="w-4 h-4 text-emerald-400" /> Daily Activity Trend (30 days)
                  </h3>
                  <ResponsiveContainer width="100%" height={280}>
                    <LineChart data={analyticsData.dailyActivity}>
                      <CartesianGrid strokeDasharray="3 3" className="stroke-gray-700/40" />
                      <XAxis 
                        dataKey="date" 
                        tick={{ fill: '#94a3b8', fontSize: 10 }} 
                        tickFormatter={(v: string) => v.slice(5)} 
                        axisLine={false} 
                        tickLine={false} 
                        interval={4}
                        minTickGap={20}
                      />
                      <YAxis tick={{ fill: '#94a3b8', fontSize: 10 }} axisLine={false} tickLine={false} />
                      <Tooltip 
                        contentStyle={{ backgroundColor: '#0f172a', border: '1px solid #1e293b', borderRadius: 6, color: '#fff', fontSize: 11, boxShadow: '0 4px 12px rgba(0,0,0,0.5)' }} 
                        labelStyle={{ color: '#94a3b8', fontWeight: 'bold' }}
                      />
                      <Legend wrapperStyle={{ fontSize: 10, paddingTop: 4 }} iconType="circle" />
                      <Line type="monotone" dataKey="posts" stroke="#0ea5e9" strokeWidth={2.5} name="Posts" dot={false} activeDot={{ r: 5 }} />
                      <Line type="monotone" dataKey="comments" stroke="#8b5cf6" strokeWidth={2.5} name="Comments" dot={false} activeDot={{ r: 5 }} />
                    </LineChart>
                  </ResponsiveContainer>
                </div>

                {/* Bar chart */}
                <div className="bg-gray-900/60 border border-gray-800 rounded-lg p-5">
                  <h3 className="text-sm font-semibold mb-4 text-white flex items-center gap-2">
                    <MessageSquare className="w-4 h-4 text-sky-400" /> Top Subreddits by Activity
                  </h3>
                  <ResponsiveContainer width="100%" height={280}>
                    <BarChart data={sortedSubs.slice(0, 6)}>
                      <CartesianGrid strokeDasharray="3 3" className="stroke-gray-700/40" />
                      <XAxis 
                        dataKey="subreddit" 
                        tick={{ fill: '#94a3b8', fontSize: 9 }} 
                        axisLine={false} 
                        tickLine={false} 
                        interval={0} 
                        angle={-12} 
                        dy={14} 
                        textAnchor="end" 
                        height={55}
                        minTickGap={20}
                      />
                      <YAxis tick={{ fill: '#94a3b8', fontSize: 10 }} axisLine={false} tickLine={false} />
                      <Tooltip 
                        contentStyle={{ backgroundColor: '#0f172a', border: '1px solid #1e293b', borderRadius: 6, color: '#fff', fontSize: 11 }} 
                        labelStyle={{ color: '#94a3b8' }}
                      />
                      <Legend wrapperStyle={{ fontSize: 10 }} iconType="circle" />
                      <Bar dataKey="postCount" fill="#0ea5e9" name="Posts" radius={[6, 6, 0, 0]} />
                      <Bar dataKey="commentCount" fill="#8b5cf6" name="Comments" radius={[6, 6, 0, 0]} />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              </div>

              {/* Row 2: Sentiment pie + Weekly crawl */}
              <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                {/* Pie chart */}
                <div className="bg-gray-900/60 border border-gray-800 rounded-lg p-5 flex flex-col">
                  <h3 className="text-sm font-semibold mb-4 text-white">Sentiment Distribution</h3>
                  <ResponsiveContainer width="100%" height={220}>
                    <PieChart>
                      <Pie
                        data={analyticsData.sentimentDistribution}
                        cx="50%"
                        cy="50%"
                        outerRadius={80}
                        innerRadius={40}
                        fill="#8884d8"
                        dataKey="count"
                        labelLine={false}
                      >
                        {analyticsData.sentimentDistribution.map((entry: any, i: number) => (
                          <Cell key={`cell-${i}`} fill={SENTIMENT_COLORS[entry.label] || COLORS[i % COLORS.length]} />
                        ))}
                      </Pie>
                      <Tooltip contentStyle={{ backgroundColor: '#0f172a', border: '1px solid #1e293b', borderRadius: 6, color: '#fff' }} />
                    </PieChart>
                  </ResponsiveContainer>
                  <div className="flex justify-center gap-4 mt-3">
                    {analyticsData.sentimentDistribution.map((entry: any) => (
                      <span key={entry.label} className="flex items-center gap-1.5 text-xs">
                        <span className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: SENTIMENT_COLORS[entry.label] || '#8884d8' }} />
                        <span className="text-gray-400">{entry.label}</span>
                      </span>
                    ))}
                  </div>
                </div>
                <div className="lg:col-span-2 bg-gray-900/60 border border-gray-800 rounded-lg p-5">
                  <h3 className="text-sm font-semibold mb-4 text-white flex items-center gap-2">
                    <Briefcase className="w-4 h-4 text-sky-400" /> Weekly Crawl Volume (articles collected per day)
                  </h3>
                  <ResponsiveContainer width="100%" height={280}>
                    <BarChart data={analyticsData.weeklyCrawl}>
                      <CartesianGrid strokeDasharray="3 3" className="stroke-gray-700/40" />
                      <XAxis dataKey="day" tick={{ fill: '#94a3b8', fontSize: 12 }} axisLine={false} tickLine={false} />
                      <YAxis tick={{ fill: '#94a3b8', fontSize: 10 }} axisLine={false} tickLine={false} />
                      <Tooltip 
                        contentStyle={{ backgroundColor: '#0f172a', border: '1px solid #1e293b', borderRadius: 6, color: '#fff', fontSize: 11 }} 
                        labelStyle={{ color: '#94a3b8' }}
                      />
                      <Legend wrapperStyle={{ fontSize: 10 }} iconType="circle" />
                      <Bar dataKey="collected" name="Articles Crawled" radius={[6, 6, 0, 0]}>
                        {analyticsData.weeklyCrawl.map((_, i) => (
                          <Cell key={`cell-${i}`} fill={COLORS[i % COLORS.length]} />
                        ))}
                      </Bar>
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              </div>

              {/* Row 3: Quick table */}
              <div className="bg-gray-900/60 border border-gray-800 rounded-lg p-5">
                <h3 className="text-sm font-semibold mb-4 text-white">Subreddit Quick Overview (Top 5)</h3>
                <div className="overflow-x-auto">
                  <table className="w-full text-left">
                    <thead>
                      <tr className="border-b border-gray-800">
                        <th className="pb-2 text-[10px] font-semibold uppercase tracking-wider text-gray-400">Subreddit</th>
                        <th className="pb-2 text-[10px] font-semibold uppercase tracking-wider text-gray-400 text-right">Posts</th>
                        <th className="pb-2 text-[10px] font-semibold uppercase tracking-wider text-gray-400 text-right">Comments</th>
                        <th className="pb-2 text-[10px] font-semibold uppercase tracking-wider text-gray-400 text-center">Sentiment</th>
                        <th className="pb-2 text-[10px] font-semibold uppercase tracking-wider text-gray-400 text-right">Threads/Day</th>
                      </tr>
                    </thead>
                    <tbody>
                      {sortedSubs.slice(0, 5).map(sub => (
                        <tr key={sub.subreddit} className="border-b border-gray-800/50 hover:bg-gray-800/30 transition-colors">
                          <td className="py-3 text-sm font-medium text-white">{sub.subreddit}</td>
                          <td className="py-3 text-sm text-gray-300 text-right tabular-nums">{sub.postCount.toLocaleString()}</td>
                          <td className="py-3 text-sm text-gray-300 text-right tabular-nums">{sub.commentCount.toLocaleString()}</td>
                          <td className="py-3 text-center">
                            <span className={`inline-flex items-center px-2 py-0.5 rounded text-[10px] font-medium ${
                              sub.sentimentScore > 0 ? 'bg-emerald-500/10 text-emerald-400' : 
                              sub.sentimentScore < 0 ? 'bg-pink-500/10 text-pink-400' : 
                              'bg-slate-500/10 text-slate-400'
                            }`}>
                              {sub.sentimentScore > 0 ? '+' : ''}{sub.sentimentScore}
                            </span>
                          </td>
                          <td className="py-3 text-sm text-gray-300 text-right tabular-nums">{sub.avgThreadsDay}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          ),

          // ========== SENTIMENT TAB ==========
          sentiment: (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {analyticsData.subredditStats.map((sub) => (
                <div 
                  key={sub.subreddit} 
                  className={`bg-gray-900/60 border rounded-lg p-5 transition-colors hover:border-sky-500/30 cursor-default ${
                    sub.sentimentScore > 0 ? 'border-emerald-500/30' : 
                    sub.sentimentScore < 0 ? 'border-pink-500/30' : 
                    'border-gray-800'
                  }`}
                >
                  <div className="flex items-start justify-between mb-4">
                    <h3 className="text-sm font-semibold text-white">{sub.subreddit}</h3>
                    <Badge color={sub.sentimentScore > 0 ? 'emerald' : sub.sentimentScore < 0 ? 'pink' : 'slate'}>
                      {sub.sentimentScore > 0 ? '+' : ''}{sub.sentimentScore} Sentiment
                    </Badge>
                  </div>
                  <div className="grid grid-cols-3 gap-4 text-xs">
                    <div><span className="text-gray-500 block mb-1">Posts</span><p className="text-white font-medium text-base">{sub.postCount.toLocaleString()}</p></div>
                    <div><span className="text-gray-500 block mb-1">Comments</span><p className="text-white font-medium text-base">{sub.commentCount.toLocaleString()}</p></div>
                    <div><span className="text-gray-500 block mb-1">Threads/Day</span><p className="text-white font-medium text-base">{sub.avgThreadsDay}</p></div>
                  </div>
                </div>
              ))}
            </div>
          ),

          // ========== KEYWORDS TAB ==========
          keywords: (
            <div className="space-y-6">
              <div className="bg-gray-900/60 border border-gray-800 rounded-lg p-5">
                <h3 className="text-sm font-semibold mb-4 text-white">Top Extracted Keywords & Themes by Frequency</h3>
                <ResponsiveContainer width="100%" height={350}>
                  <BarChart layout="vertical" data={analyticsData.topKeywords} margin={{ left: 40 }}>
                    <CartesianGrid strokeDasharray="3 3" className="stroke-gray-700/40" orientation="vertical" />
                    <XAxis type="number" tick={{ fill: '#94a3b8', fontSize: 10 }} axisLine={false} tickLine={false} />
                    <YAxis 
                      dataKey="term" 
                      type="category" 
                      tick={{ fill: '#fff', fontSize: 13, fontWeight: '600' }} 
                      width={95} 
                      axisLine={false} 
                      tickLine={false} 
                    />
                    <Tooltip 
                      contentStyle={{ backgroundColor: '#0f172a', border: '1px solid #1e293b', borderRadius: 6, color: '#fff', fontSize: 11 }} 
                      labelStyle={{ color: '#94a3b8' }}
                    />
                    <Bar dataKey="frequency" name="Frequency" radius={[0, 6, 6, 0]}>
                      {analyticsData.topKeywords.map((_, i) => (
                        <Cell key={`cell-${i}`} fill={COLORS[i % COLORS.length]} />
                      ))}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              </div>

              {/* Keyword grid */}
              <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-3">
                {analyticsData.topKeywords.map((kw, i) => (
                  <div key={i} className="bg-gray-900/60 border border-gray-800 rounded-lg p-4 text-center hover:border-sky-500/30 transition-colors">
                    <p className="text-sm font-bold text-white">{kw.term}</p>
                    <p className="text-xs text-gray-400 mt-1">{kw.frequency.toLocaleString()} mentions</p>
                  </div>
                ))}
              </div>
            </div>
          ),

          // ========== INSIGHTS TAB ==========
          insights: (
            <div className="space-y-6">
              {/* Search bar */}
              <div className="flex gap-3 flex-wrap items-center">
                <div className="relative flex-1 max-w-md">
                  <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-500" />
                  <input
                    placeholder="Search insights, keywords, project ideas..."
                    value={searchQuery}
                    onChange={e => setSearchQuery(e.target.value)}
                    className="w-full pl-9 pr-4 py-2.5 text-sm bg-gray-900/60 border border-gray-800 focus:border-sky-500/50 rounded outline-none placeholder:text-gray-500 text-white"
                  />
                </div>
              </div>

              {/* Insight cards */}
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                {filteredInsights.map((insight, i) => (
                  <div 
                    key={i} 
                    className="bg-gray-900/60 border border-gray-800 rounded-lg p-5 hover:border-sky-500/30 transition-all cursor-pointer group"
                  >
                    <div className="flex items-start justify-between mb-3 gap-2">
                      <Badge color={insight.category === 'idea' ? 'amber' : insight.category === 'needs' ? 'emerald' : 'sky'}>
                        {insight.category === 'idea' && <Lightbulb className="w-3 h-3 mr-1" />}
                        {insight.category === 'needs' && <Briefcase className="w-3 h-3 mr-1" />}
                        {insight.category === 'project' && <Target className="w-3 h-3 mr-1" />}
                        {insight.category}
                      </Badge>
                      <span className="text-[10px] text-gray-500 shrink-0">{(insight.confidence * 100).toFixed(0)}% match</span>
                    </div>
                    <h3 className="text-sm font-semibold mb-2 text-white group-hover:text-sky-400 transition-colors line-clamp-2 leading-snug">
                      {insight.title}
                    </h3>
                    <p className="text-xs text-gray-400 mb-3 line-clamp-3 leading-relaxed">{insight.subtitle}</p>
                    <div className="flex flex-wrap gap-1.5">
                      {insight.keywords.map((k: string) => (
                        <span key={k} className="inline-flex items-center px-1.5 py-0.5 bg-sky-500/10 text-sky-300 border border-sky-500/20 rounded text-[9px]">
                          {k}
                        </span>
                      ))}
                    </div>
                  </div>
                ))}
              </div>

              {/* Empty state */}
              {filteredInsights.length === 0 && (
                <div className="text-center py-16">
                  <Lightbulb className="w-12 h-12 mx-auto mb-4 text-gray-700" />
                  <p className="text-gray-500 text-sm">No insights match your search. Try a different keyword.</p>
                </div>
              )}
            </div>
          ),

        }}
      />
    </div>
  );
}
