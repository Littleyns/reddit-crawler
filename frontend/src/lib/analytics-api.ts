import { api } from "@/lib/api";

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export interface SentimentItem {
  url: string;
  text: string;
  sentiment: "positive" | "neutral" | "negative";
  confidence: number;
  positiveCount: number;
  negativeCount: number;
  wordCount: number;
}

export interface IdeationItem {
  title: string;
  description: string;
  category: string;
}

export interface CategoryScoresMap {
  [key: string]: number;
}

export interface TrendItem {
  subreddit: string;
  totalPosts: number;
  avgScore: number;
  avgComments: number;
  engagementVelocity: number;
  categoryScores: CategoryScoresMap;
}

export interface KeywordItem {
  keyword: string;
  frequency: number;
}

export interface HeatmapItem {
  subreddit: string;
  positive: number;
  neutral: number;
  negative: number;
  total: number;
  positivePercent: number;
  neutralPercent: number;
  negativePercent: number;
}

export interface SentimentSummary {
  totalCount: number;
  positiveCount: number;
  neutralCount: number;
  negativeCount: number;
  positivePercent: number;
  neutralPercent: number;
  negativePercent: number;
}

export interface IdeationCategoryInfo {
  category: string;
  count: number;
}

export interface FullReport {
  sentimentSummary: SentimentSummary;
  sentimentDetails: SentimentItem[];
  ideaCategories: IdeationCategoryInfo[];
  totalIdeas: number;
  trends: TrendItem[];
  keywords: KeywordItem[];
  heatmap: HeatmapItem[];
}

// ---------------------------------------------------------------------------
// Client functions (via existing axios instance)
// ---------------------------------------------------------------------------

export async function fetchAnalyticsSentiment(): Promise<SentimentItem[]> {
  return (await api.get<SentimentItem[]>("/analysis/sentiment")).data;
}

export async function fetchAnalyticsIdeas(categoryFilter?: string): Promise<IdeationItem[]> {
  const params: Record<string, unknown> = {};
  if (categoryFilter !== null && categoryFilter !== "") {
    params.category = categoryFilter;
  }
  return (await api.get<IdeationItem[]>("/analysis/ideas", { params })).data;
}

export async function fetchAnalyticsTrends(): Promise<TrendItem[]> {
  return (await api.get<TrendItem[]>("/analysis/trends")).data;
}

export async function fetchAnalyticsKeywords(topN = 30): Promise<KeywordItem[]> {
  return (await api.get<KeywordItem[]>(`/analysis/keywords?topN=${topN}`)).data;
}

export async function fetchAnalyticsHeatmap(): Promise<HeatmapItem[]> {
  return (await api.get<HeatmapItem[]>("/analysis/heatmap")).data;
}

export async function fetchAnalyticsReport(): Promise<FullReport> {
  return (await api.get<FullReport>("/analysis/report")).data;
}
