import axios from "axios";
import {
  createPaginatedResponse,
  mockComments,
  mockCrawlerStatus,
  mockPosts,
  mockSettings,
  mockStats,
} from "@/lib/mock-data";
import { filterBySearch } from "@/lib/utils";
import type {
  ApiErrorShape,
  CrawlConfig,
  DataQuery,
  LoginPayload,
  LoginResponse,
  PaginatedResponse,
  PostRecord,
  CommentRecord,
  SettingsPayload,
  StatsSummary,
  CrawlerStatus,
} from "@/lib/types";

export const api = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_URL || "/api",
  withCredentials: true,
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const normalized: ApiErrorShape = {
      message:
        error.response?.data?.message ||
        error.message ||
        "The Reddit Crawler API request failed.",
      status: error.response?.status,
    };

    return Promise.reject(normalized);
  },
);

function shouldFallback(error: unknown) {
  const apiError = error as ApiErrorShape;
  return apiError.status === 404 || apiError.status === 500 || apiError.status === undefined;
}

async function withFallback<T>(request: () => Promise<T>, fallback: () => T | Promise<T>) {
  try {
    return await request();
  } catch (error) {
    if (shouldFallback(error)) {
      return fallback();
    }

    throw error;
  }
}

function applyQueryToPosts(query: DataQuery) {
  const filtered = filterBySearch(mockPosts, query.search ?? "", ["title", "author", "subreddit"]);
  const narrowed = query.subreddit
    ? filtered.filter((item) => item.subreddit === query.subreddit)
    : filtered;

  return createPaginatedResponse(narrowed, query.page, query.pageSize);
}

function applyQueryToComments(query: DataQuery) {
  const filtered = filterBySearch(mockComments, query.search ?? "", [
    "body",
    "author",
    "subreddit",
    "parentPostTitle",
  ]);
  const narrowed = query.subreddit
    ? filtered.filter((item) => item.subreddit === query.subreddit)
    : filtered;

  return createPaginatedResponse(narrowed, query.page, query.pageSize);
}

export async function fetchStats() {
  return withFallback<StatsSummary>(
    async () => (await api.get<StatsSummary>("/stats")).data,
    () => mockStats,
  );
}

export async function fetchCrawlerStatus() {
  return withFallback<CrawlerStatus>(
    async () => (await api.get<CrawlerStatus>("/crawler/status")).data,
    () => mockCrawlerStatus,
  );
}

export async function startCrawler(config: CrawlConfig) {
  return withFallback<CrawlerStatus>(
    async () => (await api.post<CrawlerStatus>("/crawler/start", config)).data,
    () => ({
      ...mockCrawlerStatus,
      isRunning: true,
      mode: "warming_up",
      progress: 12,
      currentSubreddit: config.subreddit,
      config,
      lastRunAt: new Date().toISOString(),
    }),
  );
}

export async function stopCrawler() {
  return withFallback<CrawlerStatus>(
    async () => (await api.post<CrawlerStatus>("/crawler/stop")).data,
    () => ({
      ...mockCrawlerStatus,
      isRunning: false,
      mode: "idle",
      progress: 0,
      currentSubreddit: null,
      lastRunAt: new Date().toISOString(),
    }),
  );
}

export async function fetchPosts(query: DataQuery) {
  return withFallback<PaginatedResponse<PostRecord>>(
    async () => (await api.get<PaginatedResponse<PostRecord>>("/data/posts", { params: query })).data,
    () => applyQueryToPosts(query),
  );
}

export async function fetchComments(query: DataQuery) {
  return withFallback<PaginatedResponse<CommentRecord>>(
    async () =>
      (await api.get<PaginatedResponse<CommentRecord>>("/data/comments", { params: query })).data,
    () => applyQueryToComments(query),
  );
}

export async function fetchSettings() {
  return withFallback<SettingsPayload>(
    async () => (await api.get<SettingsPayload>("/settings")).data,
    () => mockSettings,
  );
}

export async function updateSettings(payload: SettingsPayload) {
  return withFallback<SettingsPayload>(
    async () => (await api.post<SettingsPayload>("/settings", payload)).data,
    () => payload,
  );
}

export async function login(payload: LoginPayload) {
  return withFallback<LoginResponse>(
    async () => (await api.post<LoginResponse>("/auth/login", payload)).data,
    () => ({
      user: mockSettings.users[0],
      sessionExpiresAt: new Date(Date.now() + 1000 * 60 * 45).toISOString(),
    }),
  );
}
