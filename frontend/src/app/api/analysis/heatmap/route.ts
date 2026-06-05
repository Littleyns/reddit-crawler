/** @file Heatmap data endpoint for sentiment distribution
Week W23: Analytics dashboard - Sentiment visualization API */

import { NextResponse } from 'next/server'
import type { NextRequest } from 'next/server'

const BASE_URL = process.env.NEXT_PUBLIC_API_BASE || 'http://localhost:8080'

export async function GET(request: NextRequest) {
    const { searchParams } = request.nextUrl
    
    const subreddit = searchParams.get('subreddit') ?? null
    const days = Number(searchParams.get('days')) ?? 7
    
    try {
        const url = `${BASE_URL}/api/analytics/heatmap` +
            new URLSearchParams({
                ...(subreddit ? { subreddit } : {}),
                days: String(days)
            })
        
        const resp = await fetch(url, { cache: 'no-store' })
        
        if (!resp.ok) {
            return NextResponse.json(
                { error: `Backend returned ${resp.status}` },
                { status: resp.status }
            )
        }
        
        const data = await resp.json()
        
        const chartData = (data.charts || []).map((chart: any) => ({
            time: chart.time,
            positive: chart.positive ?? 0,
            neutral: chart.neutral ?? 0,
            negative: chart.negative ?? 0,
            total: chart.total ?? 0,
        }))
        
        return NextResponse.json({ data: chartData })
    } catch (err) {
        console.error('[heatmap] fetch error:', err)
        return NextResponse.json(
            { error: 'Unable to reach analytics backend' },
            { status: 502 }
        )
    }
}
