import { NextResponse } from "next/server";

const BACKEND = process.env.BACKEND_URL || "http://backend:8080";

export async function GET() {
  try {
    const res = await fetch(`${BACKEND}/api/settings`, { cache: "no-store" });

    if (!res.ok) {
      return NextResponse.json(
        { error: `Settings fetch failed (${res.status})` },
        { status: res.status }
      );
    }

    const data = await res.json();
    return NextResponse.json(data);
  } catch (err: any) {
    console.error("GET /api/settings proxy error:", err.message);
    return NextResponse.json(
      { apiKey: "", defaultSubreddit: "machinelearning", defaultDepth: 4, defaultLimit: 250, autoExport: false, exportFormat: "csv", sessionTimeoutMinutes: 45 },
      { status: 502 }
    );
  }
}

export async function POST(req: Request) {
  try {
    const body = await req.json();
    const res = await fetch(`${BACKEND}/api/settings`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });

    if (!res.ok) {
      return NextResponse.json(
        { error: `Settings update failed (${res.status})` },
        { status: res.status }
      );
    }

    const data = await res.json();
    return NextResponse.json(data);
  } catch (err: any) {
    console.error("POST /api/settings proxy error:", err.message);
    return NextResponse.json(
      { error: "Settings backend unavailable" },
      { status: 502 }
    );
  }
}
