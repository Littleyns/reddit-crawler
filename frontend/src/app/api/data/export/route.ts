import { NextResponse } from "next/server";
import { buildExportPayload } from "@/lib/server/mock-api";

export async function GET(request: Request) {
  const { searchParams } = new URL(request.url);
  const payload = buildExportPayload(searchParams);

  return new NextResponse(payload.body, {
    headers: {
      "content-type": payload.contentType,
      "content-disposition": `attachment; filename="${payload.filename}"`,
    },
  });
}
