import { NextResponse } from "next/server";

const KNOWN_MODELS: Record<string, string[]> = {
  openrouter: [
    "anthropic/claude-sonnet-4",
    "google/gemini-2.5-pro",
    "google/gemini-2.0-flash",
    "openai/gpt-4o",
    "meta-llama/llama-3.3-70b-instruct",
  ],
  openai: [
    "gpt-4o",
    "gpt-4o-mini",
    "gpt-4-turbo",
    "gpt-3.5-turbo",
  ],
  azure: [
    "gpt-4-turbo",
    "gpt-4",
    "gpt-35-turbo",
  ],
};

export async function POST(req: Request) {
  try {
    const body = await req.json();
    const provider = typeof body.provider === "string" ? body.provider : "openrouter";
    const models = KNOWN_MODELS[provider] || [];

    return NextResponse.json({ models });
  } catch (error) {
    console.error("Failed to fetch models:", error);
    return NextResponse.json({ models: [] });
  }
}
