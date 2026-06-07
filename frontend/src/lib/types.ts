export interface ActivityItem {
  id: string;
  title: string;
  description: string;
  occurredAt: string;
  status: "success" | "running" | "warning" | "error";
}

export interface StatsSummary {
  totalPosts: number;
  totalComments: number;
  totalSessions: number;
  activeSubreddits: number;
  successRate: number;
  queueDepth: number;
  activities: ActivityItem[];
}

export interface CrawlConfig {
  subreddit: string;
  depth: number;
  limit: number;
  includeComments: boolean;
  keywords?: string;
}

export interface CrawlerStatus {
  isRunning: boolean;
  currentSubreddit: string | null;
  progress: number;
  mode: "idle" | "warming_up" | "collecting" | "stopping";
  activeWorkers: number;
  requestsPerMinute: number;
  lastRunAt: string;
  config: CrawlConfig;
}

export interface PostRecord {
  id: string;
  subreddit: string;
  title: string;
  author: string;
  score: number;
  commentsCount: number;
  createdAt: string;
  url: string;
}

export interface CommentRecord {
  id: string;
  subreddit: string;
  author: string;
  body: string;
  score: number;
  parentPostTitle: string;
  createdAt: string;
}

export interface PaginatedResponse<T> {
  items: T[];
  page: number;
  pageSize: number;
  total: number;
  totalPages: number;
}

export interface UserSummary {
  id: string;
  name: string;
  email: string;
  role: "admin" | "analyst" | "viewer";
}

export interface SettingsPayload {
  apiKey: string;
  defaultSubreddit: string;
  defaultDepth: number;
  defaultLimit: number;
  autoExport: boolean;
  exportFormat: "csv" | "json";
  sessionTimeoutMinutes: number;
  users: UserSummary[];
}

export interface LoginPayload {
  email: string;
  password: string;
}

export interface LoginResponse {
  user: UserSummary;
  sessionExpiresAt: string;
}

export interface DataQuery {
  page?: number;
  pageSize?: number;
  search?: string;
  subreddit?: string;
}

export interface ApiErrorShape {
  message: string;
  status?: number;
}

// P4-2: types for crawl jobs (real vs mock)
export interface CrawlJob {
  id: string;
  name: string;
  subreddit: string;
  status: 'running' | 'queued' | 'completed' | 'failed' | string;
  progress: number;
  priority?: 'high' | 'medium' | 'low';
  workersAssigned?: number;
  queuePosition?: number;
  retryCount?: number;
  maxRetries?: number;
  estimatedMinutes?: number;
  startedAt?: string;
  completedAt?: string;
}

export interface CrawlJobBackend {
  jobId: string;
  subreddit: string;
  status: string;
  startedAt: string | null;
  completedAt: string | null;
  config: string;
}
