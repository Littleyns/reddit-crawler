import { NextResponse } from "next/server";
import { stopCrawler } from "@/lib/server/mock-api";

export async function POST() {
  return NextResponse.json(stopCrawler());
}
