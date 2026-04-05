import { NextResponse } from "next/server";
import { getCrawlerStatus } from "@/lib/server/mock-api";

export async function GET() {
  return NextResponse.json(getCrawlerStatus());
}
