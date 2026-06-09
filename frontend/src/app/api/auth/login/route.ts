import { NextResponse } from "next/server";
import type { LoginPayload } from "@/lib/types";

const BACKEND = process.env.BACKEND_URL || "http://backend:8080";

export async function POST(request: Request) {
  const payload = (await request.json()) as LoginPayload;

  try {
    const res = await fetch(`${BACKEND}/api/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email: payload.email, password: payload.password }),
    });

    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      return NextResponse.json(
        { error: err.message || `Login failed (${res.status})` },
        { status: res.status }
      );
    }

    const data = await res.json();
    return NextResponse.json(data);
  } catch (err: any) {
    console.error("POST /api/auth/login proxy error:", err.message);
    return NextResponse.json(
      { error: "Backend unavailable" },
      { status: 502 }
    );
  }
}
