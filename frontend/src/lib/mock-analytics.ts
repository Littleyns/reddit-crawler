// src/lib/mock-analytics.ts — Mock data for Reddit Analytics Dashboard

export interface SubredditStats {
  subreddit: string;
  postCount: number;
  commentCount: number;
  sentimentScore: number; // -1 (negative) to 1 (positive)
  avgThreadsDay: number;
}

export interface ThreadInsight {
  title: string;
  subtitle: string;
  keywords: string[];
  category: 'needs' | 'idea' | 'project';
  confidence: number;
}

export interface AnalyticsData {
  subredditStats: SubredditStats[];
  dailyActivity: { date: string; posts: number; comments: number }[];
  sentimentDistribution: { label: string; count: number }[];
  topKeywords: { term: string; frequency: number }[];
  insights: ThreadInsight[];
  weeklyCrawl: { day: string; collected: number }[];
}

export function generateMockAnalyticsData(): AnalyticsData {
  const subreddits = [
    'r/webdev', 'r/startups', 'r/SaaS', 'r/Entrepreneur',
    'r/AI_developers', 'r/technology', 'r/learnprogramming'
  ];

  return {
    subredditStats: subreddits.map(sub => ({
      subreddit: sub,
      postCount: Math.floor(Math.random() * 1500) + 50,
      commentCount: Math.floor(Math.random() * 8000) + 200,
      sentimentScore: parseFloat((Math.random() * 2 - 1).toFixed(2)),
      avgThreadsDay: Math.floor(Math.random() * 50) + 5,
    })),
    dailyActivity: Array.from({ length: 30 }, (_, i) => ({
      date: `2026-06-${String(i + 1).padStart(2, '0')}`,
      posts: Math.floor(Math.random() * 450) + 100,
      comments: Math.floor(Math.random() * 1200) + 200,
    })),
    sentimentDistribution: [
      { label: 'Positive', count: Math.floor(Math.random() * 600) + 150 },
      { label: 'Neutral', count: Math.floor(Math.random() * 800) + 300 },
      { label: 'Negative', count: Math.floor(Math.random() * 300) + 50 },
    ],
    topKeywords: [
      { term: 'React', frequency: Math.floor(Math.random() * 2000) + 800 },
      { term: 'API', frequency: Math.floor(Math.random() * 1500) + 600 },
      { term: 'SaaS', frequency: Math.floor(Math.random() * 1200) + 400 },
      { term: 'AI', frequency: Math.floor(Math.random() * 1800) + 700 },
      { term: 'automation', frequency: Math.floor(Math.random() * 900) + 300 },
    ],
    insights: [
      { title: 'Looking for Fullstack Dev (Node/React)', subtitle: "User in r/softwareguru seeking a skilled developer to build a SaaS dashboard from scratch. Budget $5k+.", keywords: ['hiring', 'fullstack', 'dashboard'], category: 'needs', confidence: 0.92 },
      { title: 'Auto-Budget App Idea using AI LLMs', subtitle: "Has an idea for an auto-budget app that leverages LLMs to analyze spending patterns and suggest optimizations.", keywords: ['idea', 'ai', 'fintech'], category: 'idea', confidence: 0.78 },
      { title: 'Need API wrapper for Shopify data export', subtitle: "Looking to build a tool that pulls and standardizes all Shopify sales reports into a single dashboard.", keywords: ['api', 'ecommerce', 'automation'], category: 'project', confidence: 0.85 },
      { title: 'SaaS founders: need ML engineer for product analytics', subtitle: "Bootstrapped startup building AI-powered product analytics. Looking for someone with experience in predictive models.", keywords: ['hiring', 'ml', 'saas'], category: 'needs', confidence: 0.89 },
      { title: 'Chatbot for customer service — who can build it?', subtitle: "Looking to integrate a chatbot into our e-commerce site similar to Intercom but using open-source LLMs.", keywords: ['hiring', 'chatbot', 'ai'], category: 'needs', confidence: 0.87 },
      { title: 'Chrome extension idea: Reddit keyword alerts', subtitle: "Would love a Chrome extension that alerts you when keywords related to your project appear in r/webdev or r/startups.", keywords: ['idea', 'chrome-extension', 'reddit'], category: 'idea', confidence: 0.73 },
    ],
    weeklyCrawl: Array.from({ length: 7 }, (_, i) => ({
      day: ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'][i],
      collected: Math.floor(Math.random() * 3000) + 500,
    })),
  };
}
