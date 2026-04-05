import { NextResponse } from "next/server";
import { getStats } from "@/lib/server/mock-api";

export async function GET() {
  return NextResponse.json(getStats());
}
