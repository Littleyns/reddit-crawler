/** @file Idea extraction endpoint with auto-categorization Week W23 */

import { NextResponse } from 'next/server'
import type { NextRequest } from 'next/server'

const BASE_URL = process.env.NEXT_PUBLIC_API_BASE || 'http://localhost:8080'

export async function POST(request: NextRequest) {
    try {
        const body = await request.json()
        const { text, min_score = 0.5 } = body
        
        if (!text) {
            return NextResponse.json(
                { error: 'text field is required' },
                { status: 400 }
            )
        }
        
        const url = `${BASE_URL}/api/ideas/extract?min_score=${min_score}`
        
        const resp = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ text }),
        })
        
        if (!resp.ok) {
            return NextResponse.json(
                { error: `Backend returned ${resp.status}` },
                { status: resp.status }
            )
        }
        
        const result = await resp.json()
        
        const extractedId = (result.ideas || []).map((idea: any) => ({
            text: idea.text,
            category: idea.category ?? 'uncategorized',
            confidence: idea.confidence ?? 0,
            sentiment: idea.sentiment ?? 'neutral',
            tags: idea.tags || [],
        }))
        
        return NextResponse.json({ extractedIdeas: extractedId, count: extractedId.length })
    } catch (err) {
        console.error('[ideas] route error:', err)
        return NextResponse.json(
            { error: 'Unable to extract ideas from text' },
            { status: 502 }
        )
    }
}

export async function GET(request: NextRequest) {
    const { searchParams } = request.nextUrl
    
    // Retrieve stored ideas for a specific source (e.g., subreddit + date range)
    const subreddit = searchParams.get('subreddit') ?? null
    const limit = Number(searchParams.get('limit')) ?? 20
    
    if (!subreddit) {
        return NextResponse.json(
            { error: 'subreddit query parameter required' },
            { status: 400 }
        )
    }
    
    try {
        const url = `${BASE_URL}/api/ideas/source/${encodeURIComponent(subreddit)}?limit=${limit}`
        
        const resp = await fetch(url)
        
        if (!resp.ok) {
            return NextResponse.json(
                { error: `Backend returned ${resp.status}` },
                { status: resp.status }
            )
        }
        
        const result = await resp.json()
        return NextResponse.json(result)
    } catch (err) {
        console.error('[ideas] GET error:', err)
        return NextResponse.json(
            { error: 'Unable to retrieve ideas' },
            { status: 502 }
        )
    }
}
