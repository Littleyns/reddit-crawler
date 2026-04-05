import { NextResponse } from "next/server";
import { login } from "@/lib/server/mock-api";
import type { LoginPayload } from "@/lib/types";

export async function POST(request: Request) {
  const payload = (await request.json()) as LoginPayload;
  return NextResponse.json(login(payload));
}
