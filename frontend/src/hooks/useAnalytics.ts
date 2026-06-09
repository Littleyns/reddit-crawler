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
// P4-2: NO MOCK DATA — always hits real backend. Sets loading/error when unreachable.
// All fetches use relative /api/... paths so they are proxied through Next.js to the backend server.
// ---------------------------------------------------------------------------

export function useAnalytics() {
  const [data, setData] = useState<AnalysisData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [lastUpdated, setLastUpdated] = useState(new Date());
  const [apiReachable, setApiReachable] = useState(false);

  async function fetchAnalysisSnapshot() {
    try {
      // All relative to /api/ — Next.js rewrites proxy these to the backend server
      const baseUrl = "/api/analysis/snapshot";
      const reachable = await ping("/api/analysis?health=true");
      setApiReachable(reachable);

      if (reachable) {
        // P4-2: ONLY real backend calls — no mock fallback
        const [snapshotRes, sentimentRes, keywordsRes, insightsRes] =
          await Promise.all([
            fetch(baseUrl).then((r) => r.ok ? r.json() : null),
            fetch("/api/analysis/sentiment").then((r) => r.ok ? r.json() : []),
            fetch("/api/analysis/keywords")
              .then((r) => r.ok ? r.json() : []),
            fetch("/api/analysis/insights")
              .then((r) => r.ok ? r.json() : []),
          ]);

        // P4-1 + P4-2: Merge snapshot data with real sentiment/keywords/insights
        if (snapshotRes && "subredditStats" in snapshotRes) {
          const merged: AnalysisData = {
            subredditStats: snapshotRes.subredditStats ?? [],
            dailyActivity: snapshotRes.dailyActivity ?? [],
            sentimentDistribution: sentimentRes ?? snapshotRes.sentimentDistribution ?? [],
            topKeywords: keywordsRes ?? [],
            insights: insightsRes ?? [],
            weeklyCrawl: snapshotRes.weeklyCrawl ?? [],
          };
          setData(merged);
        } else {
          // P4-2: Backend returned data but without expected shape — still use it (not mock)
          if (snapshotRes) {
            convertToAnalysisData(snapshotRes).then((converted) => {
              setData(converted);
            });
          }
        }
      } else {
        // P4-2: API unreachable — set error state to indicate real failure (not silent mock)
        setError("Backend API is unreachable. Please check the server is running.");
        setData(null);
      }
    } catch (err: any) {
      setError(err?.message ?? "Failed to fetch analytics from backend");
      setData(null);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    setLastUpdated(new Date());
    fetchAnalysisSnapshot();

    // Refresh every 30 seconds (reduced from 5s for production performance)
    const id = setInterval(() => {
      setLastUpdated(new Date());
      fetchAnalysisSnapshot();
    }, 30_000);

    return () => clearInterval(id);
  }, []);

  return { data, loading, error, lastUpdated, apiReachable };
}

// ---------------------------------------------------------------------------
// Convert a semi-structured API response into our AnalysisData shape ----
// P4-2: Only called when backend returns unexpected format (not for mock data)
// ---------------------------------------------------------------------------

export function convertToAnalysisData(raw: Record<string, unknown>): Promise<AnalysisData> {
  return Promise.resolve({
    subredditStats: (Array.isArray(raw.subredditStats) ? raw.subredditStats : []) as unknown as SubredditStats[],
    dailyActivity: (Array.isArray(raw.dailyActivity) ? raw.dailyActivity : []) as unknown as { date: string; posts: number; comments: number }[],
    sentimentDistribution: (Array.isArray(raw.sentimentDistribution) ? raw.sentimentDistribution : []) as unknown as SentimentDistribution[],
    topKeywords: (Array.isArray(raw.topKeywords) ? raw.topKeywords : []) as unknown as KeywordItem[],
    insights: (Array.isArray(raw.insights) ? raw.insights : []) as unknown as ThreadInsight[],
    weeklyCrawl: (Array.isArray(raw.weeklyCrawl) ? raw.weeklyCrawl : []) as unknown as { day: string; collected: number }[],
  });
}

// Removed: generateMockAnalyticsData() — no mock data in production (P4-2)
