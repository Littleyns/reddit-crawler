"use client";

import { useQuery, useQueries } from "@tanstack/react-query";
import {
  fetchAnalyticsHeatmap,
  fetchAnalyticsIdeas,
  fetchAnalyticsKeywords,
  fetchAnalyticsReport,
  fetchAnalyticsSentiment,
  fetchAnalyticsTrends,
} from "@/lib/analytics-api";

export function useSentiment() {
  return useQuery({
    queryKey: ["analytics", "sentiment"],
    queryFn: fetchAnalyticsSentiment,
    refetchInterval: 30_000,
  });
}

export function useHeatmap() {
  return useQuery({
    queryKey: ["analytics", "heatmap"],
    queryFn: fetchAnalyticsHeatmap,
    refetchInterval: 30_000,
  });
}

export function useTrends() {
  return useQuery({
    queryKey: ["analytics", "trends"],
    queryFn: fetchAnalyticsTrends,
    refetchInterval: 30_000,
  });
}

export function useKeywords(topN = 30) {
  return useQuery({
    queryKey: ["analytics", "keywords", topN],
    queryFn: () => fetchAnalyticsKeywords(topN),
    refetchInterval: 30_000,
  });
}

export function useIdeas(category?: string) {
  return useQuery({
    queryKey: ["analytics", "ideas", category],
    queryFn: () => fetchAnalyticsIdeas(category),
    refetchInterval: 30_000,
    placeholderData: [],
  });
}

export function useReport() {
  return useQuery({
    queryKey: ["analytics", "report"],
    queryFn: fetchAnalyticsReport,
    refetchInterval: 30_000,
  });
}
