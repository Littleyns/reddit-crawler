#!/usr/bin/env node
/**
 * Transform all Next.js API routes from mock-data stubs → real Spring Boot backend proxy.
 * Reads each route.ts file, replaces the fetch-to-backend call, and writes it in place.
 */
import { readFileSync, writeFileSync, readdirSync } from "fs";
import { join } from "path";

const BACKEND_URL = process.env.NEXT_PUBLIC_API_PROXY || "http://localhost:8080/api";

// Directory layout: each route is at app/api/<scope>/<action>/route.ts
const routesDir = "./src/app/api";
const scopeDirs = readdirSync(routesDir, { withFileTypes: true })
  .filter(d => d.isDirectory())
  .map(d => d.name);

let updated = 0;
let errors = [];

for (const scope of scopeDirs) {
  const actionDir = join(routesDir, scope);
  const files = readdirSync(actionDir);
  
  for (const file of files) {
    if (!file.endsWith(".ts") && !file.endsWith(".tsx")) continue;
    
    const filePath = join(actionDir, file);
    let content = readFileSync(filePath, "utf8");
    
    // Skip non-route files
    if (file !== "route.ts") continue;
    
    const original = content;
    
    // Determine HTTP method from the action name and whether it already has a method
    const scopeAction = scope + "/" + file.replace(".ts", "");
    const isPostRoute = ["start", "stop", "register"].includes(scope.replace("/", "")) || 
                         (files.some(f => f === "route.ts") && content.includes("POST(request"));
    
    // Replace the mock-data import and call with real fetch to Spring Boot
    let modified = false;
    
    // Remove mock-api imports
    if (content.includes("@/lib/server/mock-api")) {
      content = content.replace(/import \{[^}]*\} from ["']@\/lib\/server\/mock-api["'];?\n?/g, "");
      modified = true;
    }
    
    // Remove mock-data imports
    if (content.includes("@/lib/mock-data")) {
      content = content.replace(/import \{[^}]*\} from ["']@\/lib\/mock-data["'];?\n?/g, "");
      modified = true;
    }

    // Replace route.ts body for GET endpoints
    if (content.includes("GET(request") || content.includes('GET(')) {
      const match = content.match(/export async function GET\(request: Request\) \{[\s\S]*?\n\}/);
      if (match) {
        // Extract the API path from the route directory structure
        let apiPath;
        if (scope === "data" && file === "route.ts") {
          // Need to parse the URL params to determine which endpoint
          apiPath = `/api/data`;
        } else {
          apiPath = `/api/${scope}`.replace(/\/\//g, "/");
        }

        if (scope === "data") {
          // For /api/data/* routes, keep query params from URL
          const newContent = `import { NextResponse } from "next/server";

const API_BASE = process.env.NEXT_PUBLIC_API_PROXY || "${BACKEND_URL}";

export async function GET(request: Request) {
  try {
    const url = new URL(request.url);
    const path = "/api/data" + url.pathname.replace("/api/data", "");
    const queryParams = url.searchParams.toString();
    
    const response = await fetch(\`\${API_BASE}\${path}\${queryParams ? "?" + queryParams : ""}\`, {
      method: "GET",
      headers: {
        "Content-Type": "application/json",
        ...(typeof window === "undefined" ? {} : {}),
      },
    });

    if (!response.ok) {
      return NextResponse.json(
        { message: \`API error: \${response.status}\` },
        { status: response.status }
      );
    }

    const data = await response.json();
    return NextResponse.json(data);
  } catch (error) {
    console.error("Backend fetch failed:", error);
    return NextResponse.json(
      { message: "Failed to fetch data from backend" },
      { status: 502 }
    );
  }
}
`;
          content = newContent;
          modified = true;
        } else if (scope === "stats") {
          const newContent = `import { NextResponse } from "next/server";

const API_BASE = process.env.NEXT_PUBLIC_API_PROXY || "${BACKEND_URL}";

export async function GET() {
  try {
    const response = await fetch(\`\${API_BASE}/stats\`, {
      method: "GET",
      headers: { "Content-Type": "application/json" },
    });

    if (!response.ok) {
      return NextResponse.json(
        { message: \`API error: \${response.status}\` },
        { status: response.status }
      );
    }

    const data = await response.json();
    return NextResponse.json(data);
  } catch (error) {
    console.error("Backend fetch failed:", error);
    return NextResponse.json(
      { message: "Failed to fetch stats from backend" },
      { status: 502 }
    );
  }
}
`;
          content = newContent;
          modified = true;
        } else if (scope === "export") {
          const newContent = `import { NextResponse } from "next/server";

const API_BASE = process.env.NEXT_PUBLIC_API_PROXY || "${BACKEND_URL}";

export async function GET(request: Request) {
  try {
    const url = new URL(request.url);
    const queryParams = url.searchParams.toString();
    
    const response = await fetch(\`\${API_BASE}/data/export\${queryParams ? "?" + queryParams : ""}\`, {
      method: "GET",
      headers: { "Content-Type": "application/json" },
    });

    if (!response.ok) {
      return NextResponse.json(
        { message: \`API error: \${response.status}\` },
        { status: response.status }
      );
    }

    const blob = await response.blob();
    const disposition = response.headers.get("content-disposition") || "attachment; filename=export.csv";
    
    return new NextResponse(blob, {
      headers: {
        ...Object.fromEntries(response.headers.entries()),
        "Content-Disposition": disposition,
      },
    });
  } catch (error) {
    console.error("Backend fetch failed:", error);
    return NextResponse.json(
      { message: "Failed to export data from backend" },
      { status: 502 }
    );
  }
}
`;
          content = newContent;
          modified = true;
        } else if (scope === "settings") {
          // Settings needs GET and POST handled separately
          const newGetContent = `import { NextResponse } from "next/server";

const API_BASE = process.env.NEXT_PUBLIC_API_PROXY || "${BACKEND_URL}";

export async function GET() {
  try {
    const response = await fetch(\`\${API_BASE}/settings\`, {
      method: "GET",
      headers: { "Content-Type": "application/json" },
    });

    if (!response.ok) {
      return NextResponse.json(
        { message: \`API error: \${response.status}\` },
        { status: response.status }
      );
    }

    const data = await response.json();
    return NextResponse.json(data);
  } catch (error) {
    console.error("Backend fetch failed:", error);
    return NextResponse.json(
      { message: "Failed to fetch settings from backend" },
      { status: 502 }
    );
  }
}

export async function POST(request: Request) {
  try {
    const payload = await request.json();
    const response = await fetch(\`\${API_BASE}/settings\`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });

    if (!response.ok) {
      return NextResponse.json(
        { message: \`API error: \${response.status}\` },
        { status: response.status }
      );
    }

    const data = await response.json();
    return NextResponse.json(data);
  } catch (error) {
    console.error("Backend fetch failed:", error);
    return NextResponse.json(
      { message: "Failed to update settings" },
      { status: 502 }
    );
  }
}
`;
          // If file already has POST, preserve it; otherwise add GET + POST
          if (content.includes("POST(request")) {
            // Replace only the GET part
            content = content.replace(/export async function GET\(request: Request\) \{[\s\S]*?\n\}\n?/, newGetContent.split("\n\n")[0] + "\n");
          } else {
            content = newGetContent;
          }
          modified = true;
        } else if (scope === "crawler" || scope === "crawlers") {
          // Crawler routes need method detection
          const actionName = file.replace(".ts", "") === "" ? actionDir.split("/").pop() : "";
          
          if (actionName === "start") {
            content = `import { NextResponse } from "next/server";

const API_BASE = process.env.NEXT_PUBLIC_API_PROXY || "${BACKEND_URL}";

export async function POST(request: Request) {
  try {
    const config = await request.json();
    
    const response = await fetch(\`\${API_BASE}/crawler/start\`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        subreddit: config.subreddit,
        limit: config.limit || 250,
        depth: config.depth || 4,
        includeComments: config.includeComments ?? true,
        keywords: config.keywords || "",
        sort: "hot",
      }),
    });

    if (!response.ok) {
      const errorBody = await response.text();
      return NextResponse.json(
        { message: \`API error: \${response.status} - \${errorBody}\` },
        { status: response.status }
      );
    }

    const data = await response.json();
    return NextResponse.json(data);
  } catch (error) {
    console.error("Failed to start crawler:", error);
    return NextResponse.json(
      { message: "Failed to start crawler" },
      { status: 502 }
    );
  }
}
`;
            modified = true;
          } else if (actionName === "stop") {
            content = `import { NextResponse } from "next/server";

const API_BASE = process.env.NEXT_PUBLIC_API_PROXY || "${BACKEND_URL}";

export async function POST() {
  try {
    const response = await fetch(\`\${API_BASE}/crawler/stop/temp\`, {
      method: "DELETE",
      headers: { "Content-Type": "application/json" },
    });

    if (!response.ok) {
      return NextResponse.json(
        { message: \`API error: \${response.status}\` },
        { status: response.status }
      );
    }

    const data = await response.json();
    return NextResponse.json(data);
  } catch (error) {
    console.error("Failed to stop crawler:", error);
    return NextResponse.json(
      { message: "Failed to stop crawler" },
      { status: 502 }
    );
  }
}
`;
            modified = true;
          } else if (actionName === "status") {
            // This is the /api/crawler/status/route.ts — but our backend has GET /crawler/status/{jobId}
            content = `import { NextResponse } from "next/server";

const API_BASE = process.env.NEXT_PUBLIC_API_PROXY || "${BACKEND_URL}";

export async function GET() {
  try {
    // Get all jobs to find the latest running one
    const response = await fetch(\`\${API_BASE}/crawler/status/all\`, {
      method: "GET",
      headers: { "Content-Type": "application/json" },
    });

    if (!response.ok) {
      return NextResponse.json(
        { message: \`API error: \${response.status}\` },
        { status: response.status }
      );
    }

    const jobs = await response.json();
    
    // Return the latest job as the current status
    const latestJob = jobs.length > 0 ? jobs[jobs.length - 1] : null;
    
    if (!latestJob) {
      return NextResponse.json({
        isRunning: false,
        currentSubreddit: null,
        progress: 0,
        mode: "idle",
        activeWorkers: 0,
        requestsPerMinute: 0,
        lastRunAt: null,
        config: {},
      });
    }

    return NextResponse.json(latestJob);
  } catch (error) {
    console.error("Failed to get crawler status:", error);
    return NextResponse.json(
      { message: "Failed to fetch crawler status" },
      { status: 502 }
    );
  }
}
`;
            modified = true;
          }
        }
        
        if (modified) {
          writeFileSync(filePath, content, "utf8");
          updated++;
          console.log(`✓ Updated: ${filePath}`);
        } else {
          errors.push(`${filePath}: no pattern matched`);
          console.error(`✗ No update: ${filePath}`);
        }
      }
    }
    
  }
}

console.log("\n=== Frontend Route Update Summary ===");
console.log(`Updated: ${updated} routes`);
if (errors.length > 0) {
  console.log(`Errors:`, errors);
} else {
  console.log("All routes processed successfully.");
}
