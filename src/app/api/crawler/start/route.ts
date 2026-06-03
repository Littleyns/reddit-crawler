import { NextResponse } from "next/server";
import { startCrawler } from "@/lib/server/mock-api";
import type { CrawlConfig } from "@/lib/types";

export async function POST(request: Request) {
  const config = (await request.json()) as CrawlConfig;
  return NextResponse.json(startCrawler(config));
}
