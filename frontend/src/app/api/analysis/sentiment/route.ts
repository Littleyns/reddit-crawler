/** @file Real-time sentiment analysis endpoint
Week W23: NLP pipeline integration for live text analysis */

import { NextResponse } from 'next/server'
import type { NextRequest } from 'next/server'

const BASE_URL = process.env.NEXT_PUBLIC_API_BASE || 'http://localhost:8080'

export async function POST(request: NextRequest) {
    try {
        const body = await request.json()
        const { text, method = 'vader' } = body
        
        if (!text || typeof text !== 'string') {
            return NextResponse.json(
                { error: 'text field is required (string)' },
                { status: 400 }
            )
        }
        
        const url = `${BASE_URL}/api/sentiment/analyze?method=${method}`
        
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
        return NextResponse.json(result)
    } catch (err) {
        console.error('[sentiment] route error:', err)
        return NextResponse.json(
            { error: 'Internal server error' },
            { status: 500 }
        )
    }
}

export async function GET(request: NextRequest) {
    const { searchParams } = request.nextUrl
    
    // Allow passing raw text via query string for quick checks
    const text = searchParams.get('text')
    if (text) {
        return POST({ ...request, json: () => Promise.resolve({ text }) } as unknown as NextRequest)
    }
    
    return NextResponse.json(
        { data: [], help: 'POST /api/analysis/sentiment with { text } or use GET ?text=' },
        { status: 200 }
    )
}
