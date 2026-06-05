/** @file Topic trend tracking endpoint Week W23 */

import { NextResponse } from 'next/server'
import type { NextRequest } from 'next/server'

const BASE_URL = process.env.NEXT_PUBLIC_API_BASE || 'http://localhost:8080'

export async function GET(request: NextRequest) {
    const { searchParams } = request.nextUrl
    
    const subreddit = searchParams.get('subreddit') ?? null
    const limit = Number(searchParams.get('limit')) ?? 30
    const window = searchParams.get('window') || 'daily'
    
    try {
        let url: string
        
        if (subreddit) {
            url = `${BASE_URL}/api/llm/trends?subreddit=${encodeURIComponent(subreddit)}&limit=${limit}&window=${window}`
        } else {
            url = `${BASE_URL}/api/llm/trends` +
                new URLSearchParams({ limit: String(limit), window })
        }
        
        const resp = await fetch(url)
        
        if (!resp.ok) {
            return NextResponse.json(
                { error: `Backend returned ${resp.status}` },
                { status: resp.status }
            )
        }
        
        const result = await resp.json()
        
        const trendLines = (result.topics || []).map((t: any) => ({
            topic: t.name,
            scores: ((t.scores || []) as number[]).slice(0, limit),
            timestamps: ((t.timestamps || []) as string[]).slice(0, limit),
        }))
        
        return NextResponse.json({ trends: trendLines, window })
    } catch (err) {
        console.error('[trends] route error:', err)
        return NextResponse.json(
            { error: 'Unable to fetch topic trends' },
            { status: 502 }
        )
    }
}

export async function POST(request: NextRequest) {
    try {
        const body = await request.json()
        const { keywords, days = 7 } = body
        
        if (!Array.isArray(keywords)) {
            return NextResponse.json(
                { error: 'keywords array is required' },
                { status: 400 }
            )
        }
        
        const url = `${BASE_URL}/api/llm/trends?days=${days}`
        
        await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ keywords }),
        })
        
        return NextResponse.json({ submitted: true, keywords })
    } catch (err) {
        console.error('[trends] POST error:', err)
        return NextResponse.json(
            { error: 'Internal server error' },
            { status: 500 }
        )
    }
}
