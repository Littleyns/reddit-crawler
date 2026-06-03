import { NextResponse } from "next/server";
import { saveLLMConfig, getLLMConfig } from "@/lib/server-llm-store";

export async function GET() {
  try {
    const config = getLLMConfig();
    return NextResponse.json(config);
  } catch (error) {
    console.error("getLLMConfig failed:", error);
    return NextResponse.json(
      { provider: "", baseUrl: "", modelId: "", maskedKey: "" },
      { status: 500 }
    );
  }
}

export async function POST(req: Request) {
  try {
    let body: Partial<{ provider: string; apiKey: string; baseUrl: string; modelId: string }> = {};

    const contentType = req.headers.get("content-type") || "";
    if (contentType.includes("form-urlencoded")) {
      const formData = await req.formData();
      for (const [key, value] of formData.entries()) {
        (body as any)[key] = value;
      }
    } else {
      body = await req.json().catch(() => ({}));
    }

    const result = saveLLMConfig({
      provider: body.provider || "openrouter",
      apiKey: body.apiKey || "",
      baseUrl: body.baseUrl || "",
      modelId: body.modelId || "",
    });

    if (!result) {
      return NextResponse.json(
        { error: "Failed to save configuration" },
        { status: 500 }
      );
    }

    return NextResponse.json({ success: true });
  } catch (error) {
    console.error("POST /api/llm/config failed:", error);
    return NextResponse.json(
      { error: "Request parse or save failed" },
      { status: 400 }
    );
  }
}
