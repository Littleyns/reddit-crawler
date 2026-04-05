import { NextResponse } from "next/server";
import { getPosts, parseDataQuery } from "@/lib/server/mock-api";

export async function GET(request: Request) {
  const { searchParams } = new URL(request.url);
  return NextResponse.json(getPosts(parseDataQuery(searchParams)));
}
