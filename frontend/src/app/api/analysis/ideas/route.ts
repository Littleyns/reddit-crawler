import { NextRequest, NextResponse } from "next/server";

const BACKEND_URL = process.env.BACKEND_API_URL || "http://localhost:8080/api";

export async function GET(req: NextRequest) {
  const url = new URL(req.url);
  // Extract relative path after /api/analysis — e.g. "/trends", "/sentiment"
  const relPath = "/nlp/tfidf";

  try {
    const targetUrl = `${BACKEND_URL}${relPath}${url.search}`;
    const response = await fetch(targetUrl, {
      method: "GET",
      headers: { Accept: "application/json" },
    });
    if (!response.ok) {
      return NextResponse.json(
        { error: `Backend returned ${response.status}` },
        { status: response.status },
      );
    }
    const data = await response.json();
    return NextResponse.json(data, { headers: { "Content-Type": "application/json" } });
  } catch (err: any) {
    console.warn("[Analytics Proxy] Backend unreachable:", err.message);
    return NextResponse.json(
      { error: "Backend unavailable" },
      { status: 502 },
    );
  }
}
