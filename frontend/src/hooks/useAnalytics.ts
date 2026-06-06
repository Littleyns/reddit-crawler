"use client";

import { useState, useEffect } from "react";

// --- Shared data shapes matching the backend /api/analytics/* contracts ---

export interface SentimentDistribution {
  label: string;
  count: number;
}

export interface SubredditStats {
  subreddit: string;
  postCount: number;
  commentCount: number;
  sentimentScore: number; // -1 (negative) to 1 (positive)
  avgThreadsDay: number;
  positivePercent: number;
  neutralPercent: number;
  negativePercent: number;
}

export interface KeywordItem {
  term: string;
  frequency: number;
}

export interface ThreadInsight {
  title: string;
  subtitle: string;
  keywords: string[];
  category: "needs" | "idea" | "project";
  confidence: number;
}

export interface AnalysisData {
  subredditStats: SubredditStats[];
  dailyActivity: { date: string; posts: number; comments: number }[];
  sentimentDistribution: SentimentDistribution[];
  topKeywords: KeywordItem[];
  insights: ThreadInsight[];
  weeklyCrawl: { day: string; collected: number }[];
}

// ---------------------------------------------------------------------------
// Helpers ---------------------------------------------------------------
// ---------------------------------------------------------------------------

const API_BASE =
  process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

/**
 * Check whether a URL is reachable (no-op CORS GET).
 */
async function ping(url: string): Promise<boolean> {
  try {
    const res = await fetch(url, { method: "GET", cache: "no-store" });
    return res.ok;
  } catch {
    return false;
  }
}

// ---------------------------------------------------------------------------
// Public hook ----------------------------------------------------------
// ---------------------------------------------------------------------------

/**
 * `useAnalytics` — pure useState/useEffect hook that fetches all analytics
 * data from the backend via `/api/analysis/*`.
 * Falls back to in-browser mock generators when the API is unreachable.
 */
export function useAnalytics() {
  const [data, setData] = useState<AnalysisData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [lastUpdated, setLastUpdated] = useState(new Date());
  const [apiReachable, setApiReachable] = useState(false);

  // ---- fetch helpers (no react-query needed) ----

  async function fetchAnalysisSnapshot() {
    try {
      const baseUrl = `${API_BASE}/api/analysis/snapshot`;
      // probe: try a single endpoint; if it responds, the full API is up.
      const reachable = await ping(`${API_BASE}/api/analysis?health=true`);
      setApiReachable(reachable);

      if (reachable) {
        // Fetch all endpoints in parallel
        const [snapshotRes, heatmapRes, keywordsRes, insightsRes] =
          await Promise.all([
            fetch(baseUrl).then((r) => r.json()),
            fetch(`${API_BASE}/api/analysis/sentiment`).then((r) => r.json()),
            fetch(`${API_BASE}/api/analysis/keywords`)
              .then((r) => r.json())
              .catch(() => []),
            fetch(`${API_BASE}/api/analysis/insights`)
              .then((r) => r.json())
              .catch(() => []),
          ] as Promise<any>[]);

        if (snapshotRes && "subredditStats" in snapshotRes) {
          const merged: AnalysisData = {
            subredditStats:
              snapshotRes.subredditStats ?? heatmapRes ?? [],
            dailyActivity:
              snapshotRes.dailyActivity ?? [],
            sentimentDistribution:
              heatmapRes ?? snapshotRes.sentimentDistribution ?? [],
            topKeywords: keywordsRes ?? [],
            insights: insightsRes ?? [],
            weeklyCrawl: snapshotRes.weeklyCrawl ?? [],
          };
          setData(merged);
        } else if (snapshotRes) {
          // Fallback: spread whatever we got
          setData(convertToAnalysisData(snapshotRes));
        }
      } else {
        // API unreachable – generate mock data in-browser (deterministic)
        const mock = generateMockAnalyticsData();
        setData(mock);
      }
    } catch (err: any) {
      setError(err?.message ?? "Failed to fetch analytics");
      // Last resort: use mock data so the page still renders
      setData(generateMockAnalyticsData());
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    setLastUpdated(new Date());
    fetchAnalysisSnapshot();

    // Refresh every 5 seconds (matching the original page's setInterval)
    const id = setInterval(() => {
      setLastUpdated(new Date());
      fetchAnalysisSnapshot();
    }, 5_000);

    return () => clearInterval(id);
  }, []);

  return { data, loading, error, lastUpdated, apiReachable };
}

// ---------------------------------------------------------------------------
// Inline mock generator (used when API is down) -------------------------
// ---------------------------------------------------------------------------

