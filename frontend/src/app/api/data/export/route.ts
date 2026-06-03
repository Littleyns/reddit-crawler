import { NextResponse } from "next/server";

const BACKEND = process.env.BACKEND_URL || "http://localhost:8080";

export async function GET(request: Request) {
  const { searchParams } = new URL(request.url);
  const format = searchParams.get("format") || "csv";
  const subreddit = searchParams.get("subreddit");
  const search = searchParams.get("search");

  const params = new URLSearchParams({ format });
  if (subreddit) params.set("subreddit", subreddit);
  if (search) params.set("search", search);

  try {
    const res = await fetch(`${BACKEND}/api/data/export?${params}`);

    if (!res.ok) {
      return NextResponse.json(
        { error: `Export failed (${res.status})` },
        { status: res.status }
      );
    }

    const blob = await res.blob();
    const disposition = res.headers.get("content-disposition") || 'attachment; filename="reddit_crawler_export.csv"';
    const contentType = res.headers.get("content-type") || (format === "json" ? "application/json" : "text/csv");

    return new NextResponse(blob, {
      headers: {
        "content-type": contentType,
        "content-disposition": disposition,
      },
    });
  } catch (err: any) {
    console.error("GET /api/data/export proxy error:", err.message);
    return NextResponse.json(
      { error: "Export backend unavailable" },
      { status: 502 }
    );
  }
}
