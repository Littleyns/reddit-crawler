import type { DataQuery } from "@/lib/types";

export function cn(...values: Array<string | false | null | undefined>) {
  return values.filter(Boolean).join(" ");
}

export function formatNumber(value: number) {
  return new Intl.NumberFormat("en-US", { maximumFractionDigits: 1 }).format(value);
}

export function formatDate(value: string) {
  return new Intl.DateTimeFormat("en-US", {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(new Date(value));
}

export function formatRelativeTime(value: string) {
  const diffMs = new Date(value).getTime() - Date.now();
  const diffMinutes = Math.round(diffMs / 60000);
  const formatter = new Intl.RelativeTimeFormat("en-US", { numeric: "auto" });

  if (Math.abs(diffMinutes) < 60) {
    return formatter.format(diffMinutes, "minute");
  }

  const diffHours = Math.round(diffMinutes / 60);
  if (Math.abs(diffHours) < 24) {
    return formatter.format(diffHours, "hour");
  }

  const diffDays = Math.round(diffHours / 24);
  return formatter.format(diffDays, "day");
}

export function buildExportUrl(
  format: "csv" | "json",
  type: "posts" | "comments",
  query: DataQuery,
) {
  const searchParams = new URLSearchParams({
    format,
    type,
    page: String(query.page ?? 1),
    pageSize: String(query.pageSize ?? 10),
  });

  if (query.search) {
    searchParams.set("search", query.search);
  }

  if (query.subreddit) {
    searchParams.set("subreddit", query.subreddit);
  }

  return `/api/data/export?${searchParams.toString()}`;
}

export function filterBySearch<T extends object>(
  items: T[],
  search: string,
  fields: Array<keyof T>,
) {
  if (!search.trim()) {
    return items;
  }

  const query = search.toLowerCase();
  return items.filter((item) =>
    fields.some((field) => String(item[field] ?? "").toLowerCase().includes(query)),
  );
}
