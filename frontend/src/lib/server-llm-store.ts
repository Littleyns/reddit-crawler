import { readFileSync, writeFileSync, existsSync } from "fs";
import { resolve } from "path";

const CONFIG_PATH = process.env.HOME
  ? resolve(process.env.HOME, ".reddit-crawler-config.json")
  : "/tmp/reddit-crawler-config.json";

const ENCRYPTION_KEY = process.env.LLM_CONFIG_KEY || "default-key-change-in-prod";

function simpleEncrypt(text: string): string {
  if (!text) return btoa("");
  let out = "";
  for (let i = 0; i < text.length; i++) {
    const c = text.charCodeAt(i);
    out += String.fromCharCode((c + ENCRYPTION_KEY.charCodeAt(i % ENCRYPTION_KEY.length)) ^ 165);
  }
  return btoa(out);
}

function simpleDecrypt(base64: string): string {
  try {
    const decoded = atob(base64 || "");
    if (!decoded) return "";
    let out = "";
    for (let i = 0; i < decoded.length; i++) {
      const c = decoded.charCodeAt(i);
      out += String.fromCharCode((c ^ 165) - ENCRYPTION_KEY.charCodeAt(i % ENCRYPTION_KEY.length));
    }
    return decodeURIComponent(escape(out));
  } catch {
    return "";
  }
}

export interface LLMConfigInput {
  provider: string;
  apiKey: string;
  baseUrl: string;
  modelId: string;
}

export function saveLLMConfig(config: LLMConfigInput): boolean {
  const encryptedKey = simpleEncrypt(config.apiKey || "");
  const data = JSON.stringify({ ...config, apiKey: encryptedKey }, null, 2);
  try {
    writeFileSync(CONFIG_PATH, data, "utf8");
    return true;
  } catch (e) {
    console.error("saveLLMConfig failed:", e);
    return false;
  }
}

export function getLLMConfig(): Partial<LLMConfigInput> & { maskedKey: string } {
  if (!existsSync(CONFIG_PATH)) {
    return { provider: "", baseUrl: "", modelId: "", maskedKey: "" };
  }
  try {
    const data = JSON.parse(readFileSync(CONFIG_PATH, "utf8"));
    let maskedKey = "";
    if (data.apiKey) {
      const decrypted = simpleDecrypt(data.apiKey);
      maskedKey = decrypted.length > 4 ? "****" + decrypted.slice(-4) : "[key set]";
    }
    return { ...data, maskedKey };
  } catch {
    return { provider: "", baseUrl: "", modelId: "", maskedKey: "" };
  }
}

export function testLLMConnection(baseUrl: string, apiKey: string, modelId: string): { success: boolean; latencyMs: number; message: string } {
  const start = Date.now();

  if (!baseUrl || !apiKey) {
    return { success: false, latencyMs: 0, message: "Missing baseUrl or apiKey" };
  }

  // In Node.js server-side, we use real fetch to test connection
  // This runs in the Next.js API route (Node.js runtime)
  const fullUrl = baseUrl.replace(/\/$/, "") + "/chat/completions";

  return { success: false, latencyMs: Date.now() - start, message: "Not implemented in storage layer" };
}
