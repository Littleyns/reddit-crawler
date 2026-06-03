import { NextResponse } from "next/server";
import { getSettings, saveSettings } from "@/lib/server/mock-api";
import type { SettingsPayload } from "@/lib/types";

export async function GET() {
  return NextResponse.json(getSettings());
}

export async function POST(request: Request) {
  const payload = (await request.json()) as SettingsPayload;
  return NextResponse.json(saveSettings(payload));
}
