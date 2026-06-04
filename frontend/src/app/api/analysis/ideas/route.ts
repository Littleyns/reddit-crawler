import { NextRequest, NextResponse } from "next/server";

const BACKEND_URL = process.env.BACKEND_API_URL || "http://localhost:8080/api";

export async function GET(req: NextRequest) {
  const url = new URL(req.url);
  try {
    const response = await fetch(
      BACKEND_URL + "/analysis/ideas" + url.search,
      { method: "GET", headers: { Accept: "application/json" } },
    );
    if (!response.ok) {
      return NextResponse.json(
        { error: "Backend returned " + response.status },
        { status: response.status },
      );
    }
    const data = await response.json();
    return NextResponse.json(data, { headers: { "Content-Type": "application/json" } });
  } catch (err) {
    console.warn("[Analytics Proxy] Backend unreachable:", err);
    return NextResponse.json(
      { error: "Backend unavailable", seedData: true },
      { status: 502 },
    );
  }
}
