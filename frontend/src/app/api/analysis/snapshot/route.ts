import { NextRequest, NextResponse } from "next/server"

const BACKEND_URL = process.env.BACKEND_API_URL || "http://backend:8080/api"

export async function GET(req: NextRequest) {
  try {
    const url = new URL(`${BACKEND_URL}/analytics/snapshot`)
    
    for (const [key, value] of req.nextUrl.searchParams) {
      url.searchParams.set(key, value)
    }

    const resp = await fetch(url, { cache: "no-store" })
    
    if (!resp.ok) {
      return NextResponse.json({ error: `Backend returned ${resp.status}` }, { status: 502 })
    }
    
    const data = await resp.json()
    return NextResponse.json(data)
  } catch (err: any) {
    console.warn("[analysis/snapshot proxy] Error:", err.message)
    return NextResponse.json({ error: "Backend unavailable" }, { status: 502 })
  }
}

export const revalidate = 0
