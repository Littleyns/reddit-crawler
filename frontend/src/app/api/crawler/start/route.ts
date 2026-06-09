import { NextResponse } from "next/server";
import type { CrawlConfig } from "@/lib/types";

const BACKEND = process.env.BACKEND_URL || "http://backend:8080";

export async function POST(request: Request) {
  const config = (await request.json()) as CrawlConfig;

  try {
    const res = await fetch(`${BACKEND}/api/crawler/start`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ subreddit: config.subreddit, limit: config.limit }),
    });

    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      return NextResponse.json(
        { error: err.message || `Start failed (${res.status})` },
        { status: res.status }
      );
    }

    const data = await res.json();
    return NextResponse.json(data);
  } catch (err: any) {
    console.error("POST /api/crawler/start proxy error:", err.message);
    return NextResponse.json(
      { error: "Backend unavailable" },
      { status: 502 }
    );
  }
}
