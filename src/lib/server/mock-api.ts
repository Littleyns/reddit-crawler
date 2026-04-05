import { createPaginatedResponse, mockComments, mockPosts, mockSettings, mockStats } from "@/lib/mock-data";
import { filterBySearch } from "@/lib/utils";
import type {
  CommentRecord,
  CrawlConfig,
  CrawlerStatus,
  DataQuery,
  LoginPayload,
  LoginResponse,
  PaginatedResponse,
  PostRecord,
  SettingsPayload,
} from "@/lib/types";

let crawlerStatus: CrawlerStatus = {
  isRunning: true,
  currentSubreddit: "machinelearning",
  progress: 68,
  mode: "collecting",
  activeWorkers: 4,
  requestsPerMinute: 126,
  lastRunAt: new Date().toISOString(),
  config: {
    subreddit: "machinelearning",
    depth: 4,
    limit: 250,
    includeComments: true,
    keywords: "llm, agents, benchmark",
  },
};

let settings: SettingsPayload = { ...mockSettings };

function normalizeQueryValue(value: string | null, fallback: number) {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

export function getStats() {
  const activeSubreddits = new Set(mockPosts.map((post) => post.subreddit)).size;

  return {
    ...mockStats,
    totalPosts: mockPosts.length,
    totalComments: mockComments.length,
    activeSubreddits,
    queueDepth: crawlerStatus.isRunning ? Math.max(8, crawlerStatus.config.limit / 5) : 0,
  };
}

export function getCrawlerStatus() {
  return crawlerStatus;
}

export function startCrawler(config: CrawlConfig) {
  crawlerStatus = {
    ...crawlerStatus,
    isRunning: true,
    currentSubreddit: config.subreddit,
    progress: 12,
    mode: "warming_up",
    activeWorkers: 4,
    requestsPerMinute: 84,
    lastRunAt: new Date().toISOString(),
    config,
  };

  return crawlerStatus;
}

export function stopCrawler() {
  crawlerStatus = {
    ...crawlerStatus,
    isRunning: false,
    currentSubreddit: null,
    progress: 0,
    mode: "idle",
    activeWorkers: 0,
    requestsPerMinute: 0,
    lastRunAt: new Date().toISOString(),
  };

  return crawlerStatus;
}

function applySearch<T extends PostRecord | CommentRecord>(
  items: T[],
  query: DataQuery,
  fields: Array<keyof T>,
) {
  const filtered = filterBySearch(items, query.search ?? "", fields);
  return query.subreddit
    ? filtered.filter((item) => item.subreddit === query.subreddit)
    : filtered;
}

export function getPosts(query: DataQuery): PaginatedResponse<PostRecord> {
  return createPaginatedResponse(
    applySearch(mockPosts, query, ["title", "author", "subreddit"]),
    query.page,
    query.pageSize,
  );
}

export function getComments(query: DataQuery): PaginatedResponse<CommentRecord> {
  return createPaginatedResponse(
    applySearch(mockComments, query, ["body", "author", "subreddit", "parentPostTitle"]),
    query.page,
    query.pageSize,
  );
}

export function getSettings() {
  return settings;
}

export function saveSettings(payload: SettingsPayload) {
  settings = payload;
  return settings;
}

export function login(payload: LoginPayload): LoginResponse {
  const matchedUser =
    settings.users.find((user) => user.email.toLowerCase() === payload.email.toLowerCase()) ??
    settings.users[0];

  return {
    user: matchedUser,
    sessionExpiresAt: new Date(Date.now() + 1000 * 60 * settings.sessionTimeoutMinutes).toISOString(),
  };
}

export function parseDataQuery(searchParams: URLSearchParams): DataQuery {
  return {
    page: normalizeQueryValue(searchParams.get("page"), 1),
    pageSize: normalizeQueryValue(searchParams.get("pageSize"), 10),
    search: searchParams.get("search") ?? undefined,
    subreddit: searchParams.get("subreddit") ?? undefined,
  };
}

export function buildExportPayload(searchParams: URLSearchParams) {
  const format = searchParams.get("format") === "csv" ? "csv" : "json";
  const type = searchParams.get("type") === "comments" ? "comments" : "posts";
  const query = parseDataQuery(searchParams);
  const data = type === "comments" ? getComments(query).items : getPosts(query).items;

  if (format === "json") {
    return {
      body: JSON.stringify(data, null, 2),
      contentType: "application/json; charset=utf-8",
      filename: `reddit-crawler-${type}.json`,
    };
  }

  const rows = data.map((item) => Object.entries(item)) as Array<Array<[string, unknown]>>;
  const headers = rows.length > 0 ? rows[0].map(([key]) => key) : [];
  const csv = [
    headers.join(","),
    ...rows.map((row) =>
      headers
        .map((header) => {
          const value = row.find(([key]) => key === header)?.[1] ?? "";
          return `"${String(value).replaceAll("\"", "\"\"")}"`;
        })
        .join(","),
    ),
  ].join("\n");

  return {
    body: csv,
    contentType: "text/csv; charset=utf-8",
    filename: `reddit-crawler-${type}.csv`,
  };
}
