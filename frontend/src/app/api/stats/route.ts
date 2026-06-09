import { NextResponse } from "next/server";

const BACKEND = process.env.BACKEND_URL || "http://backend:8080";

export async function GET() {
  try {
    const res = await fetch(`${BACKEND}/api/stats`, {
      cache: "no-store"
    });

    if (!res.ok) {
      return NextResponse.json(
        { error: `Stats fetch failed (${res.status})`, totalPosts: 0, totalComments: 0, totalSessions: 0, activeSubreddits: 0, successRate: 0, queueDepth: 0, activities: [], subreddits: ["n/a"] },
        { status: res.status }
      );
    }

    const data = await res.json();
    return NextResponse.json(data);
  } catch (err: any) {
    console.error("GET /api/stats proxy error:", err.message);
    return NextResponse.json(
      { totalPosts: 0, totalComments: 0, totalSessions: 0, activeSubreddits: 0, successRate: 0, queueDepth: 0, activities: [], subreddits: ["n/a"] },
      { status: 502 }
    );
  }
}
