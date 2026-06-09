import { NextResponse } from "next/server";

const BACKEND = process.env.BACKEND_URL || "http://backend:8080";

export async function GET(request: Request) {
  const { searchParams } = new URL(request.url);
  const page = searchParams.get("page") || "1";
  const pageSize = searchParams.get("pageSize") || "25";
  const subreddit = searchParams.get("subreddit");
  const search = searchParams.get("search");

  const params = new URLSearchParams({ page, pageSize });
  if (subreddit) params.set("subreddit", subreddit);
  if (search) params.set("search", search);

  try {
    const res = await fetch(`${BACKEND}/api/data/comments?${params}`, {
      cache: "no-store"
    });

    if (!res.ok) {
      return NextResponse.json(
        { error: `Comments fetch failed (${res.status})`, items: [], page: 1, pageSize: 25, total: 0, totalPages: 1 },
        { status: res.status }
      );
    }

    const data = await res.json();
    return NextResponse.json(data);
  } catch (err: any) {
    console.error("GET /api/data/comments proxy error:", err.message);
    return NextResponse.json(
      { items: [], page: 1, pageSize: parseInt(pageSize), total: 0, totalPages: 1 },
      { status: 502 }
    );
  }
}
