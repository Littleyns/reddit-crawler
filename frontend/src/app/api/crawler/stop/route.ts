import { NextResponse } from "next/server";

const BACKEND = process.env.BACKEND_URL || "http://localhost:8080";

export async function POST() {
  try {
    const res = await fetch(`${BACKEND}/api/crawler/stop`, {
      method: "POST",
    });

    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      return NextResponse.json(
        { error: err.message || `Stop failed (${res.status})` },
        { status: res.status }
      );
    }

    const data = await res.json();
    return NextResponse.json(data);
  } catch (err: any) {
    console.error("POST /api/crawler/stop proxy error:", err.message);
    return NextResponse.json(
      { error: "Backend unavailable", status: "IDLE" },
      { status: 502 }
    );
  }
}
