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
