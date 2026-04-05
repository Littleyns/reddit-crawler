export const navigation = [
  { href: "/", label: "Overview" },
  { href: "/dashboard", label: "Dashboard" },
  { href: "/controls", label: "Controls" },
  { href: "/data", label: "Data" },
  { href: "/settings", label: "Settings" }
];

export const statCards = [
  { label: "Tracked Subreddits", value: "24", delta: "+6 this week" },
  { label: "Posts Indexed", value: "182.4K", delta: "+14.8%" },
  { label: "Comment Throughput", value: "5.8K/min", delta: "P95 latency 1.9s" },
  { label: "Crawler Uptime", value: "99.94%", delta: "No incidents in 12 days" }
];

export const pipelineStages = [
  { name: "Ingestion", value: "1.2M", detail: "Queue depth stable" },
  { name: "Enrichment", value: "924K", detail: "Entity tagging active" },
  { name: "Storage", value: "880K", detail: "Compression 34%" },
  { name: "Exports", value: "148", detail: "JSON, CSV, webhook" }
];

export const activityFeed = [
  "Crawler cluster synced with subreddit priority map.",
  "Export bundle generated for moderation intelligence team.",
  "API retry threshold automatically tuned after transient spike.",
  "New keyword alert set deployed for brand monitoring."
];

export const tableRows = [
  {
    subreddit: "r/MachineLearning",
    posts: "18,402",
    comments: "311,220",
    sentiment: "Positive",
    freshness: "2m ago"
  },
  {
    subreddit: "r/startups",
    posts: "9,118",
    comments: "142,990",
    sentiment: "Mixed",
    freshness: "4m ago"
  },
  {
    subreddit: "r/technology",
    posts: "31,482",
    comments: "401,330",
    sentiment: "Neutral",
    freshness: "1m ago"
  },
  {
    subreddit: "r/datascience",
    posts: "7,930",
    comments: "126,440",
    sentiment: "Positive",
    freshness: "5m ago"
  }
];