export function generateMockAnalyticsData(): AnalysisData {
  const subreddits = [
    "r/webdev",
    "r/startups",
    "r/SaaS",
    "r/Entrepreneur",
    "r/AI_developers",
    "r/technology",
    "r/learnprogramming",
  ];

  // Build data from current time so it's deterministic-ish per-refresh
  const now = Date.now();
  function rng(min: number, max: number) {
    return Math.floor(
      ((now / 1000 + Math.random() * 10_000) % (max - min)) + min,
    );
  }

  return {
    subredditStats: subreddits.map((sub) => ({
      subreddit: sub,
      postCount: rng(50, 1500),
      commentCount: rng(200, 8000),
      sentimentScore: parseFloat((Math.random() * 2 - 1).toFixed(2)),
      avgThreadsDay: rng(5, 50),
      positivePercent: rng(20, 70),
      neutralPercent: rng(10, 40),
      negativePercent: rng(5, 30),
    })),
    dailyActivity: Array.from({ length: 30 }, (_, i) => {
      const d = new Date(now - (29 - i) * 86400_000);
      return {
        date: d.toISOString().slice(0, 10),
        posts: rng(100, 550),
        comments: rng(200, 1400),
      };
    }),
    sentimentDistribution: [
      { label: "Positive", count: rng(150, 750) },
      { label: "Neutral", count: rng(300, 1100) },
      { label: "Negative", count: rng(50, 400) },
    ],
    topKeywords: [
      { term: "React", frequency: rng(800, 2800) },
      { term: "API", frequency: rng(600, 2100) },
      { term: "SaaS", frequency: rng(400, 1600) },
      { term: "AI", frequency: rng(700, 2500) },
      { term: "automation", frequency: rng(300, 1400) },
    ],
    insights: [
      {
        title: "Looking for Fullstack Dev (Node/React)",
        subtitle:
          "User in r/softwareguru seeking a skilled developer to build a SaaS dashboard from scratch. Budget $5k+.",
        keywords: ["hiring", "fullstack", "dashboard"],
        category: "needs",
        confidence: 0.92,
      },
      {
        title: "Auto-Budget App Idea using AI LLMs",
        subtitle:
          "Has an idea for an auto-budget app that leverages LLMs to analyze spending patterns and suggest optimizations.",
        keywords: ["idea", "ai", "fintech"],
        category: "idea",
        confidence: 0.78,
      },
      {
        title: "Need API wrapper for Shopify data export",
        subtitle:
          "Looking to build a tool that pulls and standardizes all Shopify sales reports into a single dashboard.",
        keywords: ["api", "ecommerce", "automation"],
        category: "project",
        confidence: 0.85,
      },
      {
        title:
          "SaaS founders: need ML engineer for product analytics",
        subtitle:
          "Bootstrapped startup building AI-powered product analytics. Looking for someone with experience in predictive models.",
        keywords: ["hiring", "ml", "saas"],
        category: "needs",
        confidence: 0.89,
      },
      {
        title: "Chatbot for customer service — who can build it?",
        subtitle:
          "Looking to integrate a chatbot into our e-commerce site similar to Intercom but using open-source LLMs.",
        keywords: ["hiring", "chatbot", "ai"],
        category: "needs",
        confidence: 0.87,
      },
      {
        title: "Chrome extension idea: Reddit keyword alerts",
        subtitle:
          "Would love a Chrome extension that alerts you when keywords related to your project appear.",
        keywords: ["idea", "chrome-extension", "reddit"],
        category: "idea",
        confidence: 0.73,
      },
    ],
    weeklyCrawl: Array.from({ length: 7 }, (_, i) => ({
      day: ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"][i],
      collected: rng(500, 3500),
    })),
  };
}

// ---------------------------------------------------------------------------
// Convert a semi-structured API response into our AnalysisData shape ----
// ---------------------------------------------------------------------------

function convertToAnalysisData(raw: Record<string, unknown>): AnalysisData {
  return {
    subredditStats: (Array.isArray(raw.subredditStats)
      ? raw.subredditStats
      : []) as unknown as SubredditStats[],
    dailyActivity: (Array.isArray(raw.dailyActivity)
      ? raw.dailyActivity
      : []) as unknown as { date: string; posts: number; comments: number }[],
    sentimentDistribution: (Array.isArray(raw.sentimentDistribution)
      ? raw.sentimentDistribution
      : []) as unknown as SentimentDistribution[],
    topKeywords: (Array.isArray(raw.topKeywords)
      ? raw.topKeywords
      : []) as unknown as KeywordItem[],
    insights: (Array.isArray(raw.insights)
      ? raw.insights
      : []) as unknown as ThreadInsight[],
    weeklyCrawl: (Array.isArray(raw.weeklyCrawl)
      ? raw.weeklyCrawl
      : []) as unknown as { day: string; collected: number }[],
  };
}
