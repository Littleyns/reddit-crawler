import { NextResponse } from "next/server";

export async function POST(req: Request) {
  const start = Date.now();

  let body: any;
  try {
    body = await req.json();
  } catch {
    return NextResponse.json(
      { success: false, latencyMs: 0, message: "Invalid request body" },
      { status: 400 }
    );
  }

  const apiKey = typeof body.apiKey === "string" ? body.apiKey : "";
  const baseUrl = typeof body.baseUrl === "string" ? body.baseUrl : "";
  const modelId = typeof body.modelId === "string" ? body.modelId : "";

  if (!baseUrl || !apiKey) {
    return NextResponse.json(
      { success: false, latencyMs: 0, message: "Missing baseUrl or apiKey" },
      { status: 400 }
    );
  }

  const fullUrl = baseUrl.replace(/\/$/, "") + "/chat/completions";

  let responseText = "";
  try {
    // Use Node.js fetch in Next.js API route context
    const resp = await globalThis.fetch(fullUrl, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Authorization": "Bearer " + apiKey,
      },
      body: JSON.stringify({
        model: modelId || "",
        messages: [{ role: "user", content: "ping" }],
        max_tokens: 1,
      }),
    });

    const elapsed = Date.now() - start;

    if (resp.status >= 200 && resp.status < 300) {
      try { responseText = await resp.text(); } catch {}
      return NextResponse.json({ 
        success: true, 
        latencyMs: elapsed, 
        message: "Connected (" + elapsed + "ms)" 
      });
    } else {
      return NextResponse.json({
        success: false,
        latencyMs: Date.now() - start,
        message: "Auth failed (HTTP " + resp.status + ")"
      }, { status: 401 });
    }
  } catch (err: any) {
    const elapsed = Date.now() - start;
    return NextResponse.json({
      success: false, 
      latencyMs: elapsed,
      message: "Connection failed: " + (err.message || "timeout")
    }, { status: 504 });
  }
}
