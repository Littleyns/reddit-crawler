/** @file Full analytics aggregation report endpoint Week W23 */

import { NextResponse } from 'next/server'
import type { NextRequest } from 'next/server'

const BASE_URL = process.env.NEXT_PUBLIC_API_BASE || 'http://localhost:8080'

export async function GET(request: NextRequest) {
    const { searchParams } = request.nextUrl
    
    const days = Number(searchParams.get('days')) ?? 7
    const subreddit = searchParams.get('subreddit') ?? null
    const include = (searchParams.get('include') ?? 'all').split(',').filter(Boolean)
    
    try {
        const url = new URL(`${BASE_URL}/api/analytics/report`)
        url.searchParams.set('days', String(days))
        if (subreddit) url.searchParams.set('subreddit', subreddit)
        url.searchParams.set('include', 'all')
        
        const resp = await fetch(url.toString(), { cache: 'no-store' })
        
        if (!resp.ok) {
            return NextResponse.json(
                { error: `Backend returned ${resp.status}` },
                { status: resp.status }
            )
        }
        
        const data = await resp.json()
        
        return NextResponse.json({
            overall: {
                totalPosts: data.total_posts ?? 0,
                subredditsCovered: data.subreddit_stats?.length ?? 0,
                avgSentiment: data.overall_sentiment?.mean ?? 0,
                topKeywords: (data.top_keywords || []).slice(0, 15),
            },
            sentimentByCategory: data.sentiment_by_category || [],
            heatmapPreview: data.heatmap_preview || [],
            keywordFrequency: (data.keyword_frequency || []).slice(0, 20).map((kw: { word: string; count: number }) => ({
                word: kw.word,
                count: kw.count,
            })),
            metadata: { days, subreddit, timestamp: data.timestamp },
        })
    } catch (err) {
        console.error('[report] route error:', err)
        return NextResponse.json(
            { error: 'Unable to generate analytics report' },
            { status: 502 }
        )
    }
}
