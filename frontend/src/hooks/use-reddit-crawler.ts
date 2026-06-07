"use client";

import {
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import {
  fetchComments,
  fetchCrawlerStatus,
  fetchPosts,
  fetchSettings,
  fetchStats,
  login,
  startCrawler,
  stopCrawler,
  updateSettings,
} from "@/lib/api";
import type { CrawlConfig, DataQuery, LoginPayload, SettingsPayload } from "@/lib/types";

export function useStats() {
  return useQuery({
    queryKey: ["stats"],
    queryFn: fetchStats,
  });
}

export function useCrawlerStatus() {
  return useQuery({
    queryKey: ["crawler-status"],
    queryFn: fetchCrawlerStatus,
    refetchInterval: 15_000,
  });
}

export function usePosts(query: DataQuery) {
  return useQuery({
    queryKey: ["posts", query],
    queryFn: () => fetchPosts(query),
  });
}

export function useComments(query: DataQuery) {
  return useQuery({
    queryKey: ["comments", query],
    queryFn: () => fetchComments(query),
  });
}

export function useSettings() {
  return useQuery({
    queryKey: ["settings"],
    queryFn: fetchSettings,
  });
}

export function useCrawlerControls() {
  const queryClient = useQueryClient();

  const refresh = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["crawler-status"] }),
      queryClient.invalidateQueries({ queryKey: ["stats"] }),
    ]);
  };

  return {
    startMutation: useMutation({
      mutationFn: (config: CrawlConfig) => startCrawler(config),
      onSuccess: refresh,
    }),
    stopMutation: useMutation({
      mutationFn: stopCrawler,
      onSuccess: refresh,
    }),
  };
}

export function useSaveSettings() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: SettingsPayload) => updateSettings(payload),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["settings"] });
    },
  });
}

export function useLogin() {
  return useMutation({
    mutationFn: (payload: LoginPayload) => login(payload),
  });
}

// P4-2: Use real job data from backend
export function useJobs() {
  return useQuery({
    queryKey: ["jobs"],
    queryFn: fetchJobs,
    refetchInterval: 15_000,
  });
}

async function fetchJobs(): Promise<Record<string, unknown>[]> {
  const BACKEND = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";
  try {
    const res = await fetch(BACKEND + "/api/crawler/jobs", { cache: "no-store" });
    if (!res.ok) return [];
    const data = await res.json();
    return Array.isArray(data) ? data : (data.jobs ?? []);
  } catch {
    return [];
  }
}

// P4-2: Real analytics from backend API routes
export function useAnalyticsReal() {
  return useQuery({
    queryKey: ["analytics-real"],
    queryFn: async () => {
      const BACKEND = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";
      try {
        const [resAnalytics, resSubs] = await Promise.all([
          fetch(BACKEND + "/api/analytics", { cache: "no-store" }),
          fetch(BACKEND + "/api/data/subreddits", { cache: "no-store" }),
        ]);
        let analyticsData: Record<string, unknown> = {};
        let subredditData: unknown[] = [];
        if (resAnalytics.ok) analyticsData = await resAnalytics.json();
        if (resSubs.ok) subredditData = await resSubs.json();
        return { ...analyticsData, subredditStats: Array.isArray(subredditData) ? subredditData : [] };
      } catch {
        return {};
      }
    },
  });
}
