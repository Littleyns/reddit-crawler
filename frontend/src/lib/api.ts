import axios from "axios";
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
  CrawlJobBackend,
} from "@/lib/types";

// Read stored token – mirrors localStorage.getItem("token").
export function getToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem("token");
}

export const api = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_URL || "/api",
  withCredentials: true,
});

// Attach Bearer token to every request when available.
api.interceptors.request.use((config) => {
  const token = getToken();
  if (token) config.headers.set("Authorization", `Bearer ${token}`);
  return config;
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

// ─── P4-2: All fetchers now hit the real backend ONLY — no mock fallbacks ───

export async function fetchStats(): Promise<StatsSummary> {
  const res = await api.get<StatsSummary>("/stats");
  return res.data;
}

export async function fetchCrawlerStatus(): Promise<CrawlerStatus> {
  const res = await api.get<CrawlerStatus>("/crawler/status");
  return res.data;
}

export async function startCrawler(config: CrawlConfig): Promise<CrawlerStatus> {
  // P4-1: Include apiKeyAlias in request if available (from localStorage or settings)
  const activeKey = getToken()?.length ? undefined : undefined;
  const body = { ...config, sort: "hot" };
  if (activeKey != null) {
    (body as any).apiKeyAlias = activeKey;
  }
  const res = await api.post<CrawlerStatus>("/crawler/start", body);
  return res.data;
}

export async function stopCrawler(): Promise<CrawlerStatus> {
  await api.post<void>("/crawler/stop");
  const res = await api.get<CrawlerStatus>("/crawler/status");
  return res.data;
}

// Posts — uses real backend /api/data/posts endpoint. No mock.
export async function fetchPosts(query: DataQuery): Promise<PaginatedResponse<PostRecord>> {
  const res = await api.get<PaginatedResponse<PostRecord>>("/data/posts", { params: query });
  return res.data;
}

// Comments — uses real backend /api/data/comments endpoint. No mock.
export async function fetchComments(query: DataQuery): Promise<PaginatedResponse<CommentRecord>> {
  const res = await api.get<PaginatedResponse<CommentRecord>>("/data/comments", { params: query });
  return res.data;
}

// Settings — uses real backend /settings endpoint. No mock.
export async function fetchSettings(): Promise<SettingsPayload> {
  const res = await api.get<SettingsPayload>("/settings");
  return res.data;
}

export async function updateSettings(payload: SettingsPayload): Promise<SettingsPayload> {
  // POST to /settings — idempotent upsert. No mock.
  const res = await api.post<SettingsPayload>("/settings", payload);
  return res.data;
}

// Login — uses real backend /auth/login endpoint. No mock.
export async function login(payload: LoginPayload): Promise<LoginResponse> {
  const res = await api.post<LoginResponse>("/auth/login", payload);
  return res.data;
}

// ──────────────── P4-2: Real crawl jobs endpoint (dashboard queue) ─────────
export async function fetchCrawlerJobs(): Promise<CrawlJobBackend[]> {
  const BACKEND = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";
  const res = await fetch(`${BACKEND}/api/crawler/jobs`, { cache: "no-store" });
  if (!res.ok) return [];
  const data = await res.json();
  return Array.isArray(data) ? data : (data.jobs ?? []);
}

// ── P4-1 / P4-2: Real analytics endpoint on backend (used by useAnalyticsReal in hooks) — no mock ──
export async function fetchAnalytics(): Promise<Record<string, unknown>> {
  const BACKEND = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";
  const res = await fetch(`${BACKEND}/api/analytics`, { cache: "no-store" });
  if (!res.ok) return {};
  return (await res.json()) ?? {};
}

export async function fetchSubredditStats(): Promise<unknown[]> {
  const BACKEND = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";
  const res = await fetch(`${BACKEND}/api/data/subreddits`, { cache: "no-store" });
  if (!res.ok) return [];
  return (await res.json()) ?? [];
}

// P4-1 / P4-2: API keys management — real backend CRUD via /api/keys
export async function fetchApiKeys(): Promise<unknown[]> {
  const BACKEND = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";
  const res = await fetch(`${BACKEND}/api/keys`, { cache: "no-store" });
  if (!res.ok) return [];
  const data = await res.json();
  return Array.isArray(data) ? data : [];
}

export async function addApiKey(clientId: string, clientSecret: string, alias?: string): Promise<unknown> {
  const BACKEND = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";
  const res = await fetch(`${BACKEND}/api/keys`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ clientId, clientSecret, alias }),
  });
  if (!res.ok) throw new Error(`addApiKey failed (${res.status})`);
  return res.json();
}

export async function removeApiKey(id: number): Promise<void> {
  const BACKEND = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";
  const res = await fetch(`${BACKEND}/api/keys/${id}`, { method: 'DELETE' });
  if (!res.ok) throw new Error(`removeApiKey failed (${res.status})`);
}

export async function refreshAllApiTokens(): Promise<{ message: string }> {
  const BACKEND = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";
  const res = await fetch(`${BACKEND}/api/keys/refresh`, { method: 'POST' });
  if (!res.ok) throw new Error(`refreshAllApiTokens failed (${res.status})`);
  return res.json();
}

export async function fetchActiveSubreddits(): Promise<unknown> {
  const BACKEND = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";
  const res = await fetch(`${BACKEND}/data/subreddits`, { cache: 'no-store' });
  if (!res.ok) throw new Error('fetchActiveSubreddits failed');
  return res.json();
}

// Search endpoint for posts — same as fetchPosts but with full-text search
export async function searchPosts(term: string, page = 1): Promise<PaginatedResponse<PostRecord>> {
  const res = await api.get<PaginatedResponse<PostRecord>>('/data/posts', { params: { search: term, page } });
  return res.data;
}

// Filter and map post items by keyword for analytics pipelines
export async function filterPostsByKeywords(keywords: string[], limit = 50): Promise<PaginatedResponse<any>> {
  const all = await fetchPosts({ page: 1, pageSize: limit });
  const filtered = filterBySearch(all.items, keywords.join(' '), ['title', 'author', 'subreddit']);
  return { ...all, items: filtered, total: filtered.length };
}

// P4-2: Export crawl results (raw post/comment data dump) in CSV/JSON format
export async function exportCrawlResults(format: string = "json"): Promise<Blob> {
  const BACKEND = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";
  const res = await fetch(`${BACKEND}/api/export?type=${format}`, { cache: 'no-store' });
  if (!res.ok) throw new Error(`exportCrawlResults failed (${res.status})`);
  return res.blob();
}
