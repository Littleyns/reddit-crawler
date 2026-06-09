import { NextRequest, NextResponse } from "next/server";

// Proxy -> spring boot backend (NO localhost fallback!)
const BACKEND_URL = process.env.BACKEND_API_URL || "http://backend:8080/api";

export async function GET(req: NextRequest) {
  try {
    const url = `${BACKEND_URL}/analytics/snapshot`;
    
    const resp = await fetch(url, { cache: "no-store" });
    
    if (!resp.ok) {
      return NextResponse.json({ error: "Backend unavailable" }, { status: 502 });
    }
    
    const data = await resp.json();
    return NextResponse.json(data);
  } catch (err: any) {
    console.warn("[analytics snapshot route] Error:", err.message);
    return NextResponse.json({ error: "Backend unavailable" }, { status: 502 });
  }
}
