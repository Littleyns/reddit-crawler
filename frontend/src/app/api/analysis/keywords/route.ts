/** @file Keyword extraction endpoint using TF-IDF and KeyBERT Week W23 */

import { NextResponse } from 'next/server'
import type { NextRequest } from 'next/server'

const BASE_URL = process.env.NEXT_PUBLIC_API_BASE || 'http://localhost:8080'

export async function GET(request: NextRequest) {
    const { searchParams } = request.nextUrl
    
    const text = searchParams.get('text')
    const method = searchParams.get('method') ?? 'tfidf'
    const topN = Number(searchParams.get('topk')) ?? 20
    
    if (!text) {
        return NextResponse.json(
            { error: 'text query parameter is required' },
            { status: 400 }
        )
    }
    
    try {
        const url = `${BASE_URL}/api/keywords/extract?method=${method}&topk=${topN}`
        
        const resp = await fetch(url, {
            headers: { 'Content-Type': 'application/json' },
        })
        
        if (!resp.ok) {
            return NextResponse.json(
                { error: `Backend returned ${resp.status}` },
                { status: resp.status }
            )
        }
        
        const result = await resp.json()
        
        const keywords = (result.keywords || []).slice(0, topN).map((kw: string, i: number) => ({
            keyword: kw,
            tfidf: result.tfidf?.[i] ?? 0,
        }))
        
        return NextResponse.json({ keywords, method, count: keywords.length })
    } catch (err) {
        console.error('[keywords] route error:', err)
        return NextResponse.json(
            { error: 'Unable to reach keyword service' },
            { status: 502 }
        )
    }
}
