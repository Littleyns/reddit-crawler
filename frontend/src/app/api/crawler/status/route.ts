import { NextResponse } from "next/server";

const BACKEND = process.env.BACKEND_URL || "http://localhost:8080";

export async function GET() {
  try {
    const res = await fetch(`${BACKEND}/api/crawler/status`, {
      cache: "no-store",
    });

    if (!res.ok) {
      return NextResponse.json(
        { error: `Status check failed (${res.status})`, status: "IDLE" },
        { status: res.status }
      );
    }

    const data = await res.json();
    return NextResponse.json({
      ...data,
      // Normalize frontend expectations from backend's response shape
      activeJobId: data.jobId || null,
    });
  } catch (err: any) {
    console.error("GET /api/crawler/status proxy error:", err.message);
    return NextResponse.json(
      { status: "IDLE", activeJobId: null },
      { status: 502 }
    );
  }
}
