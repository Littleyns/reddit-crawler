import type {
  CommentRecord,
  CrawlerStatus,
  PaginatedResponse,
  PostRecord,
  SettingsPayload,
  StatsSummary,
} from "@/lib/types";

const now = new Date("2026-04-04T17:00:00.000Z");

export const mockStats: StatsSummary = {
  totalPosts: 18432,
  totalComments: 71408,
  totalSessions: 294,
  activeSubreddits: 12,
  successRate: 98.4,
  queueDepth: 37,
  activities: [
    {
      id: "act-1",
      title: "r/programming sync finished",
      description: "1,240 posts and 8,230 comments indexed in the last session.",
      occurredAt: new Date(now.getTime() - 1000 * 60 * 18).toISOString(),
      status: "success",
    },
    {
      id: "act-2",
      title: "Crawler workers scaled",
      description: "Raised worker count from 2 to 4 to absorb queue growth.",
      occurredAt: new Date(now.getTime() - 1000 * 60 * 43).toISOString(),
      status: "running",
    },
    {
      id: "act-3",
      title: "Rate-limit warning",
      description: "API backoff applied for r/technology after burst traffic.",
      occurredAt: new Date(now.getTime() - 1000 * 60 * 94).toISOString(),
      status: "warning",
    },
    {
      id: "act-4",
      title: "Export delivered",
      description: "JSON export generated for the moderation analytics team.",
      occurredAt: new Date(now.getTime() - 1000 * 60 * 148).toISOString(),
      status: "success",
    },
  ],
};

export const mockCrawlerStatus: CrawlerStatus = {
  isRunning: true,
  currentSubreddit: "machinelearning",
  progress: 68,
  mode: "collecting",
  activeWorkers: 4,
  requestsPerMinute: 126,
  lastRunAt: new Date(now.getTime() - 1000 * 60 * 6).toISOString(),
  config: {
    subreddit: "machinelearning",
    depth: 4,
    limit: 250,
    includeComments: true,
    keywords: "llm, agents, benchmark",
  },
};

export const mockPosts: PostRecord[] = [
  {
    id: "post-1",
    subreddit: "machinelearning",
    title: "Benchmarking agent architectures in long horizon tasks",
    author: "grad-ops",
    score: 1834,
    commentsCount: 248,
    createdAt: new Date(now.getTime() - 1000 * 60 * 36).toISOString(),
    url: "https://reddit.com/r/machinelearning/post-1",
  },
  {
    id: "post-2",
    subreddit: "technology",
    title: "Open source observability stack for large crawlers",
    author: "infra-signal",
    score: 874,
    commentsCount: 76,
    createdAt: new Date(now.getTime() - 1000 * 60 * 58).toISOString(),
    url: "https://reddit.com/r/technology/post-2",
  },
  {
    id: "post-3",
    subreddit: "dataengineering",
    title: "How are you deduplicating Reddit data across sessions?",
    author: "batchcraft",
    score: 642,
    commentsCount: 92,
    createdAt: new Date(now.getTime() - 1000 * 60 * 112).toISOString(),
    url: "https://reddit.com/r/dataengineering/post-3",
  },
  {
    id: "post-4",
    subreddit: "programming",
    title: "Crawler UI patterns that actually scale in operations",
    author: "ux-thread",
    score: 524,
    commentsCount: 44,
    createdAt: new Date(now.getTime() - 1000 * 60 * 170).toISOString(),
    url: "https://reddit.com/r/programming/post-4",
  },
  {
    id: "post-5",
    subreddit: "webdev",
    title: "Next.js dashboard ideas for internal tooling",
    author: "frontend-loop",
    score: 351,
    commentsCount: 29,
    createdAt: new Date(now.getTime() - 1000 * 60 * 240).toISOString(),
    url: "https://reddit.com/r/webdev/post-5",
  },
];

export const mockComments: CommentRecord[] = [
  {
    id: "comment-1",
    subreddit: "machinelearning",
    author: "signal-noise",
    body: "The retrieval layer is the real bottleneck once you get beyond naive agent loops.",
    score: 410,
    parentPostTitle: mockPosts[0].title,
    createdAt: new Date(now.getTime() - 1000 * 60 * 28).toISOString(),
  },
  {
    id: "comment-2",
    subreddit: "technology",
    author: "latency-scout",
    body: "You need queue-aware rate limiting or these crawlers fall apart under bursty demand.",
    score: 282,
    parentPostTitle: mockPosts[1].title,
    createdAt: new Date(now.getTime() - 1000 * 60 * 67).toISOString(),
  },
  {
    id: "comment-3",
    subreddit: "dataengineering",
    author: "warehouse-hand",
    body: "We hash title plus URL plus author because IDs alone were not enough in archived dumps.",
    score: 203,
    parentPostTitle: mockPosts[2].title,
    createdAt: new Date(now.getTime() - 1000 * 60 * 103).toISOString(),
  },
  {
    id: "comment-4",
    subreddit: "programming",
    author: "ops-visible",
    body: "If your operators cannot see worker saturation in one glance, the dashboard is missing the point.",
    score: 194,
    parentPostTitle: mockPosts[3].title,
    createdAt: new Date(now.getTime() - 1000 * 60 * 130).toISOString(),
  },
];

export const mockSettings: SettingsPayload = {
  apiKey: "rtk_live_xxxx_xxxx_xxxx",
  defaultSubreddit: "machinelearning",
  defaultDepth: 4,
  defaultLimit: 250,
  autoExport: true,
  exportFormat: "json",
  sessionTimeoutMinutes: 45,
  users: [
    { id: "usr-1", name: "Amina Rahman", email: "amina@arabtooling.com", role: "admin" },
    { id: "usr-2", name: "Yousef Ali", email: "yousef@arabtooling.com", role: "analyst" },
    { id: "usr-3", name: "Nora Haddad", email: "nora@arabtooling.com", role: "viewer" },
  ],
};

export function createPaginatedResponse<T>(
  source: T[],
  page = 1,
  pageSize = 10,
): PaginatedResponse<T> {
  const start = (page - 1) * pageSize;
  const items = source.slice(start, start + pageSize);

  return {
    items,
    page,
    pageSize,
    total: source.length,
    totalPages: Math.max(1, Math.ceil(source.length / pageSize)),
  };
}
